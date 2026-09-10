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

    @Test
    fun zeroErrorCompletionUsesSuccessTone() {
        assertEquals(
            StatusTone.Success,
            statusToneForMessage("Sync new data complete: types 18, errors 0")
        )
    }

    @Test
    fun zeroSkippedPeriodicSummaryUsesSuccessTone() {
        assertEquals(
            StatusTone.Success,
            statusToneForMessage(
                "Periodic sync is enabled and scheduled. Last run: 2026-09-10T19:49:21Z (success). " +
                    "types=13, read=0, skipped=0, errors=0"
            )
        )
    }

    @Test
    fun backgroundReadStatusNamesTheMissingCapability() {
        assertEquals("Background access unavailable", backgroundReadStatusText(false, false))
        assertEquals("Background access missing", backgroundReadStatusText(true, false))
        assertEquals("Background access ready", backgroundReadStatusText(true, true))
    }
}
