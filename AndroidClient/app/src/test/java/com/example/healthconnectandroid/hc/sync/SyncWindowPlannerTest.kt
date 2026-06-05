package com.example.healthconnectandroid.hc.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncWindowPlannerTest {
    private val zoneId = ZoneId.of("Asia/Tokyo")

    @Test
    fun missingDailyWindowsSkipsFullyCoveredLocalDates() {
        val coveredDate = LocalDate.parse("2026-05-02")
        val windows = SyncWindowPlanner.missingDailyWindows(
            requestedStart = Instant.parse("2026-05-01T00:00:00Z"),
            requestedEnd = Instant.parse("2026-05-04T00:00:00Z"),
            zoneId = zoneId,
            coveredWindows = listOf(
                SyncCoverageWindow(
                    start = coveredDate.atStartOfDay(zoneId).toInstant(),
                    end = coveredDate.plusDays(1).atStartOfDay(zoneId).toInstant()
                )
            )
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
    fun missingDailyWindowsKeepsPartiallyCoveredDays() {
        val date = LocalDate.parse("2026-05-02")
        val windows = SyncWindowPlanner.missingDailyWindows(
            requestedStart = date.atStartOfDay(zoneId).toInstant(),
            requestedEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant(),
            zoneId = zoneId,
            coveredWindows = listOf(
                SyncCoverageWindow(
                    start = date.atTime(17, 0).atZone(zoneId).toInstant(),
                    end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
                )
            )
        )

        assertEquals(listOf(date), windows.map { it.localDate })
    }

    @Test
    fun missingDailyWindowsMergesAdjacentCoverageWindows() {
        val date = LocalDate.parse("2026-05-02")
        val windows = SyncWindowPlanner.missingDailyWindows(
            requestedStart = date.atStartOfDay(zoneId).toInstant(),
            requestedEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant(),
            zoneId = zoneId,
            coveredWindows = listOf(
                SyncCoverageWindow(
                    start = date.atStartOfDay(zoneId).toInstant(),
                    end = date.atTime(12, 0).atZone(zoneId).toInstant()
                ),
                SyncCoverageWindow(
                    start = date.atTime(12, 0).atZone(zoneId).toInstant(),
                    end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
                )
            )
        )

        assertEquals(emptyList<SyncDateWindow>(), windows)
    }

    @Test
    fun missingDailyWindowsClipsPartialBoundaryDays() {
        val windows = SyncWindowPlanner.missingDailyWindows(
            requestedStart = Instant.parse("2026-05-01T03:30:00Z"),
            requestedEnd = Instant.parse("2026-05-02T07:15:00Z"),
            zoneId = ZoneId.of("UTC"),
            coveredWindows = emptyList()
        )

        assertEquals(2, windows.size)
        assertEquals(Instant.parse("2026-05-01T03:30:00Z"), windows.first().start)
        assertEquals(Instant.parse("2026-05-02T00:00:00Z"), windows.first().end)
        assertEquals(Instant.parse("2026-05-02T00:00:00Z"), windows.last().start)
        assertEquals(Instant.parse("2026-05-02T07:15:00Z"), windows.last().end)
    }
}
