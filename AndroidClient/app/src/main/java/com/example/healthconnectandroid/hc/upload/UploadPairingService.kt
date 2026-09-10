package com.example.healthconnectandroid.hc.upload

import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

sealed interface UploadPairingResult {
    data class Success(val credential: ProfileUploadCredential) : UploadPairingResult
    data class Failure(val message: String, val retryable: Boolean) : UploadPairingResult
}

class UploadPairingService(
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun redeem(
        pairing: UploadPairingCode,
        clientDeviceId: String,
        deviceName: String,
        appVersion: String
    ): UploadPairingResult = withContext(Dispatchers.IO) {
        val baseUrl = UploadEndpointPolicy.normalizeBaseUrl(pairing.apiBaseUrl)
            ?.toHttpUrlOrNull()
            ?: return@withContext UploadPairingResult.Failure("Pairing server URL is invalid", false)
        val redeemUrl = baseUrl.resolve("pair/redeem")
            ?: return@withContext UploadPairingResult.Failure("Pairing endpoint is invalid", false)
        val body = JSONObject()
            .put("code", pairing.code)
            .put("mode", pairing.serverMode.protocolName)
            .put("clientDeviceId", clientDeviceId)
            .put("deviceName", deviceName)
            .put("platform", "android")
            .put("appVersion", appVersion)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(redeemUrl)
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext UploadPairingResult.Failure(
                        message = pairingFailureMessage(response.code, responseText),
                        retryable = response.code >= 500
                    )
                }
                parseCredentialResponse(
                    rawJson = responseText,
                    pairing = pairing,
                    clientDeviceId = clientDeviceId
                )
            }
        } catch (exception: IOException) {
            UploadPairingResult.Failure(
                message = "Pairing server unavailable: ${exception.message ?: exception.javaClass.simpleName}",
                retryable = true
            )
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
    }
}

internal fun parseCredentialResponse(
    rawJson: String,
    pairing: UploadPairingCode,
    clientDeviceId: String
): UploadPairingResult {
    val json = runCatching { JSONObject(rawJson) }.getOrNull()
        ?: return UploadPairingResult.Failure("Pairing server returned invalid JSON", false)
    val profile = json.optJSONObject("profile")
        ?: return UploadPairingResult.Failure("Pairing response is missing profile", false)
    val installation = json.optJSONObject("installation")
        ?: return UploadPairingResult.Failure("Pairing response is missing installation", false)
    val profileId = profile.optString("profileId").validUuid()
        ?: return UploadPairingResult.Failure("Pairing response has an invalid profile ID", false)
    val installationId = installation.optString("installationId").validUuid()
        ?: return UploadPairingResult.Failure("Pairing response has an invalid installation ID", false)
    val profileName = profile.optString("displayName").trim().take(80)
        .ifBlank { "Server profile" }
    val accessToken = json.optString("accessToken").trim()
        .takeIf { it.length in 32..512 }
        ?: return UploadPairingResult.Failure("Pairing response is missing an access token", false)
    val expiresAt = json.optLong("expiresAtEpochMillis", 0L)
        .takeIf { it > System.currentTimeMillis() }
        ?: return UploadPairingResult.Failure("Pairing response token is already expired", false)
    val uploadBaseUrl = UploadEndpointPolicy.normalizeBaseUrl(json.optString("uploadBaseUrl"))
        ?.toHttpUrlOrNull()
        ?: return UploadPairingResult.Failure("Pairing response has an invalid upload URL", false)
    val requestedBaseUrl = UploadEndpointPolicy.normalizeBaseUrl(pairing.apiBaseUrl)
        ?.toHttpUrlOrNull()
        ?: return UploadPairingResult.Failure("Pairing server URL is invalid", false)
    if (
        uploadBaseUrl.scheme != requestedBaseUrl.scheme ||
        uploadBaseUrl.host != requestedBaseUrl.host ||
        uploadBaseUrl.port != requestedBaseUrl.port
    ) {
        return UploadPairingResult.Failure("Pairing response changed the server origin", false)
    }
    if (uploadBaseUrl.encodedPath != requestedBaseUrl.encodedPath) {
        return UploadPairingResult.Failure("Pairing response changed the server endpoint", false)
    }

    return UploadPairingResult.Success(
        ProfileUploadCredential(
            serverMode = pairing.serverMode,
            serverProfileId = profileId,
            serverProfileName = profileName,
            installationId = installationId,
            clientDeviceId = clientDeviceId,
            accessToken = accessToken,
            expiresAtEpochMillis = expiresAt,
            uploadBaseUrl = uploadBaseUrl.toString()
        )
    )
}

private fun pairingFailureMessage(statusCode: Int, rawJson: String): String {
    val detail = runCatching { JSONObject(rawJson).optJSONObject("detail") }.getOrNull()
    val code = detail?.optString("code").orEmpty()
    val message = detail?.optString("message").orEmpty()
    return when (code) {
        "pairing_code_expired" -> "Pairing code expired. Generate and scan a new QR"
        "pairing_code_used" -> "Pairing code was already used. Generate and scan a new QR"
        "pairing_mode_mismatch" -> "Pairing code is for a different server mode"
        "profile_archived" -> "The selected server profile is archived"
        "profile_v2_disabled" -> "Profile pairing is not enabled on this server"
        else -> message.ifBlank { "Pairing failed with HTTP $statusCode" }
    }
}

private val UploadServerMode.protocolName: String
    get() = when (this) {
        UploadServerMode.PRODUCTION -> "production"
        UploadServerMode.LOCAL_DEBUG -> "local"
    }

private fun String.validUuid(): String? =
    trim().takeIf { runCatching { UUID.fromString(it) }.isSuccess }
