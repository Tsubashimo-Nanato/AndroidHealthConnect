package com.example.healthconnectandroid.hc

import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class HrDateSelectionMode(val label: String) {
    WEEK("Week"),
    MONTH("Month")
}

enum class HrDateQuality(val label: String) {
    NORMAL("Normal"),
    ELEVATED("Elevated"),
    HIGH("High"),
    NO_DATA("No data")
}

data class HrDateQualitySummary(
    val quality: HrDateQuality,
    val sampleCount: Int,
    val medianBpm: Double?,
    val highPercentileBpm: Double?
)

data class HrDateRange(
    val start: Instant,
    val end: Instant
)

object HeartRateDateAnalysis {
    const val MAX_SELECTED_DAYS = 7

    fun latestDataDate(dataDates: Collection<LocalDate>, today: LocalDate): LocalDate? =
        dataDates
            .asSequence()
            .filter { it <= today }
            .maxOrNull()

    fun defaultSelectedDate(dataDates: Collection<LocalDate>, today: LocalDate): LocalDate =
        latestDataDate(dataDates, today) ?: today

    fun visibleDates(
        anchorDate: LocalDate,
        today: LocalDate,
        mode: HrDateSelectionMode = HrDateSelectionMode.WEEK,
        weekStart: DayOfWeek = DayOfWeek.SUNDAY
    ): List<LocalDate> {
        val end = anchorDate.coerceAtMost(today)
        return when (mode) {
            HrDateSelectionMode.WEEK -> {
                val start = end.with(TemporalAdjusters.previousOrSame(weekStart))
                List(7) { start.plusDays(it.toLong()) }
            }
            HrDateSelectionMode.MONTH -> monthMatrixDates(YearMonth.from(end), weekStart)
        }
    }

    fun shiftAnchor(
        anchorDate: LocalDate,
        cells: Int,
        today: LocalDate,
        mode: HrDateSelectionMode = HrDateSelectionMode.WEEK
    ): LocalDate =
        when (mode) {
            HrDateSelectionMode.WEEK -> anchorDate.plusWeeks(cells.toLong()).coerceAtMost(today)
            HrDateSelectionMode.MONTH -> anchorDate.plusMonths(cells.toLong()).coerceAtMost(today)
        }

    fun weekStripDates(
        startDate: LocalDate,
        today: LocalDate
    ): List<LocalDate> {
        if (startDate > today) return listOf(today)
        return generateSequence(startDate) { date ->
            val next = date.plusDays(1)
            if (next <= today) next else null
        }.toList()
    }

    fun monthMatrixDates(
        month: YearMonth,
        weekStart: DayOfWeek = DayOfWeek.SUNDAY
    ): List<LocalDate> {
        val firstOfMonth = month.atDay(1)
        val gridStart = firstOfMonth.with(TemporalAdjusters.previousOrSame(weekStart))
        val lastOfMonth = month.atEndOfMonth()
        val weekEnd = weekStart.plus(6)
        val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(weekEnd))
        return generateSequence(gridStart) { date ->
            val next = date.plusDays(1)
            if (next <= gridEnd) next else null
        }.toList()
    }

    fun clampSelection(start: LocalDate, end: LocalDate, maxDays: Int = MAX_SELECTED_DAYS): Set<LocalDate> {
        val maxOffset = (maxDays - 1).coerceAtLeast(0).toLong()
        val first: LocalDate
        val last: LocalDate
        if (end >= start) {
            first = start
            last = minOf(end, start.plusDays(maxOffset))
        } else {
            first = maxOf(end, start.minusDays(maxOffset))
            last = start
        }
        return generateSequence(first) { date ->
            if (date < last) date.plusDays(1) else null
        }.toSet()
    }

    fun visibleRangeForDates(dates: Set<LocalDate>, zoneId: ZoneId = ZoneId.systemDefault()): HrDateRange? {
        if (dates.isEmpty()) return null
        val first = dates.minOrNull() ?: return null
        val last = dates.maxOrNull() ?: return null
        return HrDateRange(
            start = first.atStartOfDay(zoneId).toInstant(),
            end = last.plusDays(1).atStartOfDay(zoneId).toInstant()
        )
    }

    fun qualityFor(values: List<Double>, zones: HeartRateReferenceZones): HrDateQualitySummary {
        val clean = values.filter { it in 25.0..240.0 }.sorted()
        if (clean.isEmpty()) {
            return HrDateQualitySummary(
                quality = HrDateQuality.NO_DATA,
                sampleCount = 0,
                medianBpm = null,
                highPercentileBpm = null
            )
        }
        val median = percentile(clean, 0.50)
        val p90 = percentile(clean, 0.90)
        val elevatedStart = zones.bands
            .firstOrNull { it.tone == HeartRateZoneTone.ELEVATED }
            ?.lowerBpm
            ?: 100.0
        val highStart = zones.bands
            .firstOrNull { it.tone == HeartRateZoneTone.HIGH }
            ?.lowerBpm
            ?: 140.0
        val highShare = clean.count { it >= highStart }.toDouble() / clean.size
        val elevatedShare = clean.count { it >= elevatedStart }.toDouble() / clean.size
        val quality = when {
            highShare >= 0.10 || p90 >= highStart -> HrDateQuality.HIGH
            elevatedShare >= 0.25 || median >= elevatedStart -> HrDateQuality.ELEVATED
            else -> HrDateQuality.NORMAL
        }
        return HrDateQualitySummary(
            quality = quality,
            sampleCount = clean.size,
            medianBpm = median,
            highPercentileBpm = p90
        )
    }

    private fun percentile(sortedValues: List<Double>, percentile: Double): Double {
        if (sortedValues.isEmpty()) return 0.0
        val rawIndex = ((sortedValues.size - 1) * percentile).toInt()
            .coerceIn(0, sortedValues.lastIndex)
        return sortedValues[rawIndex]
    }
}
