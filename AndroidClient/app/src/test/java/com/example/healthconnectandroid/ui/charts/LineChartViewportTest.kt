package com.example.healthconnectandroid.ui.charts

import com.example.healthconnectandroid.hc.InspectorChartPoint
import org.junit.Assert.assertEquals
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

    @Test
    fun visibleWindowUsesInclusiveBounds() {
        val points = listOf(
            point(0),
            point(1_000),
            point(2_000),
            point(3_000)
        )

        val window = visibleChartPointWindow(points, NumericBounds(1_000.0, 2_000.0))

        assertEquals(listOf(1_000L, 2_000L), window.map { it.epochMillis })
    }

    @Test
    fun visibleWindowKeepsDuplicateEpochsInsideBounds() {
        val points = listOf(
            point(0),
            point(1_000, value = 60.0),
            point(1_000, value = 72.0),
            point(2_000)
        )

        val window = visibleChartPointWindow(points, NumericBounds(1_000.0, 1_000.0))

        assertEquals(listOf(60.0, 72.0), window.map { it.value })
    }

    @Test
    fun visibleWindowReturnsEmptyWhenRangeMissesData() {
        val points = listOf(point(0), point(1_000), point(2_000))

        val window = visibleChartPointWindow(points, NumericBounds(3_000.0, 4_000.0))

        assertTrue(window.isEmpty())
    }

    private fun point(epochMillis: Long, value: Double = epochMillis.toDouble()): InspectorChartPoint =
        InspectorChartPoint(
            epochMillis = epochMillis,
            value = value,
            value2 = null,
            label = null,
            unit = "bpm"
        )
}
