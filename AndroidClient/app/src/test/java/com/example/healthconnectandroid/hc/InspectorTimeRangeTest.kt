package com.example.healthconnectandroid.hc

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class InspectorTimeRangeTest {
    @Test
    fun weeklyUsesSevenDayWindow() {
        val end = Instant.parse("2026-05-10T12:00:00Z")

        assertEquals(end.minus(Duration.ofDays(7)), InspectorTimeRange.WEEKLY.startBefore(end))
        assertEquals(LocalDate.of(2026, 5, 4), InspectorTimeRange.WEEKLY.startLocalDate(LocalDate.of(2026, 5, 10)))
    }

    @Test
    fun monthlyUsesCalendarMonthWindowNotThreeDays() {
        val end = Instant.parse("2026-05-10T12:00:00Z")
        val zoneId = ZoneId.systemDefault()

        assertEquals(
            LocalDate.of(2026, 5, 1).atStartOfDay(zoneId).toInstant(),
            InspectorTimeRange.MONTHLY.startBefore(end)
        )
        assertEquals(LocalDate.of(2026, 5, 1), InspectorTimeRange.MONTHLY.startLocalDate(LocalDate.of(2026, 5, 10)))
    }

    @Test
    fun monthlyStartUsesSelectedTimezone() {
        val end = Instant.parse("2026-05-01T01:00:00Z")

        assertEquals(
            LocalDate.of(2026, 5, 1).atStartOfDay(ZoneId.of("UTC")).toInstant(),
            InspectorTimeRange.MONTHLY.startBefore(end, ZoneId.of("UTC"))
        )
        assertEquals(
            LocalDate.of(2026, 4, 1).atStartOfDay(ZoneId.of("America/Los_Angeles")).toInstant(),
            InspectorTimeRange.MONTHLY.startBefore(end, ZoneId.of("America/Los_Angeles"))
        )
    }
}
