package com.example.healthconnectandroid.hc.sync

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SyncDateWindow(
    val localDate: LocalDate,
    val start: Instant,
    val end: Instant
)

object SyncWindowPlanner {
    fun missingDailyWindows(
        requestedStart: Instant,
        requestedEnd: Instant,
        zoneId: ZoneId,
        coveredLocalDates: Set<LocalDate>
    ): List<SyncDateWindow> {
        if (!requestedStart.isBefore(requestedEnd)) return emptyList()

        var date = LocalDate.ofInstant(requestedStart, zoneId)
        val lastDate = LocalDate.ofInstant(requestedEnd.minusMillis(1), zoneId)
        val windows = mutableListOf<SyncDateWindow>()

        while (!date.isAfter(lastDate)) {
            if (date !in coveredLocalDates) {
                val dayStart = date.atStartOfDay(zoneId).toInstant()
                val dayEnd = date.plusDays(1).atStartOfDay(zoneId).toInstant()
                val start = maxInstant(requestedStart, dayStart)
                val end = minInstant(requestedEnd, dayEnd)
                if (start.isBefore(end)) {
                    windows += SyncDateWindow(
                        localDate = date,
                        start = start,
                        end = end
                    )
                }
            }
            date = date.plusDays(1)
        }

        return windows
    }
}

private fun maxInstant(a: Instant, b: Instant): Instant = if (a.isAfter(b)) a else b

private fun minInstant(a: Instant, b: Instant): Instant = if (a.isBefore(b)) a else b
