package com.example.healthconnectandroid.hc.upload

internal data class UploadRunCompletion(
    val success: Boolean,
    val retryable: Boolean,
    val failureKind: UploadFailureKind,
    val errors: Int,
    val message: String
)

internal object UploadRunCompletionPolicy {
    fun resolve(
        pendingRows: Int,
        uploadedRows: Int,
        stoppedBecauseBatchWasEmpty: Boolean,
        reachedBatchLimit: Boolean
    ): UploadRunCompletion {
        if (pendingRows <= 0) {
            return UploadRunCompletion(
                success = true,
                retryable = false,
                failureKind = UploadFailureKind.NONE,
                errors = 0,
                message = "Upload complete: $uploadedRows rows"
            )
        }
        if (stoppedBecauseBatchWasEmpty) {
            return UploadRunCompletion(
                success = false,
                retryable = true,
                failureKind = UploadFailureKind.SERVER,
                errors = 1,
                message = "Upload stopped because pending rows could not be loaded"
            )
        }
        if (reachedBatchLimit) {
            return UploadRunCompletion(
                success = false,
                retryable = true,
                failureKind = UploadFailureKind.NONE,
                errors = 0,
                message = "Upload paused with $pendingRows rows pending"
            )
        }
        return UploadRunCompletion(
            success = false,
            retryable = true,
            failureKind = UploadFailureKind.SERVER,
            errors = 1,
            message = "Upload stopped with $pendingRows rows pending"
        )
    }
}
