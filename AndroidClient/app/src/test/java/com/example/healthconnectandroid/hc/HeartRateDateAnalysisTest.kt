package com.example.healthconnectandroid.hc

import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateDateAnalysisTest {
    @Test
    fun tapDateRangeMapsToTwentyFourHourWindow() {
        val date = LocalDate.of(2026, 5, 10)

        val range = HeartRateDateAnalysis.visibleRangeForDates(setOf(date), ZoneOffset.UTC)

        assertEquals(date.atStartOfDay(ZoneOffset.UTC).toInstant(), range?.start)
        assertEquals(date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(), range?.end)
    }

    @Test
    fun holdDragSelectionClampsToSevenDays() {
        val start = LocalDate.of(2026, 5, 1)
        val end = LocalDate.of(2026, 5, 20)

        val dates = HeartRateDateAnalysis.clampSelection(start, end)

        assertEquals(7, dates.size)
        assertTrue(dates.contains(start))
        assertTrue(dates.contains(start.plusDays(6)))
    }

    @Test
    fun reverseHoldDragSelectionClampsAroundGestureStart() {
        val start = LocalDate.of(2026, 5, 20)
        val end = LocalDate.of(2026, 5, 1)

        val dates = HeartRateDateAnalysis.clampSelection(start, end)

        assertEquals(7, dates.size)
        assertTrue(dates.contains(start))
        assertTrue(dates.contains(start.minusDays(6)))
    }

    @Test
    fun modesAreWeekAndMonthOnlyWithWeekFirst() {
        assertEquals(listOf(HrDateSelectionMode.WEEK, HrDateSelectionMode.MONTH), HrDateSelectionMode.entries.toList())
    }

    @Test
    fun latestDataDateIgnoresFutureDates() {
        val today = LocalDate.of(2026, 5, 10)
        val dates = listOf(
            LocalDate.of(2026, 5, 5),
            LocalDate.of(2026, 5, 9),
            LocalDate.of(2026, 5, 12)
        )

        val selected = HeartRateDateAnalysis.latestDataDate(dates, today)

        assertEquals(LocalDate.of(2026, 5, 9), selected)
    }

    @Test
    fun defaultSelectedDateFallsBackToTodayWithoutData() {
        val today = LocalDate.of(2026, 5, 10)

        val selected = HeartRateDateAnalysis.defaultSelectedDate(emptyList(), today)

        assertEquals(today, selected)
    }

    @Test
    fun weekModeUsesWeekContainingAnchor() {
        val today = LocalDate.of(2026, 5, 10)
        val futureAnchor = LocalDate.of(2026, 5, 14)

        val dates = HeartRateDateAnalysis.visibleDates(futureAnchor, today)

        assertEquals(LocalDate.of(2026, 5, 10), dates.first())
        assertEquals(LocalDate.of(2026, 5, 16), dates.last())
    }

    @Test
    fun weekModeRespectsConfiguredWeekStart() {
        val today = LocalDate.of(2026, 5, 10)
        val anchor = LocalDate.of(2026, 5, 7)

        val sundayWeek = HeartRateDateAnalysis.visibleDates(
            anchorDate = anchor,
            today = today,
            mode = HrDateSelectionMode.WEEK,
            weekStart = DayOfWeek.SUNDAY
        )
        val mondayWeek = HeartRateDateAnalysis.visibleDates(
            anchorDate = anchor,
            today = today,
            mode = HrDateSelectionMode.WEEK,
            weekStart = DayOfWeek.MONDAY
        )

        assertEquals(LocalDate.of(2026, 5, 3), sundayWeek.first())
        assertEquals(LocalDate.of(2026, 5, 9), sundayWeek.last())
        assertEquals(LocalDate.of(2026, 5, 4), mondayWeek.first())
        assertEquals(LocalDate.of(2026, 5, 10), mondayWeek.last())
    }

    @Test
    fun monthModeIncludesAllDaysInMonthGrid() {
        val dates = HeartRateDateAnalysis.monthMatrixDates(
            month = YearMonth.of(2026, 5),
            weekStart = DayOfWeek.SUNDAY
        )

        assertTrue(dates.contains(LocalDate.of(2026, 5, 1)))
        assertTrue(dates.contains(LocalDate.of(2026, 5, 31)))
        assertEquals(DayOfWeek.SUNDAY, dates.first().dayOfWeek)
        assertEquals(0, dates.size % 7)
    }

    @Test
    fun monthModeVisibleDatesUsesFullMonthGrid() {
        val dates = HeartRateDateAnalysis.visibleDates(
            anchorDate = LocalDate.of(2026, 5, 10),
            today = LocalDate.of(2026, 5, 10),
            mode = HrDateSelectionMode.MONTH,
            weekStart = DayOfWeek.SUNDAY
        )

        assertTrue(dates.contains(LocalDate.of(2026, 5, 1)))
        assertTrue(dates.contains(LocalDate.of(2026, 5, 31)))
    }

    @Test
    fun dateQualityClassifiesNormalElevatedAndHigh() {
        val zones = HeartRateAnalysis.referenceZones(age = 40)

        assertEquals(
            HrDateQuality.NORMAL,
            HeartRateDateAnalysis.qualityFor(listOf(62.0, 70.0, 75.0), zones).quality
        )
        assertEquals(
            HrDateQuality.ELEVATED,
            HeartRateDateAnalysis.qualityFor(listOf(95.0, 105.0, 115.0, 120.0), zones).quality
        )
        assertEquals(
            HrDateQuality.HIGH,
            HeartRateDateAnalysis.qualityFor(listOf(90.0, 150.0, 158.0, 164.0), zones).quality
        )
    }

    @Test
    fun dateQualitySummaryClassifiesWithoutAllSamples() {
        val zones = HeartRateAnalysis.referenceZones(age = 40)

        assertEquals(
            HrDateQuality.NO_DATA,
            HeartRateDateAnalysis.qualityForSummary(0, null, null, zones).quality
        )
        assertEquals(
            HrDateQuality.NORMAL,
            HeartRateDateAnalysis.qualityForSummary(240, 72.0, 88.0, zones).quality
        )
        assertEquals(
            HrDateQuality.ELEVATED,
            HeartRateDateAnalysis.qualityForSummary(240, 112.0, 128.0, zones).quality
        )
        assertEquals(
            HrDateQuality.NORMAL,
            HeartRateDateAnalysis.qualityForSummary(240, 72.0, 152.0, zones).quality
        )
        assertEquals(
            HrDateQuality.HIGH,
            HeartRateDateAnalysis.qualityForSummary(240, 145.0, 168.0, zones).quality
        )
    }

    @Test
    fun dateQualitySummaryUsesContinuousZoneScore() {
        val zones = HeartRateAnalysis.referenceZones(age = 40)

        val reference = HeartRateDateAnalysis.qualityForSummary(240, 72.0, 88.0, zones)
        val elevated = HeartRateDateAnalysis.qualityForSummary(240, 112.0, 128.0, zones)
        val high = HeartRateDateAnalysis.qualityForSummary(240, 145.0, 168.0, zones)

        assertTrue(reference.zoneScore < elevated.zoneScore)
        assertTrue(elevated.zoneScore < high.zoneScore)
        assertTrue(reference.zoneScore < 0.28f)
        assertTrue(elevated.zoneScore in 0.28f..0.68f)
    }
}
