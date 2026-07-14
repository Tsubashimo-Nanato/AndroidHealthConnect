package com.example.healthconnectandroid.ui.sleep

import com.example.healthconnectandroid.hc.InspectorTimeRange
import com.example.healthconnectandroid.hc.SleepQualityMatrixModel
import com.example.healthconnectandroid.hc.SleepSessionAnalyzer
import com.example.healthconnectandroid.hc.SleepSessionInput
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SleepQueryWindow(
    val start: Instant,
    val end: Instant,
    val key: String
)

fun sleepMatrixForAnchor(
    range: InspectorTimeRange,
    sessions: List<SleepSessionInput>,
    anchorDate: LocalDate,
    weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    zoneId: ZoneId = ZoneId.systemDefault()
): SleepQualityMatrixModel =
    when (range) {
        InspectorTimeRange.MONTHLY -> SleepSessionAnalyzer.monthlyMatrix(
            sessions = sessions,
            startDate = anchorDate.withDayOfMonth(1),
            endDate = anchorDate
                .withDayOfMonth(anchorDate.lengthOfMonth())
                .coerceAtMost(LocalDate.now(zoneId)),
            zoneId = zoneId,
            weekStart = weekStart
        )
        else -> SleepSessionAnalyzer.weeklyMatrix(
            sessions = sessions,
            startDate = sleepVisibleStartDate(range, anchorDate),
            zoneId = zoneId
        )
    }

fun sleepMatrixCache(
    range: InspectorTimeRange,
    sessions: List<SleepSessionInput>,
    centerDate: LocalDate,
    weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    zoneId: ZoneId = ZoneId.systemDefault()
): Map<String, SleepQualityMatrixModel> =
    (-3..3).associate { offset ->
        val anchor = if (range == InspectorTimeRange.MONTHLY) {
            centerDate.withDayOfMonth(1).plusMonths(offset.toLong())
        } else {
            centerDate.plusWeeks(offset.toLong())
        }.coerceSleepAnchor(range, zoneId)
        sleepMatrixKey(range, anchor) to sleepMatrixForAnchor(range, sessions, anchor, weekStart, zoneId)
    }

fun sleepVisibleStartDate(
    range: InspectorTimeRange,
    anchorDate: LocalDate
): LocalDate =
    if (range == InspectorTimeRange.MONTHLY) {
        anchorDate.withDayOfMonth(1)
    } else anchorDate.minusDays(6)

fun sleepVisibleEndDate(
    range: InspectorTimeRange,
    anchorDate: LocalDate
): LocalDate =
    if (range == InspectorTimeRange.MONTHLY) {
        anchorDate.withDayOfMonth(anchorDate.lengthOfMonth())
    } else anchorDate

fun sleepWindowEndInstant(
    range: InspectorTimeRange,
    anchorDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now()
): Instant {
    val today = now.atZone(zoneId).toLocalDate()
    return if (range == InspectorTimeRange.MONTHLY) {
        val monthStart = anchorDate.withDayOfMonth(1)
        val currentMonthStart = today.withDayOfMonth(1)
        if (monthStart == currentMonthStart) {
            now
        } else {
            monthStart.plusMonths(1).atStartOfDay(zoneId).toInstant()
        }
    } else if (sleepVisibleEndDate(range, anchorDate) >= today) {
        now
    } else {
        sleepVisibleEndDate(range, anchorDate).plusDays(1).atStartOfDay(zoneId).toInstant()
    }
}

fun sleepDataQueryWindow(
    range: InspectorTimeRange,
    centerDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
): SleepQueryWindow {
    val today = LocalDate.now(zoneId)
    return if (range == InspectorTimeRange.MONTHLY) {
        val centerMonth = centerDate.withDayOfMonth(1)
        val start = centerMonth.minusMonths(3).atStartOfDay(zoneId).toInstant()
        val endMonth = centerMonth.plusMonths(3)
        val currentMonth = today.withDayOfMonth(1)
        val end = if (!endMonth.isBefore(currentMonth)) {
            Instant.now()
        } else {
            endMonth.plusMonths(1).atStartOfDay(zoneId).toInstant()
        }
        SleepQueryWindow(start = start, end = end, key = "month:${centerMonth}")
    } else {
        val startDate = centerDate.minusWeeks(3).minusDays(6)
        val endDate = centerDate.plusWeeks(3).coerceAtMost(today)
        val end = if (endDate == today) Instant.now() else endDate.plusDays(1).atStartOfDay(zoneId).toInstant()
        SleepQueryWindow(
            start = startDate.atStartOfDay(zoneId).toInstant(),
            end = end,
            key = "week:${centerDate}"
        )
    }
}

fun sleepMatrixKey(
    range: InspectorTimeRange,
    anchorDate: LocalDate
): String =
    if (range == InspectorTimeRange.MONTHLY) {
        "month:${anchorDate.withDayOfMonth(1)}"
    } else {
        "week:${sleepVisibleStartDate(range, anchorDate)}"
    }

fun sleepWindowLabel(
    range: InspectorTimeRange,
    anchorDate: LocalDate
): String =
    if (range == InspectorTimeRange.MONTHLY) {
        anchorDate.withDayOfMonth(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    } else {
        val start = sleepVisibleStartDate(range, anchorDate)
        compactSleepWeekLabel(start, start.plusDays(6))
    }

private fun compactSleepWeekLabel(start: LocalDate, end: LocalDate): String =
    when {
        start.year == end.year && start.month == end.month ->
            "${start.format(compactMonthFormatter)} ${start.dayOfMonth}-${end.dayOfMonth}"
        start.year == end.year ->
            "${start.format(compactMonthDayFormatter)}-${end.format(compactMonthDayFormatter)}"
        else ->
            "${start.format(compactMonthDayYearFormatter)}-${end.format(compactMonthDayYearFormatter)}"
    }

fun sleepShiftWindowDate(
    range: InspectorTimeRange,
    anchorDate: LocalDate,
    cells: Int,
    zoneId: ZoneId = ZoneId.systemDefault()
): LocalDate {
    val today = LocalDate.now(zoneId)
    return if (range == InspectorTimeRange.MONTHLY) {
        val currentMonth = today.withDayOfMonth(1)
        val targetMonth = anchorDate.withDayOfMonth(1).plusMonths(cells.coerceIn(-1, 1).toLong())
        if (targetMonth.isAfter(currentMonth)) today else targetMonth
    } else {
        anchorDate.plusWeeks(cells.coerceIn(-1, 1).toLong()).coerceAtMost(today)
    }
}

fun sleepAnchorInsideQueryWindow(
    range: InspectorTimeRange,
    anchorDate: LocalDate,
    centerDate: LocalDate
): Boolean =
    if (range == InspectorTimeRange.MONTHLY) {
        val month = anchorDate.withDayOfMonth(1)
        val center = centerDate.withDayOfMonth(1)
        !month.isBefore(center.minusMonths(3)) && !month.isAfter(center.plusMonths(3))
    } else {
        !anchorDate.isBefore(centerDate.minusWeeks(3)) && !anchorDate.isAfter(centerDate.plusWeeks(3))
    }

private fun LocalDate.coerceSleepAnchor(range: InspectorTimeRange, zoneId: ZoneId): LocalDate {
    val today = LocalDate.now(zoneId)
    return if (range == InspectorTimeRange.MONTHLY) {
        val currentMonth = today.withDayOfMonth(1)
        val month = withDayOfMonth(1)
        if (month.isAfter(currentMonth)) today else month
    } else {
        coerceAtMost(today)
    }
}

fun defaultSleepSelection(model: SleepQualityMatrixModel): Set<String> =
    model.boxes
        .lastOrNull { it.sessionCount > 0 }
        ?.let { setOf(it.id) }
        ?: emptySet()

fun filterSleepSessionsForSelection(
    sessions: List<SleepSessionUiModel>,
    model: SleepQualityMatrixModel,
    selectedBoxIds: Set<String>,
    zoneId: ZoneId = ZoneId.systemDefault()
): List<SleepSessionUiModel> {
    val selectedBoxes = model.boxes.filter { it.id in selectedBoxIds }
    if (selectedBoxes.isEmpty()) return emptyList()
    return sessions.filter { session ->
        val date = session.analysisStart?.atZone(zoneId)?.toLocalDate() ?: return@filter false
        selectedBoxes.any { box -> !date.isBefore(box.startDate) && !date.isAfter(box.endDate) }
    }
}

private val compactMonthDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.US)

private val compactMonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM", Locale.US)

private val compactMonthDayYearFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
