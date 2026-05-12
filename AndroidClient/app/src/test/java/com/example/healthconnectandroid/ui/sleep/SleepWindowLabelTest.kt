package com.example.healthconnectandroid.ui.sleep

import com.example.healthconnectandroid.hc.InspectorTimeRange
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepWindowLabelTest {
    @Test
    fun weeklyLabelUsesDefaultSundayStart() {
        assertEquals(
            "May 10-16",
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
    fun weeklyLabelRespectsMondayWeekStart() {
        assertEquals(
            "May 4-10",
            sleepWindowLabel(
                range = InspectorTimeRange.WEEKLY,
                anchorDate = LocalDate.of(2026, 5, 7),
                weekStart = DayOfWeek.MONDAY
            )
        )
    }

    @Test
    fun weeklyMatrixKeyUsesVisibleWeekStart() {
        assertEquals(
            "week:2026-05-10",
            sleepMatrixKey(
                range = InspectorTimeRange.WEEKLY,
                anchorDate = LocalDate.of(2026, 5, 11),
                weekStart = DayOfWeek.SUNDAY
            )
        )
    }
}
