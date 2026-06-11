package com.example.healthconnectandroid.hc.upload

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

internal enum class UploadHttpAction(val label: String) {
    STATUS("Connection"),
    INGEST("Upload")
}

internal data class UploadHttpFailure(
    val retryable: Boolean,
    val failureKind: UploadFailureKind,
    val message: String
)

internal object UploadHttpFailurePolicy {
    fun fromResponse(
        action: UploadHttpAction,
        url: String,
        code: Int,
        responseBody: String?
    ): UploadHttpFailure =
        when {
            code == 401 || code == 403 -> UploadHttpFailure(
                retryable = false,
                failureKind = UploadFailureKind.API_KEY_INVALID,
                message = authMessage(action, url, code, responseBody)
            )
            code in 400..499 -> UploadHttpFailure(
                retryable = false,
                failureKind = UploadFailureKind.SERVER_VALIDATION,
                message = "${action.label} request was rejected ($code) at ${safeUrl(url)}${detailSuffix(responseBody)}"
            )
            else -> UploadHttpFailure(
                retryable = true,
                failureKind = UploadFailureKind.SERVER,
                message = "${action.label} server unavailable ($code) at ${safeUrl(url)}${detailSuffix(responseBody)}"
            )
        }

    private fun authMessage(
        action: UploadHttpAction,
        url: String,
        code: Int,
        responseBody: String?
    ): String {
        val detail = serverDetail(responseBody)
        val reason = when {
            detail?.contains("Invalid HealthConnect API key", ignoreCase = true) == true ->
                "The server rejected this HealthConnect API key."
            detail?.contains("session", ignoreCase = true) == true ->
                "The URL appears to point at a session-protected route, not the HealthConnect upload API."
            else ->
                "The server returned an authentication error."
        }
        return buildString {
            append("${action.label} authentication failed ($code) at ${safeUrl(url)}")
            append('\n')
            append(reason)
            detail?.let {
                append('\n')
                append("Server response: ")
                append(it)
            }
            append('\n')
            append("Check the upload URL and the active API key source on the website backend.")
        }
    }

    private fun detailSuffix(responseBody: String?): String =
        serverDetail(responseBody)?.let { "\nServer response: $it" }.orEmpty()

    private fun serverDetail(responseBody: String?): String? {
        val body = responseBody
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val jsonDetail = runCatching {
            JSONObject(body).optString("detail")
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return (jsonDetail ?: body)
            .replace(Regex("""\s+"""), " ")
            .take(240)
    }

    private fun safeUrl(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url.take(160)
        // Status messages are user-visible; never echo query strings that may contain copied secrets.
        return parsed.newBuilder()
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }
}
