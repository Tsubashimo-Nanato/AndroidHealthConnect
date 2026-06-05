package com.example.healthconnectandroid.hc.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class SyncDateWindow(
    val localDate: LocalDate,
    val start: Instant,
    val end: Instant
)

data class SyncCoverageWindow(
    val start: Instant,
    val end: Instant
)

object SyncWindowPlanner {
    fun missingDailyWindows(
        requestedStart: Instant,
        requestedEnd: Instant,
        zoneId: ZoneId,
        coveredWindows: List<SyncCoverageWindow>
    ): List<SyncDateWindow> {
        if (!requestedStart.isBefore(requestedEnd)) return emptyList()

        var date = LocalDate.ofInstant(requestedStart, zoneId)
        val lastDate = LocalDate.ofInstant(requestedEnd.minusMillis(1), zoneId)
        val mergedCoverage = coveredWindows
            .filter { it.start.isBefore(it.end) }
            .sortedBy { it.start }
        val windows = mutableListOf<SyncDateWindow>()

        while (!date.isAfter(lastDate)) {
            val dayStart = date.atStartOfDay(zoneId).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val start = maxInstant(requestedStart, dayStart)
            val end = minInstant(requestedEnd, dayEnd)
            if (start.isBefore(end) && !isFullyCovered(start, end, mergedCoverage)) {
                windows += SyncDateWindow(
                    localDate = date,
                    start = start,
                    end = end
                )
            }
            date = date.plusDays(1)
        }

        return windows
    }

    fun dailyWindowCount(
        requestedStart: Instant,
        requestedEnd: Instant,
        zoneId: ZoneId
    ): Int {
        if (!requestedStart.isBefore(requestedEnd)) return 0
        val firstDate = LocalDate.ofInstant(requestedStart, zoneId)
        val lastDate = LocalDate.ofInstant(requestedEnd.minusMillis(1), zoneId)
        return ChronoUnit.DAYS.between(firstDate, lastDate).toInt() + 1
    }

    private fun isFullyCovered(
        start: Instant,
        end: Instant,
        windows: List<SyncCoverageWindow>
    ): Boolean {
        var cursor = start
        for (window in windows) {
            if (!window.end.isAfter(cursor)) continue
            if (window.start.isAfter(cursor)) return false
            if (window.end.isAfter(cursor)) {
                cursor = window.end
                if (!cursor.isBefore(end)) return true
            }
        }
        return false
    }
}

private fun maxInstant(a: Instant, b: Instant): Instant = if (a.isAfter(b)) a else b

private fun minInstant(a: Instant, b: Instant): Instant = if (a.isBefore(b)) a else b
