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
    FAIR("Fair", SleepTagTone.INFO),
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
    val qualityBand: SleepQualityBand
)

data class SleepSessionInput(
    val start: Instant?,
    val end: Instant?,
    val stageCount: Int
)

data class SleepDaySummary(
    val localDate: LocalDate,
    val sessionCount: Int,
    val totalDuration: Duration,
    val stageCount: Int,
    val qualityBand: SleepQualityBand
)

data class SleepRangeSummary(
    val averageSessionDuration: Duration?,
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
    private const val NAP_MIN_MINUTES = 10L
    private const val NAP_MAX_MINUTES = 60L
    private const val NAP_START_HOUR = 10
    private const val NAP_END_HOUR = 18
    private const val SHORT_SLEEP_MAX_HOURS = 6L
    private const val GOOD_SLEEP_MIN_HOURS = 7L
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

        val isNap = isDaytimeNap(start, duration, zoneId)
        val isShort = duration != null && !isNap && duration < Duration.ofHours(SHORT_SLEEP_MAX_HOURS)
        val isFragmented = duration != null &&
            duration >= Duration.ofHours(SHORT_SLEEP_MAX_HOURS) &&
            stageCount >= FRAGMENTED_STAGE_COUNT_THRESHOLD

        val quality = when {
            duration == null -> SleepQualityBand.UNKNOWN
            isNap -> SleepQualityBand.NAP
            isShort -> SleepQualityBand.SHORT
            isFragmented -> SleepQualityBand.FRAGMENTED
            duration >= Duration.ofHours(GOOD_SLEEP_MIN_HOURS) -> SleepQualityBand.GOOD
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
            qualityBand = quality
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
                averageSessionDuration = null,
                lastSessionDuration = null
            )
        }
        val validSessions = rangedSessions.mapNotNull { input ->
            durationOf(input)?.let { duration -> input to duration }
        }
        val averageDuration = validSessions
            .takeIf { it.isNotEmpty() }
            ?.let { values -> Duration.ofMinutes(values.sumOf { it.second.toMinutes() } / values.size) }
        val lastSessionDuration = validSessions.maxByOrNull { it.first.start!! }?.second
        return SleepRangeSummary(
            averageSessionDuration = averageDuration,
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
        val naturalMonthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
        val monthEnd = naturalMonthEnd.coerceAtMost(endDate)
        val firstCell = monthStart.with(TemporalAdjusters.previousOrSame(weekStart))
        val lastCell = if (monthEnd == naturalMonthEnd) {
            monthEnd.with(TemporalAdjusters.nextOrSame(weekStart.minusOneDay()))
        } else {
            monthEnd
        }
        val days = generateSequence(firstCell) { previous ->
            previous.plusDays(1).takeIf { !it.isAfter(lastCell) }
        }.toList()
        // Padding dates carry real sleep data so month-to-month scrolling never shows placeholder colors.
        val summaries = dailySummaries(sessions, firstCell, lastCell, zoneId)
            .associateBy { it.localDate }
        return SleepQualityMatrixModel(
            title = monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)),
            columns = 7,
            boxes = days.map { date ->
                val summary = summaries[date]
                SleepQualityMatrixBox(
                    id = date.toString(),
                    label = date.format(DateTimeFormatter.ofPattern("M/d EEE", Locale.US)),
                    startDate = date,
                    endDate = date,
                    qualityBand = summary?.qualityBand?.takeIf { summary.sessionCount > 0 },
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
            val stageCount = daySessions.sumOf { it.stageCount }
            SleepDaySummary(
                localDate = date,
                sessionCount = daySessions.size,
                totalDuration = totalDuration,
                stageCount = stageCount,
                qualityBand = dailyQuality(totalDuration, daySessions.size, stageCount)
            )
        }
    }

    private fun durationOf(input: SleepSessionInput): Duration? {
        val start = input.start ?: return null
        val end = input.end ?: return null
        return if (end.isAfter(start)) Duration.between(start, end) else null
    }

    private fun isDaytimeNap(
        start: Instant?,
        duration: Duration?,
        zoneId: ZoneId
    ): Boolean {
        // Nap is a short daytime session marker; daily sleep quality is calculated separately.
        if (start == null || duration == null) return false
        val minutes = duration.toMinutes()
        if (minutes < NAP_MIN_MINUTES || minutes >= NAP_MAX_MINUTES) return false
        return start.atZone(zoneId).hour in NAP_START_HOUR until NAP_END_HOUR
    }

    private fun DayOfWeek.minusOneDay(): DayOfWeek =
        if (this == DayOfWeek.MONDAY) DayOfWeek.SUNDAY else DayOfWeek.of(value - 1)

    private fun dailyQuality(
        totalDuration: Duration,
        sessionCount: Int,
        stageCount: Int
    ): SleepQualityBand {
        if (sessionCount == 0) return SleepQualityBand.UNKNOWN
        val hours = totalDuration.toMinutes() / 60.0
        val fragmented = sessionCount >= 3 || stageCount >= FRAGMENTED_STAGE_COUNT_THRESHOLD
        return when {
            hours < SHORT_SLEEP_MAX_HOURS -> SleepQualityBand.SHORT
            fragmented -> SleepQualityBand.FRAGMENTED
            hours >= GOOD_SLEEP_MIN_HOURS -> SleepQualityBand.GOOD
            else -> SleepQualityBand.FAIR
        }
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
