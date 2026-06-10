package com.example.healthconnectandroid.ui

import com.example.healthconnectandroid.hc.upload.UploadRunResult
import com.example.healthconnectandroid.hc.upload.UploadTimeRange
import org.junit.Assert.assertEquals
import org.junit.Test

class AppStatusPoliciesTest {
    @Test
    fun uploadStartStatusIncludesScopedRange() {
        assertEquals("Uploading local data...", uploadStartStatus(UploadTimeRange.ALL))
        assertEquals("Uploading local data (Past week)...", uploadStartStatus(UploadTimeRange.PAST_WEEK))
    }

    @Test
    fun retryableAllUploadQueuesRetry() {
        val status = uploadCompletionStatus(
            result = UploadRunResult(
                success = false,
                retryable = true,
                message = "Server unavailable"
            ),
            range = UploadTimeRange.ALL
        )

        assertEquals("Server unavailable. Retry queued.", status.message)
        assertEquals(UploadRetryAction.QUEUE_ALL, status.retryAction)
    }

    @Test
    fun retryableScopedUploadStaysManual() {
        val status = uploadCompletionStatus(
            result = UploadRunResult(
                success = false,
                retryable = true,
                message = "Network unavailable"
            ),
            range = UploadTimeRange.PAST_MONTH
        )

        assertEquals("Network unavailable. Retry Past month manually.", status.message)
        assertEquals(UploadRetryAction.RETRY_SCOPED_MANUALLY, status.retryAction)
    }
}
