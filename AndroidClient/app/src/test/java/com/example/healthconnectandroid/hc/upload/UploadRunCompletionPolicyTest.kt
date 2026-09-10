package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadRunCompletionPolicyTest {
    @Test
    fun completedRunHasNoError() {
        val result = UploadRunCompletionPolicy.resolve(
            pendingRows = 0,
            uploadedRows = 120,
            stoppedBecauseBatchWasEmpty = false,
            reachedBatchLimit = false
        )

        assertTrue(result.success)
        assertFalse(result.retryable)
        assertEquals(UploadFailureKind.NONE, result.failureKind)
        assertEquals(0, result.errors)
    }

    @Test
    fun intentionalBatchPauseIsRetryableWithoutServerError() {
        val result = UploadRunCompletionPolicy.resolve(
            pendingRows = 500,
            uploadedRows = 2_000,
            stoppedBecauseBatchWasEmpty = false,
            reachedBatchLimit = true
        )

        assertFalse(result.success)
        assertTrue(result.retryable)
        assertEquals(UploadFailureKind.NONE, result.failureKind)
        assertEquals(0, result.errors)
        assertEquals("Upload paused with 500 rows pending", result.message)
    }

    @Test
    fun emptyBatchWithPendingRowsRemainsAnError() {
        val result = UploadRunCompletionPolicy.resolve(
            pendingRows = 500,
            uploadedRows = 0,
            stoppedBecauseBatchWasEmpty = true,
            reachedBatchLimit = false
        )

        assertFalse(result.success)
        assertTrue(result.retryable)
        assertEquals(UploadFailureKind.SERVER, result.failureKind)
        assertEquals(1, result.errors)
    }
}
