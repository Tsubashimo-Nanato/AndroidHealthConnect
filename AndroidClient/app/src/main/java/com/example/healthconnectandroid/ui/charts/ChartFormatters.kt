package com.example.healthconnectandroid.ui.charts

import com.example.healthconnectandroid.hc.HealthDisplayFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun formatEpoch(value: Long?, zoneId: ZoneId): String =
    value?.let { HealthDisplayFormatter.formatInstantForUi(Instant.ofEpochMilli(it), zoneId) } ?: ""

fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value).trimEnd('0').trimEnd('.')

fun formatTimeTick(
    epochMillis: Long,
    visibleDurationMillis: Long,
    zoneId: ZoneId
): String = when {
    visibleDurationMillis <= 36L * 60L * 60L * 1000L ->
        shortTimeTickFormatter.withZone(zoneId).format(Instant.ofEpochMilli(epochMillis))
    else ->
        shortDateTickInstantFormatter.withZone(zoneId).format(Instant.ofEpochMilli(epochMillis))
}

fun formatDateTick(localDate: String): String =
    runCatching { LocalDate.parse(localDate).format(shortDateTickLocalFormatter) }
        .getOrDefault(localDate)

fun compactEpochRange(startEpochMillis: Long, endEpochMillis: Long, zoneId: ZoneId): String {
    val start = Instant.ofEpochMilli(startEpochMillis).atZone(zoneId)
    val end = Instant.ofEpochMilli(endEpochMillis).atZone(zoneId)
    return when {
        start.toLocalDate() == end.toLocalDate() ->
            "${start.format(compactMonthDayFormatter)} ${start.format(shortTimeOnlyFormatter)}-${end.format(shortTimeOnlyFormatter)}"
        start.year == end.year ->
            "${start.format(compactMonthDayFormatter)}-${end.format(compactMonthDayFormatter)}"
        else ->
            "${start.format(compactDateFormatter)}-${end.format(compactDateFormatter)}"
    }
}

fun compactLocalDateRange(startLocalDate: String?, endLocalDate: String?): String {
    if (startLocalDate.isNullOrBlank() || endLocalDate.isNullOrBlank()) return "No dates"
    val start = runCatching { LocalDate.parse(startLocalDate) }.getOrNull()
    val end = runCatching { LocalDate.parse(endLocalDate) }.getOrNull()
    if (start == null || end == null) return "$startLocalDate-$endLocalDate"
    return if (start == end) {
        start.format(compactMonthDayFormatter)
    } else {
        "${start.format(compactMonthDayFormatter)}-${end.format(compactMonthDayFormatter)}"
    }
}

private val shortTimeTickFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")

private val shortDateTickInstantFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M/d")

private val shortDateTickLocalFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("M/d")

private val shortTimeOnlyFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")

private val compactMonthDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d")

private val compactDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy")
