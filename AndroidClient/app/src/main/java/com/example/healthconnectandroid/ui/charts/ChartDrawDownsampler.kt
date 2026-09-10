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

        val interiorSize = sorted.size - 2
        val bucketCount = ((targetCount - 2) / 2).coerceAtLeast(1)
        val bucketSize = interiorSize.toDouble() / bucketCount.toDouble()
        val result = ArrayList<InspectorChartPoint>(targetCount)

        result.add(sorted.first())
        for (bucketIndex in 0 until bucketCount) {
            val startOffset = floor(bucketIndex * bucketSize).toInt().coerceIn(0, interiorSize)
            if (startOffset >= interiorSize) continue
            val endOffset = if (bucketIndex == bucketCount - 1) {
                interiorSize
            } else {
                floor((bucketIndex + 1) * bucketSize).toInt()
                    .coerceIn(startOffset + 1, interiorSize)
            }
            appendBucketExtrema(
                points = sorted,
                startIndex = startOffset + 1,
                endIndexExclusive = endOffset + 1,
                destination = result
            )
        }
        result.addIfNew(sorted.last())

        return if (result.size <= targetCount) {
            result
        } else {
            result.take(targetCount - 1) + sorted.last()
        }
    }

    private fun appendBucketExtrema(
        points: List<InspectorChartPoint>,
        startIndex: Int,
        endIndexExclusive: Int,
        destination: MutableList<InspectorChartPoint>
    ) {
        var lowIndex = startIndex
        var highIndex = startIndex
        var lowValue = points[startIndex].lowValueForDownsampling()
        var highValue = points[startIndex].highValueForDownsampling()
        for (index in startIndex + 1 until endIndexExclusive) {
            val point = points[index]
            val candidateLow = point.lowValueForDownsampling()
            if (candidateLow < lowValue) {
                lowValue = candidateLow
                lowIndex = index
            }
            val candidateHigh = point.highValueForDownsampling()
            if (candidateHigh > highValue) {
                highValue = candidateHigh
                highIndex = index
            }
        }

        val lowPoint = points[lowIndex]
        val highPoint = points[highIndex]
        if (lowPoint.epochMillis <= highPoint.epochMillis) {
            destination.addIfNew(lowPoint)
            destination.addIfNew(highPoint)
        } else {
            destination.addIfNew(highPoint)
            destination.addIfNew(lowPoint)
        }
    }

    private fun List<InspectorChartPoint>.isSortedByEpoch(): Boolean {
        for (index in 1 until size) {
            if (this[index - 1].epochMillis > this[index].epochMillis) return false
        }
        return true
    }

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
}
