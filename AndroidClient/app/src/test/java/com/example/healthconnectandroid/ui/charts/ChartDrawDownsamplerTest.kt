package com.example.healthconnectandroid.ui.charts

import com.example.healthconnectandroid.hc.InspectorChartPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartDrawDownsamplerTest {
    @Test
    fun sparseInputReturnsExactList() {
        val points = (0 until 10).map { index -> point(index, index.toDouble()) }

        val result = ChartDrawDownsampler.downsampleMinMax(points, maxDrawPoints = 20)

        assertSame(points, result)
    }

    @Test
    fun denseInputReducesDrawCount() {
        val points = (0 until 1_000).map { index -> point(index, index.toDouble()) }

        val result = ChartDrawDownsampler.downsampleMinMax(points, maxDrawPoints = 120)

        assertTrue(result.size <= 120)
        assertTrue(result.size < points.size)
        assertEquals(points.first(), result.first())
        assertEquals(points.last(), result.last())
    }

    @Test
    fun denseInputPreservesMinAndMaxSpikes() {
        val points = (0 until 1_000).map { index ->
            when (index) {
                400 -> point(index, 220.0)
                610 -> point(index, 35.0)
                else -> point(index, 80.0)
            }
        }

        val result = ChartDrawDownsampler.downsampleMinMax(points, maxDrawPoints = 120)

        assertTrue(result.any { it.epochMillis == 400_000L && it.value == 220.0 })
        assertTrue(result.any { it.epochMillis == 610_000L && it.value == 35.0 })
    }

    @Test
    fun denseInputPreservesSecondarySeriesExtrema() {
        val points = (0 until 1_000).map { index ->
            when (index) {
                420 -> point(index, value = 80.0, value2 = 240.0)
                570 -> point(index, value = 80.0, value2 = 25.0)
                else -> point(index, value = 80.0, value2 = 90.0)
            }
        }

        val result = ChartDrawDownsampler.downsampleMinMax(points, maxDrawPoints = 120)

        assertTrue(result.any { it.epochMillis == 420_000L && it.value2 == 240.0 })
        assertTrue(result.any { it.epochMillis == 570_000L && it.value2 == 25.0 })
    }

    @Test
    fun denseUnsortedInputProducesChronologicalOutput() {
        val points = (0 until 1_000)
            .map { index -> point(index, index.toDouble()) }
            .reversed()

        val result = ChartDrawDownsampler.downsampleMinMax(points, maxDrawPoints = 120)

        assertTrue(result.zipWithNext().all { (previous, next) ->
            previous.epochMillis <= next.epochMillis
        })
        assertEquals(0L, result.first().epochMillis)
        assertEquals(999_000L, result.last().epochMillis)
    }

    private fun point(index: Int, value: Double, value2: Double? = null): InspectorChartPoint =
        InspectorChartPoint(
            epochMillis = index * 1_000L,
            value = value,
            value2 = value2,
            label = null,
            unit = "bpm"
        )
}
