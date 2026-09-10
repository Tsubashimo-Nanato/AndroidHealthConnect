package com.example.healthconnectandroid.hc.upload

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

object ProfilePairingStore {
    private const val TAG = "HCPairingStore"
    private const val KEY_ALIAS = "health_connect_profile_pairing"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val TOKEN_DIRECTORY = "health_connect_pairings"
    private val profileIdPattern = Regex("""^[0-9a-fA-F-]{36}$""")

    fun load(context: Context, localProfileId: String): ProfileUploadCredential? {
        val file = credentialFile(context, localProfileId) ?: return null
        if (!file.isFile) return null
        return runCatching {
            val encrypted = file.readText(Charsets.UTF_8)
            decodeCredential(decrypt(encrypted))
        }.onFailure { error ->
            Log.w(TAG, "Failed to read pairing for profile=$localProfileId", error)
        }.getOrNull()
    }

    fun save(context: Context, localProfileId: String, credential: ProfileUploadCredential) {
        val file = credentialFile(context, localProfileId)
            ?: throw IllegalArgumentException("Invalid local profile ID")
        check(file.parentFile?.mkdirs() != false) { "Cannot create pairing storage" }
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(encrypt(encodeCredential(credential)), Charsets.UTF_8)
        if (file.exists() && !file.delete()) {
            temporary.delete()
            error("Cannot replace saved pairing")
        }
        check(temporary.renameTo(file)) {
            temporary.delete()
            "Cannot commit saved pairing"
        }
    }

    fun clear(context: Context, localProfileId: String) {
        credentialFile(context, localProfileId)?.delete()
    }

    fun clientDeviceId(
        context: Context,
        localProfileId: String,
        legacyDeviceId: String,
        useLegacyId: Boolean
    ): String {
        if (useLegacyId) return legacyDeviceId
        val file = deviceIdFile(context, localProfileId)
            ?: throw IllegalArgumentException("Invalid local profile ID")
        file.takeIf(File::isFile)
            ?.readText(Charsets.UTF_8)
            ?.trim()
            ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
            ?.let { return it }
        check(file.parentFile?.mkdirs() != false) { "Cannot create pairing storage" }
        val generated = UUID.randomUUID().toString()
        file.writeText(generated, Charsets.UTF_8)
        return generated
    }

    private fun credentialFile(context: Context, localProfileId: String): File? {
        if (!profileIdPattern.matches(localProfileId)) return null
        return File(File(context.noBackupFilesDir, TOKEN_DIRECTORY), "$localProfileId.bin")
    }

    private fun deviceIdFile(context: Context, localProfileId: String): File? {
        if (!profileIdPattern.matches(localProfileId)) return null
        return File(File(context.noBackupFilesDir, TOKEN_DIRECTORY), "$localProfileId.device")
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 2)
        require(parts.size == 2) { "Encrypted pairing has an invalid envelope" }
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encodeCredential(credential: ProfileUploadCredential): String =
        JSONObject()
            .put("serverMode", credential.serverMode.name)
            .put("serverProfileId", credential.serverProfileId)
            .put("serverProfileName", credential.serverProfileName)
            .put("installationId", credential.installationId)
            .put("clientDeviceId", credential.clientDeviceId)
            .put("accessToken", credential.accessToken)
            .put("expiresAtEpochMillis", credential.expiresAtEpochMillis)
            .put("uploadBaseUrl", credential.uploadBaseUrl)
            .toString()

    private fun decodeCredential(rawJson: String): ProfileUploadCredential {
        val json = JSONObject(rawJson)
        return ProfileUploadCredential(
            serverMode = UploadServerMode.valueOf(json.getString("serverMode")),
            serverProfileId = json.getString("serverProfileId"),
            serverProfileName = json.getString("serverProfileName"),
            installationId = json.getString("installationId"),
            clientDeviceId = json.getString("clientDeviceId"),
            accessToken = json.getString("accessToken"),
            expiresAtEpochMillis = json.getLong("expiresAtEpochMillis"),
            uploadBaseUrl = json.getString("uploadBaseUrl")
        )
    }
}
