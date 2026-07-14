package com.example.healthconnectandroid.ui.sleep

import com.example.healthconnectandroid.hc.SleepDaySummary
import com.example.healthconnectandroid.hc.SleepQualityBand
import com.example.healthconnectandroid.hc.SleepQualityMatrixBox
import com.example.healthconnectandroid.hc.SleepQualityMatrixModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SleepDetailSectionTest {
    @Test
    fun timelineDateLabelShowsDateAndWeekday() {
        assertEquals("7/12\nSun", sleepTimelineDateLabel(LocalDate.of(2026, 7, 12)))
    }

    @Test
    fun timelineWidthFitsFiveColumnsAfterAxisAndSpacing() {
        assertEquals(52.8f, sleepTimelineColumnWidth(324.dp).value, 0.001f)
    }

    @Test
    fun durationColorsProgressFromMutedRedToGreenAtSevenHours() {
        assertEquals(Color(0xFFC9857E), sleepDurationColor(3 * 60L))
        assertEquals(Color(0xFFD3A071), sleepDurationColor(5 * 60L))
        assertEquals(Color(0xFFC5B66F), sleepDurationColor(6 * 60L))
        assertEquals(Color(0xFF7FAA82), sleepDurationColor(7 * 60L))
    }

    @Test
    fun chartRowsUseDailyDurationInHoursAndSkipMissingDays() {
        val rows = sleepDurationChartRows(
            listOf(
                daySummary("2026-05-09", sessions = 0, minutes = 0),
                daySummary("2026-05-10", sessions = 1, minutes = 90)
            )
        )

        assertEquals(1, rows.size)
        assertEquals("2026-05-10", rows.single().localDate)
        assertEquals(1.5, rows.single().total, 0.0)
        assertEquals("h", rows.single().unit)
    }

    @Test
    fun selectedDateLabelDoesNotExposeSessionCounts() {
        val date = LocalDate.of(2026, 5, 10)
        val model = SleepQualityMatrixModel(
            title = "Week",
            columns = 7,
            boxes = listOf(
                SleepQualityMatrixBox(
                    id = date.toString(),
                    label = "Sun",
                    startDate = date,
                    endDate = date,
                    qualityBand = SleepQualityBand.SHORT,
                    sessionCount = 3
                )
            )
        )

        val label = selectedSleepLabel(model, setOf(date.toString())).orEmpty()

        assertEquals("Selected: Sun", label)
        assertFalse(label.contains("3"))
        assertFalse(label.contains("session", ignoreCase = true))
    }

    @Test
    fun weeklyTimelineKeepsAllSevenDatesWithoutSessions() {
        val start = LocalDate.of(2026, 7, 6)
        val model = SleepQualityMatrixModel(
            title = "Week",
            columns = 7,
            boxes = List(7) { offset ->
                val date = start.plusDays(offset.toLong())
                SleepQualityMatrixBox(
                    id = date.toString(),
                    label = date.dayOfWeek.name.take(3),
                    startDate = date,
                    endDate = date,
                    qualityBand = null,
                    sessionCount = 0
                )
            }
        )

        val days = sleepTimelineDays(model, emptyList(), ZoneOffset.UTC)

        assertEquals(7, days.size)
        assertEquals(start, days.first().date)
        assertEquals(start.plusDays(6), days.last().date)
        assertEquals(0, days.sumOf { it.periods.size })
    }

    @Test
    fun sleepClockPeriodsWrapCrossMidnightSessions() {
        val periods = sleepClockPeriods(
            start = Instant.parse("2026-07-11T23:30:00Z"),
            end = Instant.parse("2026-07-12T01:15:00Z"),
            stageLabel = "Sleeping",
            zoneId = ZoneOffset.UTC
        )

        assertEquals(2, periods.size)
        assertEquals(1410f, periods[0].startMinute)
        assertEquals(1440f, periods[0].endMinute)
        assertEquals(0f, periods[1].startMinute)
        assertEquals(75f, periods[1].endMinute)
    }

    @Test
    fun monthlyTimelineKeepsEveryPaddingColumn() {
        val start = LocalDate.of(2026, 4, 26)
        val model = SleepQualityMatrixModel(
            title = "May 2026",
            columns = 7,
            boxes = List(42) { offset ->
                val date = start.plusDays(offset.toLong())
                SleepQualityMatrixBox(
                    id = date.toString(),
                    label = date.dayOfMonth.toString(),
                    startDate = date,
                    endDate = date,
                    qualityBand = null,
                    sessionCount = 0
                )
            }
        )

        val days = sleepTimelineDays(model, emptyList(), ZoneOffset.UTC)

        assertEquals(42, days.size)
        assertEquals(LocalDate.of(2026, 4, 26), days.first().date)
        assertEquals(LocalDate.of(2026, 6, 6), days.last().date)
    }

    private fun daySummary(date: String, sessions: Int, minutes: Long): SleepDaySummary =
        SleepDaySummary(
            localDate = LocalDate.parse(date),
            sessionCount = sessions,
            totalDuration = Duration.ofMinutes(minutes),
            stageCount = sessions,
            qualityBand = if (sessions == 0) SleepQualityBand.UNKNOWN else SleepQualityBand.SHORT
        )
}
