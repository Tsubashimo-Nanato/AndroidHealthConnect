package com.example.healthconnectandroid.hc

import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class SleepTagTone {
    NEUTRAL,
    INFO,
    WARNING,
    ERROR,
    SUCCESS,
    DATE,
    NAP
}

enum class SleepTagRole {
    DATE,
    QUALITY,
    SESSION_TYPE,
    DETAIL
}

data class SleepSessionTag(
    val label: String,
    val tone: SleepTagTone,
    val role: SleepTagRole
)

enum class SleepQualityBand(val label: String, val tone: SleepTagTone) {
    GOOD("Good", SleepTagTone.SUCCESS),
    FAIR("Fair", SleepTagTone.SUCCESS),
    NAP("Nap", SleepTagTone.NAP),
    FRAGMENTED("Fragmented", SleepTagTone.WARNING),
    SHORT("Short", SleepTagTone.ERROR),
    UNKNOWN("Unknown", SleepTagTone.NEUTRAL)
}

data class SleepSessionAnalysis(
    val dateTag: SleepSessionTag,
    val qualityTag: SleepSessionTag,
    val flags: List<SleepSessionTag>,
    val stageCount: Int,
    val duration: Duration?,
    val tags: List<SleepSessionTag>,
    val qualityBand: SleepQualityBand,
    val explanation: String
)

data class SleepSessionInput(
    val start: Instant?,
    val end: Instant?,
    val stageCount: Int
)

data class SleepSummaryMetrics(
    val sessionCount: Int,
    val averageDuration: Duration?,
    val napCount: Int,
    val typicalQuality: SleepQualityBand,
    val lastSessionDuration: Duration?
)

data class SleepDaySummary(
    val localDate: LocalDate,
    val sessionCount: Int,
    val totalDuration: Duration,
    val napCount: Int,
    val stageCount: Int,
    val qualityBand: SleepQualityBand
)

data class SleepRangeSummary(
    val sessionCount: Int,
    val averageDuration: Duration?,
    val napCount: Int,
    val typicalQuality: SleepQualityBand,
    val lastSessionDuration: Duration?
)

data class SleepQualityMatrixBox(
    val id: String,
    val label: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val qualityBand: SleepQualityBand?,
    val sessionCount: Int
)

data class SleepQualityMatrixModel(
    val title: String,
    val columns: Int,
    val boxes: List<SleepQualityMatrixBox>
)

object SleepSessionAnalyzer {
    private const val NAP_MAX_HOURS = 4L
    private const val SHORT_MAIN_SLEEP_MAX_HOURS = 6L
    private const val GOOD_SLEEP_MIN_HOURS = 7L
    private const val VERY_SHORT_NAP_MINUTES = 90L
    private const val FRAGMENTED_STAGE_COUNT_THRESHOLD = 120

    fun analyze(
        start: Instant?,
        end: Instant?,
        stageCount: Int,
        now: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SleepSessionAnalysis {
        val localDate = start?.atZone(zoneId)?.toLocalDate()
        val duration = if (start != null && end != null && end.isAfter(start)) {
            Duration.between(start, end)
        } else {
            null
        }
        val dateTag = SleepSessionTag(dayLabel(localDate, now), SleepTagTone.DATE, SleepTagRole.DATE)

        val isNap = duration != null && duration < Duration.ofHours(NAP_MAX_HOURS)
        val isVeryShortNap = duration != null && duration < Duration.ofMinutes(VERY_SHORT_NAP_MINUTES)
        val isShortMain = duration != null &&
            duration >= Duration.ofHours(NAP_MAX_HOURS) &&
            duration < Duration.ofHours(SHORT_MAIN_SLEEP_MAX_HOURS)
        val isShort = isVeryShortNap || isShortMain
        val isFragmented = duration != null &&
            duration >= Duration.ofHours(SHORT_MAIN_SLEEP_MAX_HOURS) &&
            stageCount >= FRAGMENTED_STAGE_COUNT_THRESHOLD

        val quality = when {
            duration == null -> SleepQualityBand.UNKNOWN
            isShort -> SleepQualityBand.SHORT
            isNap -> SleepQualityBand.NAP
            isFragmented -> SleepQualityBand.FRAGMENTED
            duration >= Duration.ofHours(GOOD_SLEEP_MIN_HOURS) && stageCount > 0 -> SleepQualityBand.GOOD
            else -> SleepQualityBand.FAIR
        }
        val qualityTag = SleepSessionTag(quality.label, quality.tone, SleepTagRole.QUALITY)
        val flags = buildList {
            if (isNap) add(SleepSessionTag("Nap", SleepTagTone.NAP, SleepTagRole.SESSION_TYPE))
            if (isShort) add(SleepSessionTag("Short", SleepTagTone.ERROR, SleepTagRole.SESSION_TYPE))
        }.distinctBy { it.role to it.label }
        val tags = canonicalTags(dateTag, qualityTag, flags)

        return SleepSessionAnalysis(
            dateTag = dateTag,
            qualityTag = qualityTag,
            flags = flags,
            stageCount = stageCount,
            duration = duration,
            tags = tags,
            qualityBand = quality,
            explanation = "Placeholder visual guide using duration and stage availability only."
        )
    }

    fun summarize(analyses: List<SleepSessionAnalysis>): SleepSummaryMetrics {
        if (analyses.isEmpty()) {
            return SleepSummaryMetrics(
                sessionCount = 0,
                averageDuration = null,
                napCount = 0,
                typicalQuality = SleepQualityBand.UNKNOWN,
                lastSessionDuration = null
            )
        }
        val durations = analyses.mapNotNull { it.duration }
        val averageDuration = durations
            .takeIf { it.isNotEmpty() }
            ?.let { values -> Duration.ofMinutes(values.sumOf { it.toMinutes() } / values.size) }
        val quality = analyses
            .groupingBy { it.qualityBand }
            .eachCount()
            .maxWithOrNull(compareBy<Map.Entry<SleepQualityBand, Int>> { it.value }.thenBy { -qualityRank(it.key) })
            ?.key
            ?: SleepQualityBand.UNKNOWN
        return SleepSummaryMetrics(
            sessionCount = analyses.size,
            averageDuration = averageDuration,
            napCount = analyses.count { analysis -> analysis.flags.any { it.label == "Nap" } },
            typicalQuality = quality,
            lastSessionDuration = analyses.firstOrNull()?.duration
        )
    }

    fun summarizeRange(
        sessions: List<SleepSessionInput>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SleepRangeSummary {
        val rangedSessions = sessions.filter { input ->
            val date = input.start?.atZone(zoneId)?.toLocalDate() ?: return@filter false
            !date.isBefore(startDate) && !date.isAfter(endDate)
        }
        if (rangedSessions.isEmpty()) {
            return SleepRangeSummary(
                sessionCount = 0,
                averageDuration = null,
                napCount = 0,
                typicalQuality = SleepQualityBand.UNKNOWN,
                lastSessionDuration = null
            )
        }
        val daySummaries = dailySummaries(rangedSessions, startDate, endDate, zoneId)
            .filter { it.sessionCount > 0 }
        val averageDuration = daySummaries
            .takeIf { it.isNotEmpty() }
            ?.let { days -> Duration.ofMinutes(days.sumOf { it.totalDuration.toMinutes() } / days.size) }
        val lastSessionDuration = rangedSessions
            .filter { it.start != null && it.end != null && it.end.isAfter(it.start) }
            .maxByOrNull { it.start!! }
            ?.let { Duration.between(it.start, it.end) }
        return SleepRangeSummary(
            sessionCount = rangedSessions.size,
            averageDuration = averageDuration,
            napCount = rangedSessions.count { input -> durationOf(input)?.let(::isNapDuration) == true },
            typicalQuality = rangeQuality(daySummaries),
            lastSessionDuration = lastSessionDuration
        )
    }

    fun weeklyMatrix(
        sessions: List<SleepSessionInput>,
        startDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SleepQualityMatrixModel {
        val dates = List(7) { startDate.plusDays(it.toLong()) }
        val summaries = dailySummaries(sessions, startDate, startDate.plusDays(6), zoneId)
            .associateBy { it.localDate }
        return SleepQualityMatrixModel(
            title = "Week",
            columns = 7,
            boxes = dates.map { date ->
                val summary = summaries[date]
                SleepQualityMatrixBox(
                    id = date.toString(),
                    label = date.format(DateTimeFormatter.ofPattern("E", Locale.US)),
                    startDate = date,
                    endDate = date,
                    qualityBand = summary?.qualityBand?.takeIf { summary.sessionCount > 0 },
                    sessionCount = summary?.sessionCount ?: 0
                )
            }
        )
    }

    fun monthlyMatrix(
        sessions: List<SleepSessionInput>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        weekStart: DayOfWeek = DayOfWeek.SUNDAY
    ): SleepQualityMatrixModel {
        val monthAnchor = if (endDate.isBefore(startDate)) endDate else startDate
        val monthStart = monthAnchor.withDayOfMonth(1)
        val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        val firstCell = monthStart.with(TemporalAdjusters.previousOrSame(weekStart))
        val lastCell = monthEnd.with(TemporalAdjusters.nextOrSame(weekStart.minusOneDay()))
        val days = generateSequence(firstCell) { previous ->
            previous.plusDays(1).takeIf { !it.isAfter(lastCell) }
        }.toList()
        val summaries = dailySummaries(sessions, monthStart, monthEnd, zoneId)
            .associateBy { it.localDate }
        return SleepQualityMatrixModel(
            title = monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
            columns = 7,
            boxes = days.map { date ->
                val summary = summaries[date]
                SleepQualityMatrixBox(
                    id = date.toString(),
                    label = date.dayOfMonth.toString(),
                    startDate = date,
                    endDate = date,
                    qualityBand = summary?.qualityBand?.takeIf { summary.sessionCount > 0 && date.month == monthStart.month },
                    sessionCount = summary?.sessionCount ?: 0
                )
            }
        )
    }

    fun dailySummaries(
        sessions: List<SleepSessionInput>,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<SleepDaySummary> {
        val days = generateSequence(startDate) { previous ->
            previous.plusDays(1).takeIf { !it.isAfter(endDate) }
        }.toList()
        val sessionsByDate = sessions.groupBy { it.start?.atZone(zoneId)?.toLocalDate() }
        return days.map { date ->
            val daySessions = sessionsByDate[date].orEmpty()
            val durations = daySessions.mapNotNull(::durationOf)
            val totalDuration = Duration.ofMinutes(durations.sumOf { it.toMinutes() })
            val napCount = durations.count(::isNapDuration)
            val stageCount = daySessions.sumOf { it.stageCount }
            SleepDaySummary(
                localDate = date,
                sessionCount = daySessions.size,
                totalDuration = totalDuration,
                napCount = napCount,
                stageCount = stageCount,
                qualityBand = dailyQuality(totalDuration, daySessions.size, stageCount, napCount)
            )
        }
    }

    private fun durationOf(input: SleepSessionInput): Duration? {
        val start = input.start ?: return null
        val end = input.end ?: return null
        return if (end.isAfter(start)) Duration.between(start, end) else null
    }

    private fun isNapDuration(duration: Duration): Boolean =
        duration < Duration.ofHours(NAP_MAX_HOURS)

    private fun DayOfWeek.minusOneDay(): DayOfWeek =
        if (this == DayOfWeek.MONDAY) DayOfWeek.SUNDAY else DayOfWeek.of(value - 1)

    private fun dailyQuality(
        totalDuration: Duration,
        sessionCount: Int,
        stageCount: Int,
        napCount: Int
    ): SleepQualityBand {
        if (sessionCount == 0) return SleepQualityBand.UNKNOWN
        val hours = totalDuration.toMinutes() / 60.0
        val allSessionsAreNaps = napCount == sessionCount
        return when {
            allSessionsAreNaps && hours < NAP_MAX_HOURS -> SleepQualityBand.NAP
            hours >= GOOD_SLEEP_MIN_HOURS && sessionCount <= 2 -> SleepQualityBand.GOOD
            hours >= 5.0 -> if (sessionCount >= 3 || stageCount >= FRAGMENTED_STAGE_COUNT_THRESHOLD) {
                SleepQualityBand.FRAGMENTED
            } else {
                SleepQualityBand.FAIR
            }
            hours >= 3.0 -> SleepQualityBand.SHORT
            else -> SleepQualityBand.SHORT
        }
    }

    private fun rangeQuality(daySummaries: List<SleepDaySummary>): SleepQualityBand {
        val activeDays = daySummaries.filter { it.sessionCount > 0 }
        if (activeDays.isEmpty()) return SleepQualityBand.UNKNOWN
        if (activeDays.all { it.qualityBand == SleepQualityBand.NAP }) return SleepQualityBand.NAP
        val averageMinutes = activeDays.sumOf { it.totalDuration.toMinutes() } / activeDays.size
        val averageDuration = Duration.ofMinutes(averageMinutes)
        val fragmentedDays = activeDays.count { it.qualityBand == SleepQualityBand.FRAGMENTED }
        val napDays = activeDays.count { it.qualityBand == SleepQualityBand.NAP }
        val base = dailyQuality(
            totalDuration = averageDuration,
            sessionCount = 1,
            stageCount = 0,
            napCount = if (napDays > activeDays.size / 2) 1 else 0
        )
        return when {
            base == SleepQualityBand.SHORT -> SleepQualityBand.SHORT
            fragmentedDays > activeDays.size / 2 -> SleepQualityBand.FRAGMENTED
            else -> base
        }
    }

    private fun qualityRank(band: SleepQualityBand): Int =
        when (band) {
            SleepQualityBand.GOOD -> 0
            SleepQualityBand.FAIR -> 1
            SleepQualityBand.NAP -> 2
            SleepQualityBand.FRAGMENTED -> 3
            SleepQualityBand.SHORT -> 4
            SleepQualityBand.UNKNOWN -> 5
        }

    private fun canonicalTags(
        dateTag: SleepSessionTag,
        qualityTag: SleepSessionTag,
        flags: List<SleepSessionTag>
    ): List<SleepSessionTag> =
        (listOf(dateTag, qualityTag) + flags)
            .distinctBy { it.label }

    private fun dayLabel(date: LocalDate?, now: LocalDate): String {
        if (date == null) return "Unknown date"
        return when (date) {
            now -> "Today"
            now.minusDays(1) -> "Yesterday"
            else -> date.format(DateTimeFormatter.ofPattern("EEE M/d", Locale.US))
        }
    }
}
