package com.example.healthconnectandroid.hc.upload

import java.net.URI
import java.net.URLDecoder
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

sealed interface UploadScanApplyResult {
    data class Success(
        val settings: UploadSettings,
        val message: String
    ) : UploadScanApplyResult

    data class Invalid(val message: String) : UploadScanApplyResult
}

object UploadScanPolicy {
    private val PlainApiKeyPattern = Regex("""^[A-Za-z0-9._~+/=-]{12,256}$""")
    private const val PAIRING_SCHEME = "nanato-hc"
    private const val PAIRING_HOST = "pair"

    fun applyScannedText(
        current: UploadSettings,
        rawText: String
    ): UploadScanApplyResult {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return UploadScanApplyResult.Invalid("QR code was empty")
        }

        return when (val pairing = parsePairing(trimmed)) {
            is PairingParseResult.Valid -> applyPairing(current, pairing.value)
            is PairingParseResult.Invalid -> UploadScanApplyResult.Invalid(pairing.message)
        }
    }

    private fun applyPairing(
        current: UploadSettings,
        pairing: UploadPairing
    ): UploadScanApplyResult {
        val baseUrl = baseUrlFromPairingUrl(pairing.serverUrl)
            ?: return UploadScanApplyResult.Invalid("QR code did not contain a server URL")
        val apiKey = cleanApiKey(pairing.apiKey)
            ?: return UploadScanApplyResult.Invalid("QR code did not contain an API key")
        val serverMode = pairing.serverMode ?: serverModeForBaseUrl(baseUrl)

        val next = when (serverMode) {
            UploadServerMode.PRODUCTION -> current.copy(
                serverMode = UploadServerMode.PRODUCTION,
                productionBaseUrl = baseUrl,
                apiKey = apiKey
            )
            UploadServerMode.LOCAL_DEBUG -> current.copy(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = baseUrl,
                apiKey = apiKey
            )
        }

        val validation = UploadEndpointPolicy.validate(next)
        if (validation is UploadEndpointValidation.Invalid) {
            return UploadScanApplyResult.Invalid(validation.reason)
        }

        return UploadScanApplyResult.Success(
            settings = next,
            message = "Paired ${next.serverMode.label} upload endpoint"
        )
    }

    private fun cleanApiKey(value: String): String? =
        value.trim().takeIf { PlainApiKeyPattern.matches(it) }

    private fun baseUrlFromPairingUrl(rawUrl: String): String? {
        val normalized = UploadEndpointPolicy.normalizeBaseUrl(rawUrl) ?: return null
        return normalized.takeIf { it.toHttpUrlOrNull() != null }
    }

    private fun parsePairing(rawText: String): PairingParseResult {
        val uri = runCatching { URI(rawText) }.getOrNull()
            ?: return PairingParseResult.Invalid("QR code did not contain upload pairing data")
        if (!uri.scheme.equals(PAIRING_SCHEME, ignoreCase = true)) {
            return PairingParseResult.Invalid("QR code did not contain upload pairing data")
        }
        if (!uri.host.orEmpty().equals(PAIRING_HOST, ignoreCase = true)) {
            return PairingParseResult.Invalid("QR code did not contain upload pairing data")
        }
        val params = queryParams(uri.rawQuery ?: "")
            ?: return PairingParseResult.Invalid("QR code contained invalid pairing text")
        val serverUrl = params["u"]?.takeIf { it.isNotBlank() }
            ?: return PairingParseResult.Invalid("QR code did not contain a server URL")
        val apiKey = params["k"]?.takeIf { it.isNotBlank() }
            ?: return PairingParseResult.Invalid("QR code did not contain an API key")
        val serverMode = if (params.containsKey("m")) {
            // Current admin QR owns the target mode; older QR values fall back to URL inference.
            pairingMode(params["m"])
                ?: return PairingParseResult.Invalid("QR code pairing mode is not supported")
        } else {
            null
        }
        return PairingParseResult.Valid(
            UploadPairing(
                serverUrl = serverUrl,
                apiKey = apiKey,
                serverMode = serverMode
            )
        )
    }

    private fun queryParams(rawQuery: String): Map<String, String>? {
        val params = mutableMapOf<String, String>()
        rawQuery.split('&').forEach { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@forEach
            val name = decodeQueryValue(part.substring(0, separator)) ?: return null
            val value = decodeQueryValue(part.substring(separator + 1)) ?: return null
            params.putIfAbsent(name, value)
        }
        return params
    }

    private fun decodeQueryValue(value: String): String? =
        runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrNull()

    private fun serverModeForBaseUrl(baseUrl: String): UploadServerMode {
        val parsed = baseUrl.toHttpUrlOrNull() ?: return UploadServerMode.LOCAL_DEBUG
        val host = parsed.host.lowercase()
        return when {
            parsed.scheme == "http" -> UploadServerMode.LOCAL_DEBUG
            UploadEndpointPolicy.isLocalDebugHost(host) -> UploadServerMode.LOCAL_DEBUG
            else -> UploadServerMode.PRODUCTION
        }
    }

    private fun pairingMode(rawMode: String?): UploadServerMode? =
        when (rawMode?.trim()?.lowercase()) {
            "production" -> UploadServerMode.PRODUCTION
            "local", "local_debug", "local-debug" -> UploadServerMode.LOCAL_DEBUG
            else -> null
        }

    private sealed interface PairingParseResult {
        data class Valid(val value: UploadPairing) : PairingParseResult
        data class Invalid(val message: String) : PairingParseResult
    }

    private data class UploadPairing(
        val serverUrl: String,
        val apiKey: String,
        val serverMode: UploadServerMode?
    )
}
