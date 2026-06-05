package com.example.healthconnectandroid.hc.upload

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UploadTimeRangeTest {
    @Test
    fun allHasNoStartCutoff() {
        assertNull(UploadTimeRange.ALL.startEpochMillis(Instant.parse("2026-06-04T00:00:00Z")))
    }

    @Test
    fun pastMonthUsesThirtyDayCutoff() {
        val now = Instant.parse("2026-06-04T00:00:00Z")

        assertEquals(
            Instant.parse("2026-05-05T00:00:00Z").toEpochMilli(),
            UploadTimeRange.PAST_MONTH.startEpochMillis(now)
        )
    }

    @Test
    fun pastWeekUsesSevenDayCutoff() {
        val now = Instant.parse("2026-06-04T00:00:00Z")

        assertEquals(
            Instant.parse("2026-05-28T00:00:00Z").toEpochMilli(),
            UploadTimeRange.PAST_WEEK.startEpochMillis(now)
        )
    }
}
