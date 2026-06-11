package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.InspectorTimeRange
import com.example.healthconnectandroid.ui.sleep.sleepWindowEndInstant
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object DetailSyncRanges {
    private const val QUICK_SYNC_DAYS = 31L

    fun detailEnd(
        isSleep: Boolean,
        range: InspectorTimeRange,
        sleepWindowEndDate: LocalDate,
        weekStart: DayOfWeek,
        zoneId: ZoneId,
        now: Instant = Instant.now()
    ): Instant =
        if (isSleep) {
            // Sleep ranges are calendar-window based; ending at "now" would split the selected night.
            sleepWindowEndInstant(
                range = range,
                anchorDate = sleepWindowEndDate,
                weekStart = weekStart,
                zoneId = zoneId,
                now = now
            )
        } else {
            now
        }

    fun selectedRange(
        isSleep: Boolean,
        range: InspectorTimeRange,
        sleepWindowEndDate: LocalDate,
        weekStart: DayOfWeek,
        zoneId: ZoneId,
        now: Instant = Instant.now()
    ): Pair<Instant, Instant> {
        val end = detailEnd(
            isSleep = isSleep,
            range = range,
            sleepWindowEndDate = sleepWindowEndDate,
            weekStart = weekStart,
            zoneId = zoneId,
            now = now
        )
        return range.startBefore(end, zoneId) to end
    }

    fun quickRange(now: Instant = Instant.now()): Pair<Instant, Instant> =
        now.minus(QUICK_SYNC_DAYS, ChronoUnit.DAYS) to now
}
