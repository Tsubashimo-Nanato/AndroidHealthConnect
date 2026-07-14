package com.example.healthconnectandroid.hc.upload

import org.json.JSONObject

internal enum class UploadRequestCompression {
    NONE,
    GZIP
}

internal data class UploadBatchProfile(
    val recordLimit: Int,
    val valueLimit: Int,
    val aggregateLimit: Int,
    val requestCompression: UploadRequestCompression
)

internal object UploadCapabilityPolicy {
    val Legacy = UploadBatchProfile(
        recordLimit = 1_000,
        valueLimit = 5_000,
        aggregateLimit = 1_000,
        requestCompression = UploadRequestCompression.NONE
    )

    private val CompressedDefault = UploadBatchProfile(
        recordLimit = 4_000,
        valueLimit = 12_000,
        aggregateLimit = 2_000,
        requestCompression = UploadRequestCompression.GZIP
    )

    fun fromStatusJson(rawJson: String?): UploadBatchProfile {
        if (rawJson.isNullOrBlank()) return Legacy
        return runCatching {
            val capabilities = JSONObject(rawJson).optJSONObject("uploadCapabilities")
                ?: return Legacy
            if (capabilities.optInt("protocolVersion", SUPPORTED_PROTOCOL_VERSION) != SUPPORTED_PROTOCOL_VERSION) {
                return Legacy
            }
            val compression = capabilities.optJSONArray("requestCompression")
            val supportsGzip = compression?.let { encodings ->
                (0 until encodings.length()).any { index ->
                    encodings.optString(index).equals("gzip", ignoreCase = true)
                }
            } == true
            val defaults = if (supportsGzip) CompressedDefault else Legacy
            val limits = capabilities.optJSONObject("batchLimits")
            defaults.copy(
                recordLimit = limits.readLimit("records", defaults.recordLimit, MAX_RECORDS),
                valueLimit = limits.readLimit("values", defaults.valueLimit, MAX_VALUES),
                aggregateLimit = limits.readLimit("aggregates", defaults.aggregateLimit, MAX_AGGREGATES)
            )
        }.getOrDefault(Legacy)
    }

    private fun JSONObject?.readLimit(name: String, fallback: Int, maximum: Int): Int {
        val value = this?.optInt(name, fallback) ?: fallback
        return if (value > 0) value.coerceAtMost(maximum) else fallback
    }

    private const val MAX_RECORDS = 5_000
    private const val MAX_VALUES = 20_000
    private const val MAX_AGGREGATES = 5_000
    private const val SUPPORTED_PROTOCOL_VERSION = 1
}
