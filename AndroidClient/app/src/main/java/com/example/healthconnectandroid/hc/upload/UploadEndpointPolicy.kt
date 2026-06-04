package com.example.healthconnectandroid.hc.upload

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UploadEndpointPolicy {
    const val PRODUCTION_BASE_URL = "https://www.tsubashimonanato.com/health/api/v1/"
    const val DEFAULT_LOCAL_BASE_URL = "http://10.0.2.2:8000/health/api/v1/"

    fun validate(settings: UploadSettings, requireApiKey: Boolean = true): UploadEndpointValidation {
        if (requireApiKey && settings.apiKey.isBlank()) {
            return UploadEndpointValidation.Invalid("API key is required")
        }

        val rawBaseUrl = when (settings.serverMode) {
            UploadServerMode.PRODUCTION -> PRODUCTION_BASE_URL
            UploadServerMode.LOCAL_DEBUG -> settings.localBaseUrl.trim()
        }
        val normalized = normalizeBaseUrl(rawBaseUrl)
            ?: return UploadEndpointValidation.Invalid("Invalid server URL")
        val parsed = normalized.toHttpUrlOrNull()
            ?: return UploadEndpointValidation.Invalid("Invalid server URL")

        if (settings.serverMode == UploadServerMode.PRODUCTION && parsed.scheme != "https") {
            return UploadEndpointValidation.Invalid("Production upload requires HTTPS")
        }
        if (settings.serverMode == UploadServerMode.LOCAL_DEBUG && parsed.scheme !in setOf("http", "https")) {
            return UploadEndpointValidation.Invalid("Local server must use HTTP or HTTPS")
        }
        if (settings.serverMode == UploadServerMode.LOCAL_DEBUG && parsed.scheme == "http" && !parsed.host.isLocalDebugHost()) {
            return UploadEndpointValidation.Invalid("HTTP local debug upload must use a local/private host")
        }

        val statusUrl = parsed.resolve("status")?.toString()
            ?: return UploadEndpointValidation.Invalid("Cannot build status endpoint")
        val ingestUrl = parsed.resolve("ingest/batches")?.toString()
            ?: return UploadEndpointValidation.Invalid("Cannot build ingest endpoint")
        val baseUrl = parsed.toString()
        return UploadEndpointValidation.Valid(
            UploadEndpoint(
                mode = settings.serverMode,
                baseUrl = baseUrl,
                statusUrl = statusUrl,
                ingestBatchesUrl = ingestUrl,
                serverKey = "${settings.serverMode.name}:$baseUrl"
            )
        )
    }

    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun String.isLocalDebugHost(): Boolean {
        val lower = lowercase()
        if (lower == "localhost" || lower == "127.0.0.1" || lower == "::1" || lower == "10.0.2.2") {
            return true
        }
        if (lower.startsWith("192.168.")) return true
        if (lower.startsWith("10.")) return true
        val parts = lower.split(".")
        if (parts.size == 4 && parts[0] == "172") {
            val second = parts[1].toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        return false
    }
}
