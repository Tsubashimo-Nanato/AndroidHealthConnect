package com.example.healthconnectandroid.hc

import java.time.Duration
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepSessionAnalyzerTest {
    @Test
    fun partialCurrentMonthStopsAtProvidedEndDateWithoutFuturePadding() {
        val model = SleepSessionAnalyzer.monthlyMatrix(
            sessions = emptyList(),
            startDate = LocalDate.of(2026, 7, 1),
            endDate = LocalDate.of(2026, 7, 12),
            zoneId = ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2026, 6, 28), model.boxes.first().startDate)
        assertEquals(LocalDate.of(2026, 7, 12), model.boxes.last().startDate)
        assertEquals("7/12 Sun", model.boxes.last().label)
    }

    private val zoneId: ZoneId = ZoneId.of("UTC")
    private val today: LocalDate = LocalDate.of(2026, 5, 10)

    @Test
    fun addsTodayAndYesterdayTags() {
        val todayAnalysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-10T01:00:00Z"),
            end = Instant.parse("2026-05-10T08:00:00Z"),
            stageCount = 3,
            now = today,
            zoneId = zoneId
        )
        val yesterdayAnalysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-09T22:00:00Z"),
            end = Instant.parse("2026-05-10T06:00:00Z"),
            stageCount = 3,
            now = today,
            zoneId = zoneId
        )

        assertTrue(todayAnalysis.tags.any { it.label == "Today" })
        assertTrue(yesterdayAnalysis.tags.any { it.label == "Yesterday" })
    }

    @Test
    fun oneHourSleepIsShortAndNotANap() {
        val analysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-10T09:33:00Z"),
            end = Instant.parse("2026-05-10T10:44:00Z"),
            stageCount = 3,
            now = today,
            zoneId = zoneId
        )

        assertEquals(SleepQualityBand.SHORT, analysis.qualityBand)
        assertTrue(analysis.tags.none { it.label == "Nap" })
        assertEquals(1, analysis.tags.count { it.label == "Short" })
    }

    @Test
    fun shortAfternoonNapUsesNapSessionBand() {
        val analysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-10T13:00:00Z"),
            end = Instant.parse("2026-05-10T13:30:00Z"),
            stageCount = 1,
            now = today,
            zoneId = zoneId
        )

        assertEquals(SleepQualityBand.NAP, analysis.qualityBand)
        assertEquals(SleepTagRole.DATE, analysis.tags[0].role)
        assertEquals(SleepTagRole.QUALITY, analysis.tags[1].role)
        assertEquals("Nap", analysis.tags[1].label)
        assertEquals(1, analysis.tags.count { it.label == "Nap" })
    }

    @Test
    fun marksFourToSixHourMainSleepAsShort() {
        val analysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-10T01:00:00Z"),
            end = Instant.parse("2026-05-10T06:30:00Z"),
            stageCount = 2,
            now = today,
            zoneId = zoneId
        )

        assertEquals(SleepQualityBand.SHORT, analysis.qualityBand)
        assertTrue(analysis.tags.any { it.label == "Short" && it.tone == SleepTagTone.ERROR })
    }

    @Test
    fun normalLongSleepWithManyStageRowsIsNotAutomaticallyFragmented() {
        val analysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-10T00:00:00Z"),
            end = Instant.parse("2026-05-10T06:45:00Z"),
            stageCount = 61,
            now = today,
            zoneId = zoneId
        )

        assertEquals(SleepQualityBand.FAIR, analysis.qualityBand)
    }

    @Test
    fun extremeStageChurnCanStillBeFragmented() {
        val analysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-10T00:00:00Z"),
            end = Instant.parse("2026-05-10T06:45:00Z"),
            stageCount = 125,
            now = today,
            zoneId = zoneId
        )

        assertEquals(SleepQualityBand.FRAGMENTED, analysis.qualityBand)
    }

    @Test
    fun missingDurationFallsBackToUnknown() {
        val analysis = SleepSessionAnalyzer.analyze(
            start = null,
            end = null,
            stageCount = 0,
            now = today,
            zoneId = zoneId
        )

        assertEquals(SleepQualityBand.UNKNOWN, analysis.qualityBand)
        assertTrue(analysis.tags.any { it.label == "Unknown date" })
    }

    @Test
    fun canonicalTagOrderStartsWithDateThenQuality() {
        val analysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-10T01:00:00Z"),
            end = Instant.parse("2026-05-10T08:30:00Z"),
            stageCount = 5,
            now = today,
            zoneId = zoneId
        )

        assertEquals(SleepTagRole.DATE, analysis.tags[0].role)
        assertEquals(SleepTagRole.QUALITY, analysis.tags[1].role)
    }

    @Test
    fun doesNotEmitDuplicateSemanticLabels() {
        val analysis = SleepSessionAnalyzer.analyze(
            start = Instant.parse("2026-05-10T13:00:00Z"),
            end = Instant.parse("2026-05-10T14:00:00Z"),
            stageCount = 2,
            now = today,
            zoneId = zoneId
        )

        assertEquals(analysis.tags.map { it.label }.distinct(), analysis.tags.map { it.label })
    }

    @Test
    fun threeHourDailyTotalUsesShortQuality() {
        val summary = SleepSessionAnalyzer.dailySummaries(
            sessions = listOf(
                SleepSessionInput(
                    start = Instant.parse("2026-05-10T01:00:00Z"),
                    end = Instant.parse("2026-05-10T04:00:00Z"),
                    stageCount = 2
                )
            ),
            startDate = today,
            endDate = today,
            zoneId = zoneId
        ).single()

        assertEquals(SleepQualityBand.SHORT, summary.qualityBand)
    }

    @Test
    fun sevenHourDailyTotalIsGood() {
        val summary = SleepSessionAnalyzer.dailySummaries(
            sessions = listOf(
                SleepSessionInput(
                    start = Instant.parse("2026-05-10T00:00:00Z"),
                    end = Instant.parse("2026-05-10T07:30:00Z"),
                    stageCount = 5
                )
            ),
            startDate = today,
            endDate = today,
            zoneId = zoneId
        ).single()

        assertEquals(SleepQualityBand.GOOD, summary.qualityBand)
    }

    @Test
    fun extremeStageChurnDoesNotProduceAGreenDay() {
        val summary = SleepSessionAnalyzer.dailySummaries(
            sessions = listOf(
                SleepSessionInput(
                    start = Instant.parse("2026-05-10T00:00:00Z"),
                    end = Instant.parse("2026-05-10T07:30:00Z"),
                    stageCount = 125
                )
            ),
            startDate = today,
            endDate = today,
            zoneId = zoneId
        ).single()

        assertEquals(SleepQualityBand.FRAGMENTED, summary.qualityBand)
    }

    @Test
    fun multipleShortSessionsAggregateByDay() {
        val summary = SleepSessionAnalyzer.dailySummaries(
            sessions = listOf(
                SleepSessionInput(
                    start = Instant.parse("2026-05-10T01:00:00Z"),
                    end = Instant.parse("2026-05-10T03:00:00Z"),
                    stageCount = 1
                ),
                SleepSessionInput(
                    start = Instant.parse("2026-05-10T14:00:00Z"),
                    end = Instant.parse("2026-05-10T16:00:00Z"),
                    stageCount = 1
                )
            ),
            startDate = today,
            endDate = today,
            zoneId = zoneId
        ).single()

        assertEquals(SleepQualityBand.SHORT, summary.qualityBand)
    }

    @Test
    fun averagesSessionsInsteadOfDailyTotalsAndKeepsLatestDuration() {
        val summary = SleepSessionAnalyzer.summarizeRange(
            sessions = listOf(
                SleepSessionInput(
                    start = Instant.parse("2026-05-10T01:00:00Z"),
                    end = Instant.parse("2026-05-10T03:00:00Z"),
                    stageCount = 1
                ),
                SleepSessionInput(
                    start = Instant.parse("2026-05-10T14:00:00Z"),
                    end = Instant.parse("2026-05-10T18:00:00Z"),
                    stageCount = 1
                )
            ),
            startDate = today,
            endDate = today,
            zoneId = zoneId
        )

        assertEquals(Duration.ofHours(3), summary.averageSessionDuration)
        assertEquals(Duration.ofHours(4), summary.lastSessionDuration)
    }

    @Test
    fun monthlyMatrixContainsEveryDayInMonth() {
        val model = SleepSessionAnalyzer.monthlyMatrix(
            sessions = emptyList(),
            startDate = LocalDate.of(2026, 5, 1),
            endDate = LocalDate.of(2026, 5, 31),
            zoneId = zoneId,
            weekStart = DayOfWeek.SUNDAY
        )

        assertEquals("May 2026", model.title)
        assertEquals(7, model.columns)
        assertEquals(42, model.boxes.size)
        assertEquals(LocalDate.of(2026, 4, 26), model.boxes.first().startDate)
        assertEquals(LocalDate.of(2026, 6, 6), model.boxes.last().startDate)
        assertTrue(model.boxes.any { it.startDate == LocalDate.of(2026, 5, 1) })
        assertTrue(model.boxes.any { it.startDate == LocalDate.of(2026, 5, 31) })
    }

    @Test
    fun monthlyMatrixFirstRowRespectsMondayWeekStart() {
        val model = SleepSessionAnalyzer.monthlyMatrix(
            sessions = emptyList(),
            startDate = LocalDate.of(2026, 5, 1),
            endDate = LocalDate.of(2026, 5, 31),
            zoneId = zoneId,
            weekStart = DayOfWeek.MONDAY
        )

        assertEquals(LocalDate.of(2026, 4, 27), model.boxes.first().startDate)
        assertEquals(LocalDate.of(2026, 5, 31), model.boxes.last().startDate)
    }

    @Test
    fun monthlyMatrixKeepsSleepQualityForPreviousMonthPaddingDays() {
        val model = SleepSessionAnalyzer.monthlyMatrix(
            sessions = listOf(
                SleepSessionInput(
                    start = Instant.parse("2026-04-30T14:00:00Z"),
                    end = Instant.parse("2026-04-30T21:00:00Z"),
                    stageCount = 4
                )
            ),
            startDate = LocalDate.of(2026, 5, 1),
            endDate = LocalDate.of(2026, 5, 31),
            zoneId = zoneId,
            weekStart = DayOfWeek.SUNDAY
        )

        val paddingDay = model.boxes.single { it.startDate == LocalDate.of(2026, 4, 30) }
        assertEquals(SleepQualityBand.GOOD, paddingDay.qualityBand)
        assertEquals(1, paddingDay.sessionCount)
    }
}
