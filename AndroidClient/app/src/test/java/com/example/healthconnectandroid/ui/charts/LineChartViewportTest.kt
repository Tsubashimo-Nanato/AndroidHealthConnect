package com.example.healthconnectandroid.ui.charts

import org.junit.Assert.assertTrue
import org.junit.Test

class LineChartViewportTest {
    @Test
    fun minViewportAllowsZoomInsideSmallPreferredRange() {
        val preferredDayInNinetyDays = 1f / 90f

        val minViewport = lineMinViewportFraction(
            totalPoints = 100,
            preferredViewportFraction = preferredDayInNinetyDays
        )

        assertTrue(minViewport < preferredDayInNinetyDays)
    }

    @Test
    fun minViewportWithoutPreferredRangeUsesSampleFloor() {
        val minViewport = lineMinViewportFraction(
            totalPoints = 100,
            preferredViewportFraction = null
        )

        assertTrue(minViewport >= 0.019f)
        assertTrue(minViewport <= 0.021f)
    }
}
