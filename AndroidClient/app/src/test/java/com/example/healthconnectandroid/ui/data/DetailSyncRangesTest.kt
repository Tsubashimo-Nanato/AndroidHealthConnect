package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.InspectorTimeRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailSyncRangesTest {
    private val zoneId = ZoneId.of("Asia/Tokyo")

    @Test
    fun quickRangeUsesMostRecentThirtyOneDays() {
        val now = Instant.parse("2026-06-10T12:00:00Z")

        val (start, end) = DetailSyncRanges.quickRange(now)

        assertEquals(Instant.parse("2026-05-10T12:00:00Z"), start)
        assertEquals(now, end)
    }

    @Test
    fun selectedRangeForNormalDataEndsAtNow() {
        val now = Instant.parse("2026-06-10T12:00:00Z")

        val (start, end) = DetailSyncRanges.selectedRange(
            isSleep = false,
            range = InspectorTimeRange.LAST_7_DAYS,
            sleepWindowEndDate = LocalDate.parse("2026-06-01"),
            zoneId = zoneId,
            now = now
        )

        assertEquals(now.minus(InspectorTimeRange.LAST_7_DAYS.duration), start)
        assertEquals(now, end)
    }

    @Test
    fun selectedRangeForPastSleepWeekEndsAfterAnchorDate() {
        val now = Instant.parse("2026-06-10T12:00:00Z")

        val (start, end) = DetailSyncRanges.selectedRange(
            isSleep = true,
            range = InspectorTimeRange.WEEKLY,
            sleepWindowEndDate = LocalDate.parse("2026-05-04"),
            zoneId = zoneId,
            now = now
        )

        assertEquals(Instant.parse("2026-04-27T15:00:00Z"), start)
        assertEquals(Instant.parse("2026-05-04T15:00:00Z"), end)
    }

    @Test
    fun detailEndForCurrentSleepMonthUsesNow() {
        val now = Instant.parse("2026-06-10T12:00:00Z")

        val end = DetailSyncRanges.detailEnd(
            isSleep = true,
            range = InspectorTimeRange.MONTHLY,
            sleepWindowEndDate = LocalDate.parse("2026-06-01"),
            zoneId = zoneId,
            now = now
        )

        assertEquals(now, end)
    }
}
