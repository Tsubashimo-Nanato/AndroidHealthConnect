package com.example.healthconnectandroid.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StatusToneForMessageTest {
    @Test
    fun completeBackgroundSyncWithZeroErrorsIsSuccess() {
        val tone = statusToneForMessage(
            "Background sync complete: types=8, inserted=0, updated=0, " +
                "duplicates=0, summaries=0, skipped=0, timeouts=0, cancelled=0, errors=0"
        )

        assertEquals(StatusTone.Success, tone)
    }

    @Test
    fun completeBackgroundSyncWithZeroErrorsPhraseIsSuccess() {
        val tone = statusToneForMessage("Background sync complete with 0 errors")

        assertEquals(StatusTone.Success, tone)
    }

    @Test
    fun nonzeroErrorsAreWarning() {
        val tone = statusToneForMessage("Background sync complete: types=8, errors=2")

        assertEquals(StatusTone.Warning, tone)
    }

    @Test
    fun failedMessagesAreError() {
        val tone = statusToneForMessage("Background sync failed: Health Connect unavailable")

        assertEquals(StatusTone.Error, tone)
    }
}
