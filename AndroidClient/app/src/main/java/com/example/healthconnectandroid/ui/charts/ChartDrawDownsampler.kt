package com.example.healthconnectandroid.ui.charts

import com.example.healthconnectandroid.hc.InspectorChartPoint
import kotlin.math.floor

object ChartDrawDownsampler {
    const val DEFAULT_MAX_DRAW_POINTS = 360

    fun downsampleMinMax(
        points: List<InspectorChartPoint>,
        maxDrawPoints: Int = DEFAULT_MAX_DRAW_POINTS
    ): List<InspectorChartPoint> {
        val targetCount = maxDrawPoints.coerceAtLeast(3)
        if (points.size <= targetCount) return points

        val sorted = if (points.isSortedByEpoch()) points else points.sortedBy { it.epochMillis }
        if (sorted.size <= 2) return sorted

        val interior = sorted.subList(1, sorted.lastIndex)
        val bucketCount = ((targetCount - 2) / 2).coerceAtLeast(1)
        val bucketSize = interior.size.toDouble() / bucketCount.toDouble()
        val result = ArrayList<InspectorChartPoint>(targetCount)

        result.add(sorted.first())
        for (bucketIndex in 0 until bucketCount) {
            val start = floor(bucketIndex * bucketSize).toInt().coerceIn(0, interior.size)
            if (start >= interior.size) continue
            val end = if (bucketIndex == bucketCount - 1) {
                interior.size
            } else {
                floor((bucketIndex + 1) * bucketSize).toInt().coerceIn(start + 1, interior.size)
            }
            if (start >= end) continue

            val bucket = interior.subList(start, end)
            val lowPoint = bucket.minByOrNull { it.lowValueForDownsampling() } ?: continue
            val highPoint = bucket.maxByOrNull { it.highValueForDownsampling() } ?: lowPoint
            listOf(lowPoint, highPoint)
                .distinctBy { DownsampleIdentity(it.epochMillis, it.value, it.value2) }
                .sortedBy { it.epochMillis }
                .forEach { result.addIfNew(it) }
        }
        result.addIfNew(sorted.last())

        return if (result.size <= targetCount) {
            result
        } else {
            result.take(targetCount - 1) + sorted.last()
        }
    }

    private fun List<InspectorChartPoint>.isSortedByEpoch(): Boolean =
        asSequence()
            .zipWithNext()
            .all { (previous, next) -> previous.epochMillis <= next.epochMillis }

    private fun InspectorChartPoint.lowValueForDownsampling(): Double =
        minOf(value, value2 ?: value)

    private fun InspectorChartPoint.highValueForDownsampling(): Double =
        maxOf(value, value2 ?: value)

    private fun MutableList<InspectorChartPoint>.addIfNew(point: InspectorChartPoint) {
        val last = lastOrNull()
        if (last == null ||
            last.epochMillis != point.epochMillis ||
            last.value != point.value ||
            last.value2 != point.value2
        ) {
            add(point)
        }
    }

    private data class DownsampleIdentity(
        val epochMillis: Long,
        val value: Double,
        val value2: Double?
    )
}
