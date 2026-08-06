package com.example.healthconnectandroid.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import android.util.Base64
import com.example.healthconnectandroid.hc.upload.UploadServerMode
import com.example.healthconnectandroid.hc.upload.UploadSettings
import org.json.JSONObject
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.InvalidAlgorithmParameterException
import java.security.KeyStore
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal sealed interface SecureUploadSettingsReadResult {
    data class Value(val settings: UploadSettings) : SecureUploadSettingsReadResult
    data object Missing : SecureUploadSettingsReadResult
    data object Corrupt : SecureUploadSettingsReadResult
    data object Unavailable : SecureUploadSettingsReadResult
}

internal enum class SecurePayloadFailureKind {
    CORRUPT,
    TEMPORARILY_UNAVAILABLE
}

internal data class SecurePayloadRecoveryPlan(
    val result: SecureUploadSettingsReadResult
)

internal object SecurePayloadRecoveryPolicy {
    fun plan(failure: SecurePayloadFailureKind): SecurePayloadRecoveryPlan =
        when (failure) {
            SecurePayloadFailureKind.CORRUPT ->
                SecurePayloadRecoveryPlan(SecureUploadSettingsReadResult.Corrupt)
            SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE ->
                SecurePayloadRecoveryPlan(SecureUploadSettingsReadResult.Unavailable)
        }
}

internal object UploadSettingsPayloadCodec {
    private const val FORMAT_VERSION = 1

    fun encode(settings: UploadSettings): ByteArray =
        JSONObject()
            .put("version", FORMAT_VERSION)
            .put("serverMode", settings.serverMode.name)
            .put("productionBaseUrl", settings.productionBaseUrl.trim())
            .put("localBaseUrl", settings.localBaseUrl.trim())
            .put("apiKey", settings.apiKey.trim())
            .put("deviceId", settings.deviceId.trim())
            .put("autoUploadEnabled", settings.autoUploadEnabled)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

    fun decode(payload: ByteArray): UploadSettings {
        val json = JSONObject(String(payload, StandardCharsets.UTF_8))
        require(json.getInt("version") == FORMAT_VERSION)
        val deviceId = json.getString("deviceId").trim()
        require(deviceId.isNotEmpty())
        return UploadSettings(
            serverMode = UploadServerMode.valueOf(json.getString("serverMode")),
            productionBaseUrl = json.getString("productionBaseUrl").trim(),
            localBaseUrl = json.getString("localBaseUrl").trim(),
            apiKey = json.getString("apiKey").trim(),
            deviceId = deviceId,
            autoUploadEnabled = json.getBoolean("autoUploadEnabled")
        )
    }
}

/** Atomically stores all upload settings under an AES-GCM key held by Android Keystore. */
internal object SecureUploadSettingsStore {
    private const val PREFS_NAME = "health_connect_secure_upload_settings"
    private const val KEY_ALIAS = "health_connect_upload_settings_v1"
    private const val KEY_CIPHERTEXT = "ciphertext"
    private const val KEY_IV = "iv"
    private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AUTH_TAG_LENGTH_BITS = 128
    private const val AUTH_TAG_LENGTH_BYTES = AUTH_TAG_LENGTH_BITS / 8
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val KEY_SIZE_BITS = 256
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private val additionalAuthenticatedData =
        "com.example.healthconnectandroid.upload-settings.v1".toByteArray(StandardCharsets.UTF_8)

    @Synchronized
    fun read(context: Context): SecureUploadSettingsReadResult {
        val appContext = context.applicationContext
        val preferences = try {
            securePreferences(appContext)
        } catch (_: Exception) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        }

        val encodedPayload = try {
            val ciphertext = preferences.getString(KEY_CIPHERTEXT, null)
            val iv = preferences.getString(KEY_IV, null)
            when {
                ciphertext == null && iv == null -> return SecureUploadSettingsReadResult.Missing
                ciphertext.isNullOrBlank() || iv.isNullOrBlank() -> {
                    return readFailure(SecurePayloadFailureKind.CORRUPT)
                }
                else -> ciphertext to iv
            }
        } catch (_: ClassCastException) {
            return readFailure(SecurePayloadFailureKind.CORRUPT)
        } catch (_: Exception) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        }

        val key = try {
            existingKey()
        } catch (_: KeyPermanentlyInvalidatedException) {
            return readFailure(SecurePayloadFailureKind.CORRUPT)
        } catch (_: UnrecoverableKeyException) {
            return readFailure(SecurePayloadFailureKind.CORRUPT)
        } catch (_: UserNotAuthenticatedException) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        } catch (_: ProviderException) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        } catch (_: IOException) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        } catch (_: GeneralSecurityException) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        } catch (_: Exception) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        }
        if (key == null) {
            return readFailure(SecurePayloadFailureKind.CORRUPT)
        }

        val ciphertext: ByteArray
        val iv: ByteArray
        try {
            ciphertext = Base64.decode(encodedPayload.first, Base64.NO_WRAP)
            iv = Base64.decode(encodedPayload.second, Base64.NO_WRAP)
            require(ciphertext.size >= AUTH_TAG_LENGTH_BYTES && iv.size == GCM_IV_LENGTH_BYTES)
        } catch (_: IllegalArgumentException) {
            return readFailure(SecurePayloadFailureKind.CORRUPT)
        }

        val cleartext = try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(AUTH_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(additionalAuthenticatedData)
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            return readFailure(SecurePayloadFailureKind.CORRUPT)
        } catch (_: KeyPermanentlyInvalidatedException) {
            return readFailure(SecurePayloadFailureKind.CORRUPT)
        } catch (_: InvalidAlgorithmParameterException) {
            return readFailure(SecurePayloadFailureKind.CORRUPT)
        } catch (_: UserNotAuthenticatedException) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        } catch (_: ProviderException) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        } catch (_: GeneralSecurityException) {
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        } catch (_: Exception) {
            // Preserve both ciphertext and alias when storage/provider access may be temporary.
            return readFailure(SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE)
        }

        return try {
            SecureUploadSettingsReadResult.Value(UploadSettingsPayloadCodec.decode(cleartext))
        } catch (_: Exception) {
            readFailure(SecurePayloadFailureKind.CORRUPT)
        }
    }

    @Synchronized
    fun write(
        context: Context,
        settings: UploadSettings,
        replaceCorruptKey: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext
        return try {
            if (replaceCorruptKey && !deleteExistingKey()) return false
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            cipher.updateAAD(additionalAuthenticatedData)
            val ciphertext = cipher.doFinal(UploadSettingsPayloadCodec.encode(settings))

            securePreferences(appContext).edit()
                .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .commit()
        } catch (_: Exception) {
            // Leave the previously committed payload intact if an update cannot be completed.
            false
        }
    }

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val specification = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(specification)
        return generator.generateKey()
    }

    private fun existingKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
    }

    private fun readFailure(failure: SecurePayloadFailureKind): SecureUploadSettingsReadResult =
        SecurePayloadRecoveryPolicy.plan(failure).result

    private fun deleteExistingKey(): Boolean =
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
            true
        } catch (_: Exception) {
            false
        }

    private fun securePreferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
