package com.example.healthconnectandroid.hc.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWindowPlannerTest {
    private val zoneId = ZoneId.of("Asia/Tokyo")

    @Test
    fun missingDailyWindowsSkipsCoveredLocalDates() {
        val windows = SyncWindowPlanner.missingDailyWindows(
            requestedStart = Instant.parse("2026-05-01T00:00:00Z"),
            requestedEnd = Instant.parse("2026-05-04T00:00:00Z"),
            zoneId = zoneId,
            coveredLocalDates = setOf(LocalDate.parse("2026-05-02"))
        )

        assertEquals(
            listOf(
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-03"),
                LocalDate.parse("2026-05-04")
            ),
            windows.map { it.localDate }
        )
    }

    @Test
    fun missingDailyWindowsClipsPartialBoundaryDays() {
        val windows = SyncWindowPlanner.missingDailyWindows(
            requestedStart = Instant.parse("2026-05-01T03:30:00Z"),
            requestedEnd = Instant.parse("2026-05-02T07:15:00Z"),
            zoneId = ZoneId.of("UTC"),
            coveredLocalDates = emptySet()
        )

        assertEquals(2, windows.size)
        assertEquals(Instant.parse("2026-05-01T03:30:00Z"), windows.first().start)
        assertEquals(Instant.parse("2026-05-02T00:00:00Z"), windows.first().end)
        assertEquals(Instant.parse("2026-05-02T00:00:00Z"), windows.last().start)
        assertEquals(Instant.parse("2026-05-02T07:15:00Z"), windows.last().end)
    }
}
