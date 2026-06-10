package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.HrDateSelectionMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateDetailTextTest {
    @Test
    fun weekWindowLabelUsesCompactDateRange() {
        withUsLocale {
            val label = heartRateWindowLabel(
                mode = HrDateSelectionMode.WEEK,
                anchorDate = LocalDate.of(2026, 5, 7),
                selectedDates = emptySet(),
                today = LocalDate.of(2026, 5, 10),
                weekStart = DayOfWeek.MONDAY
            )

            assertEquals("May 4-10", label)
        }
    }

    @Test
    fun monthWindowLabelUsesMonthAndYear() {
        withUsLocale {
            val label = heartRateWindowLabel(
                mode = HrDateSelectionMode.MONTH,
                anchorDate = LocalDate.of(2026, 5, 10),
                selectedDates = emptySet(),
                today = LocalDate.of(2026, 5, 10),
                weekStart = DayOfWeek.MONDAY
            )

            assertEquals("May 2026", label)
        }
    }

    @Test
    fun compactDateRangeHandlesMissingDates() {
        assertEquals("No dates", compactDateRange(null, LocalDate.of(2026, 5, 10)))
    }

    private fun withUsLocale(block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
