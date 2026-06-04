package com.example.healthconnectandroid.hc.upload

import java.time.Instant

enum class UploadServerMode(val label: String) {
    PRODUCTION("Production"),
    LOCAL_DEBUG("Local debug")
}

data class UploadSettings(
    val serverMode: UploadServerMode = UploadServerMode.PRODUCTION,
    val localBaseUrl: String = UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL,
    val apiKey: String = "",
    val deviceId: String
)

data class UploadPendingCounts(
    val records: Int = 0,
    val values: Int = 0,
    val aggregates: Int = 0
) {
    val total: Int get() = records + values + aggregates

    companion object {
        val Empty = UploadPendingCounts()
    }
}

enum class UploadResultSeverity {
    IDLE,
    SUCCESS,
    WARNING,
    ERROR
}

data class UploadStatus(
    val lastUploadEpochMillis: Long? = null,
    val lastResult: String = "No upload yet",
    val severity: UploadResultSeverity = UploadResultSeverity.IDLE,
    val pendingCount: Int = 0,
    val serverMode: UploadServerMode = UploadServerMode.PRODUCTION,
    val connectionResult: String? = null
)

data class UploadProgress(
    val phase: String,
    val currentBatch: Int,
    val uploadedItems: Int,
    val totalPendingItems: Int,
    val currentType: String? = null
) {
    val determinate: Boolean get() = totalPendingItems > 0
    val fraction: Float
        get() = if (totalPendingItems <= 0) 0f else uploadedItems.toFloat() / totalPendingItems.toFloat()
}

data class UploadEndpoint(
    val mode: UploadServerMode,
    val baseUrl: String,
    val statusUrl: String,
    val ingestBatchesUrl: String,
    val serverKey: String
)

sealed interface UploadEndpointValidation {
    data class Valid(val endpoint: UploadEndpoint) : UploadEndpointValidation
    data class Invalid(val reason: String) : UploadEndpointValidation
}

enum class UploadFailureKind {
    NONE,
    INVALID_URL,
    API_KEY_INVALID,
    NETWORK,
    SERVER_VALIDATION,
    SERVER,
    CANCELLED
}

data class UploadRunResult(
    val success: Boolean,
    val retryable: Boolean,
    val failureKind: UploadFailureKind = UploadFailureKind.NONE,
    val message: String,
    val uploadedRecords: Int = 0,
    val uploadedValues: Int = 0,
    val uploadedAggregates: Int = 0,
    val errors: Int = 0,
    val lastUploadTime: Instant? = null,
    val pendingCounts: UploadPendingCounts = UploadPendingCounts.Empty,
    val serverMode: UploadServerMode = UploadServerMode.PRODUCTION
) {
    val uploadedItems: Int get() = uploadedRecords + uploadedValues + uploadedAggregates
}

data class UploadConnectionResult(
    val success: Boolean,
    val retryable: Boolean,
    val failureKind: UploadFailureKind = UploadFailureKind.NONE,
    val message: String,
    val serverMode: UploadServerMode
)

fun UploadRunResult.toStatus(): UploadStatus =
    UploadStatus(
        lastUploadEpochMillis = lastUploadTime?.toEpochMilli(),
        lastResult = message,
        severity = when {
            success -> UploadResultSeverity.SUCCESS
            retryable -> UploadResultSeverity.WARNING
            else -> UploadResultSeverity.ERROR
        },
        pendingCount = pendingCounts.total,
        serverMode = serverMode
    )

fun UploadConnectionResult.toStatus(previous: UploadStatus, pendingCount: Int): UploadStatus =
    previous.copy(
        connectionResult = message,
        severity = when {
            success -> UploadResultSeverity.SUCCESS
            retryable -> UploadResultSeverity.WARNING
            else -> UploadResultSeverity.ERROR
        },
        pendingCount = pendingCount,
        serverMode = serverMode
    )
