package com.example.healthconnectandroid.ui

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.sync.SyncResultSeverity
import com.example.healthconnectandroid.hc.sync.SyncResultSeverityPolicy
import com.example.healthconnectandroid.hc.upload.UploadRunResult
import com.example.healthconnectandroid.hc.upload.UploadTimeRange

enum class UploadRetryAction {
    NONE,
    QUEUE_ALL,
    RETRY_SCOPED_MANUALLY
}

data class UploadCompletionStatus(
    val message: String,
    val retryAction: UploadRetryAction
)

fun uploadStartStatus(range: UploadTimeRange): String =
    if (range == UploadTimeRange.ALL) {
        "Uploading local data..."
    } else {
        "Uploading local data (${range.label})..."
    }

fun uploadCompletionStatus(
    result: UploadRunResult,
    range: UploadTimeRange
): UploadCompletionStatus {
    if (result.success || !result.retryable) {
        return UploadCompletionStatus(result.message, UploadRetryAction.NONE)
    }
    if (range == UploadTimeRange.ALL) {
        return UploadCompletionStatus("${result.message}. Retry queued.", UploadRetryAction.QUEUE_ALL)
    }
    return UploadCompletionStatus(
        "${result.message}. Retry ${range.label} manually.",
        UploadRetryAction.RETRY_SCOPED_MANUALLY
    )
}

fun syncResultsStatusTone(results: List<HealthDataTypeSyncResult>): StatusTone =
    when (SyncResultSeverityPolicy.fromResults(results)) {
        SyncResultSeverity.NEUTRAL -> StatusTone.Neutral
        SyncResultSeverity.SUCCESS -> StatusTone.Success
        SyncResultSeverity.WARNING -> StatusTone.Warning
        SyncResultSeverity.ERROR -> StatusTone.Error
    }
