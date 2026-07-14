package com.example.healthconnectandroid.ui.sleep

import com.example.healthconnectandroid.hc.InspectorTimeRange
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepWindowLabelTest {
    @Test
    fun weeklyLabelUsesRollingSevenDaysEndingAtAnchor() {
        assertEquals(
            "May 4-10",
            sleepWindowLabel(InspectorTimeRange.WEEKLY, LocalDate.of(2026, 5, 10))
        )
    }

    @Test
    fun monthlyLabelShowsMonthAndYear() {
        assertEquals(
            "May 2026",
            sleepWindowLabel(InspectorTimeRange.MONTHLY, LocalDate.of(2026, 5, 10))
        )
    }

    @Test
    fun weeklyLabelUsesAnchorEvenInMiddleOfCalendarWeek() {
        assertEquals(
            "May 1-7",
            sleepWindowLabel(
                range = InspectorTimeRange.WEEKLY,
                anchorDate = LocalDate.of(2026, 5, 7)
            )
        )
    }

    @Test
    fun weeklyMatrixKeyUsesRollingWindowStart() {
        assertEquals(
            "week:2026-05-05",
            sleepMatrixKey(
                range = InspectorTimeRange.WEEKLY,
                anchorDate = LocalDate.of(2026, 5, 11)
            )
        )
    }

    @Test
    fun weeklyWindowMovesBySevenDaysWithoutCalendarWeekSnapping() {
        val shifted = sleepShiftWindowDate(
            range = InspectorTimeRange.WEEKLY,
            anchorDate = LocalDate.of(2026, 5, 10),
            cells = -1,
            zoneId = java.time.ZoneOffset.UTC
        )

        assertEquals(LocalDate.of(2026, 5, 3), shifted)
        assertEquals(LocalDate.of(2026, 4, 27), sleepVisibleStartDate(InspectorTimeRange.WEEKLY, shifted))
        assertEquals(LocalDate.of(2026, 5, 3), sleepVisibleEndDate(InspectorTimeRange.WEEKLY, shifted))
    }
}
