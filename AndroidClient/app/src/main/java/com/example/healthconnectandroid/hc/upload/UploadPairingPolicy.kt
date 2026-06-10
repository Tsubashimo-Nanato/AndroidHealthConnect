package com.example.healthconnectandroid.hc.upload

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

data class UploadPairingCandidate(
    val baseUrl: String? = null,
    val apiKey: String? = null
) {
    val hasData: Boolean get() = baseUrl != null || apiKey != null
}

sealed interface UploadPairingApplyResult {
    data class Success(
        val settings: UploadSettings,
        val message: String
    ) : UploadPairingApplyResult

    data class Invalid(val message: String) : UploadPairingApplyResult
}

object UploadPairingPolicy {
    private val ApiKeyJsonFields = listOf(
        "apiKey",
        "api_key",
        "xApiKey",
        "x_api_key",
        "uploadKey",
        "upload_key",
        "healthConnectApiKey",
        "healthconnect_api_key",
        "key"
    )
    private val EndpointJsonFields = listOf(
        "uploadUrl",
        "upload_url",
        "ingestUrl",
        "ingest_url",
        "ingestEndpoint",
        "ingest_endpoint",
        "androidEndpoint",
        "android_endpoint",
        "endpoint",
        "statusUrl",
        "status_url"
    )
    private val BaseUrlJsonFields = listOf(
        "baseUrl",
        "base_url",
        "uploadBaseUrl",
        "upload_base_url",
        "localBaseUrl",
        "local_base_url",
        "productionBaseUrl",
        "production_base_url"
    )
    private val ApiKeyQueryFields = listOf("apiKey", "api_key", "xApiKey", "x_api_key", "key")
    private val EndpointQueryFields = listOf(
        "uploadUrl",
        "upload_url",
        "ingestUrl",
        "ingest_url",
        "endpoint",
        "androidEndpoint",
        "android_endpoint",
        "url",
        "baseUrl",
        "base_url"
    )
    private val PlainApiKeyPattern = Regex("""^[A-Za-z0-9._~+/=-]{12,256}$""")
    private val LabelledApiKeyPattern = Regex("""(?i)\b(?:x-api-key|api[_ -]?key)\s*[:=]\s*(\S{12,256})""")

    fun applyPairingText(
        current: UploadSettings,
        rawText: String
    ): UploadPairingApplyResult {
        val candidate = parse(rawText)
        if (!candidate.hasData) {
            return UploadPairingApplyResult.Invalid("QR code did not contain an upload URL or API key")
        }

        var next = current
        candidate.apiKey?.let { apiKey ->
            next = next.copy(apiKey = apiKey)
        }
        candidate.baseUrl?.let { baseUrl ->
            next = when (serverModeForBaseUrl(baseUrl)) {
                UploadServerMode.PRODUCTION -> next.copy(serverMode = UploadServerMode.PRODUCTION)
                UploadServerMode.LOCAL_DEBUG -> next.copy(
                    serverMode = UploadServerMode.LOCAL_DEBUG,
                    localBaseUrl = baseUrl
                )
            }
        }

        val validation = UploadEndpointPolicy.validate(next, requireApiKey = false)
        if (validation is UploadEndpointValidation.Invalid) {
            return UploadPairingApplyResult.Invalid(validation.reason)
        }

        val message = when {
            candidate.baseUrl != null && candidate.apiKey != null ->
                "Paired ${next.serverMode.label} upload endpoint and key"
            candidate.baseUrl != null && next.apiKey.isBlank() ->
                "Paired ${next.serverMode.label} upload endpoint. Scan or paste the API key next."
            candidate.baseUrl != null ->
                "Paired ${next.serverMode.label} upload endpoint"
            else ->
                "Paired upload API key"
        }
        return UploadPairingApplyResult.Success(next, message)
    }

    fun parse(rawText: String): UploadPairingCandidate {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) return UploadPairingCandidate()

        val jsonCandidate = parseJson(trimmed)
        val urlCandidate = parseUrl(trimmed)
        val plainKey = parsePlainApiKey(trimmed)
        return UploadPairingCandidate(
            baseUrl = jsonCandidate.baseUrl ?: urlCandidate.baseUrl,
            apiKey = jsonCandidate.apiKey ?: urlCandidate.apiKey ?: plainKey
        )
    }

    private fun parseJson(rawText: String): UploadPairingCandidate {
        if (!rawText.trimStart().startsWith("{")) return UploadPairingCandidate()
        val json = runCatching { JSONObject(rawText) }.getOrNull()
            ?: return UploadPairingCandidate()
        val endpoint = jsonString(json, EndpointJsonFields)
        val baseUrl = jsonString(json, BaseUrlJsonFields)
        return UploadPairingCandidate(
            baseUrl = baseUrlFromEndpoint(baseUrl) ?: baseUrlFromEndpoint(endpoint),
            apiKey = cleanApiKey(jsonString(json, ApiKeyJsonFields))
        )
    }

    private fun parseUrl(rawText: String): UploadPairingCandidate {
        val queryParams = queryParams(rawText)
        val endpoint = firstQueryValue(queryParams, EndpointQueryFields) ?: rawText
        return UploadPairingCandidate(
            baseUrl = baseUrlFromEndpoint(endpoint),
            apiKey = cleanApiKey(firstQueryValue(queryParams, ApiKeyQueryFields))
        )
    }

    private fun parsePlainApiKey(rawText: String): String? {
        LabelledApiKeyPattern.find(rawText)?.groupValues?.getOrNull(1)?.let { labelled ->
            cleanApiKey(labelled)?.let { return it }
        }
        return rawText.takeIf { PlainApiKeyPattern.matches(it) }
    }

    private fun baseUrlFromEndpoint(rawUrl: String?): String? {
        val trimmed = rawUrl
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val parsed = trimmed.toHttpUrlOrNull()
            ?: UploadEndpointPolicy.normalizeBaseUrl(trimmed)?.toHttpUrlOrNull()
            ?: return null
        val pathWithoutEndpoint = parsed.encodedPath.trimEnd('/').let { path ->
            when {
                path.endsWith("/ingest/batches") -> path.removeSuffix("/ingest/batches")
                path.endsWith("/status") -> path.removeSuffix("/status")
                else -> path
            }
        }
        val basePath = if (pathWithoutEndpoint.isBlank()) "/" else "$pathWithoutEndpoint/"
        return parsed.newBuilder()
            .encodedPath(basePath)
            .query(null)
            .fragment(null)
            .build()
            .toString()
    }

    private fun serverModeForBaseUrl(baseUrl: String): UploadServerMode {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return UploadServerMode.LOCAL_DEBUG
        val host = parsed.host.lowercase()
        return when {
            parsed.scheme == "https" && host in setOf("tsubashimonanato.com", "www.tsubashimonanato.com") ->
                UploadServerMode.PRODUCTION
            parsed.scheme == "http" || UploadEndpointPolicy.isLocalDebugHost(host) || parsed.port == 8000 ->
                UploadServerMode.LOCAL_DEBUG
            else ->
                UploadServerMode.PRODUCTION
        }
    }

    private fun queryParams(rawText: String): Map<String, String> {
        val rawQuery = runCatching { URI(rawText).rawQuery }.getOrNull() ?: return emptyMap()
        return rawQuery
            .split("&")
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = urlDecode(part.substring(0, separator))
                val value = urlDecode(part.substring(separator + 1))
                key to value
            }
            .toMap()
    }

    private fun firstQueryValue(params: Map<String, String>, names: List<String>): String? =
        names.firstNotNullOfOrNull { name ->
            params.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
                ?.value
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

    private fun jsonString(json: JSONObject, names: List<String>): String? =
        names.firstNotNullOfOrNull { name ->
            if (!json.has(name) || json.isNull(name)) {
                return@firstNotNullOfOrNull null
            }
            json.optString(name)
                .trim()
                .takeIf { it.isNotBlank() }
        }

    private fun cleanApiKey(value: String?): String? =
        value
            ?.trim()
            ?.takeIf { it.length in 12..256 }

    private fun urlDecode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}
