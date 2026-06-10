package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.HeartRateDateAnalysis
import com.example.healthconnectandroid.hc.HrDateSelectionMode
import com.example.healthconnectandroid.ui.charts.ChartVisibleRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun heartRateVisibleRangeForDates(
    dates: Set<LocalDate>,
    zoneId: ZoneId
): ChartVisibleRange? =
    HeartRateDateAnalysis.visibleRangeForDates(dates, zoneId)?.let { range ->
        ChartVisibleRange(
            startEpochMillis = range.start.toEpochMilli(),
            endEpochMillis = range.end.toEpochMilli()
        )
    }

internal fun heartRateWindowLabel(
    mode: HrDateSelectionMode,
    anchorDate: LocalDate,
    selectedDates: Set<LocalDate>,
    today: LocalDate,
    weekStart: DayOfWeek
): String {
    return when (mode) {
        HrDateSelectionMode.WEEK -> {
            val labelAnchor = selectedDates.maxOrNull() ?: anchorDate
            val dates = HeartRateDateAnalysis.visibleDates(
                anchorDate = labelAnchor,
                today = today,
                mode = mode,
                weekStart = weekStart
            )
            compactDateRange(dates.firstOrNull(), dates.lastOrNull())
        }
        HrDateSelectionMode.MONTH -> DateTimeFormatter.ofPattern("MMM yyyy")
            .format(YearMonth.from(anchorDate))
    }
}

internal fun heartRateDateCellLabel(date: LocalDate): String =
    DateTimeFormatter.ofPattern("E d").format(date)

internal fun compactDateRange(start: LocalDate?, end: LocalDate?): String {
    if (start == null || end == null) return "No dates"
    if (start == end) return compactDate(start)
    return if (start.month == end.month && start.year == end.year) {
        "${DateTimeFormatter.ofPattern("MMM d").format(start)}-${DateTimeFormatter.ofPattern("d").format(end)}"
    } else {
        "${compactDate(start)}-${compactDate(end)}"
    }
}

private fun compactDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("MMM d").format(date)
