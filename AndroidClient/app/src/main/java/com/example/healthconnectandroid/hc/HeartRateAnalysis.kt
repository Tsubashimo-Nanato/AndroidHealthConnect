package com.example.healthconnectandroid.hc

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

enum class HeartRateZoneTone {
    REFERENCE,
    ELEVATED,
    HIGH
}

data class HeartRateReferenceBand(
    val label: String,
    val lowerBpm: Double,
    val upperBpm: Double,
    val tone: HeartRateZoneTone
)

data class HeartRateReferenceZones(
    val age: Int?,
    val estimatedMaxBpm: Int?,
    val bands: List<HeartRateReferenceBand>
)

data class RestingHeartRateEstimate(
    val estimatedBpm: Double?,
    val recordedBpm: Double?,
    val sampleCount: Int,
    val rangeLabel: String,
    val method: String
) {
    val displayText: String
        get() = when {
            recordedBpm != null -> "Recorded ${formatBpm(recordedBpm)} bpm"
            estimatedBpm != null -> "Estimated ${formatBpm(estimatedBpm)} bpm"
            else -> "Not enough data"
        }
}

object HeartRateAnalysis {
    private const val GENERIC_MAX_BPM = 190

    fun referenceZones(age: Int?): HeartRateReferenceZones {
        val normalizedAge = age?.takeIf { it in 1..120 }
        val estimatedMax = normalizedAge?.let { (220 - it).coerceIn(100, 205) }
        val maxBpm = estimatedMax ?: GENERIC_MAX_BPM
        val referenceUpper = (maxBpm * 0.50).coerceIn(85.0, 105.0)
        val elevatedUpper = (maxBpm * 0.75).coerceAtLeast(referenceUpper + 20.0)
        val highUpper = max(maxBpm.toDouble(), elevatedUpper + 20.0)
        return HeartRateReferenceZones(
            age = normalizedAge,
            estimatedMaxBpm = estimatedMax,
            bands = listOf(
                HeartRateReferenceBand("Reference", 40.0, referenceUpper, HeartRateZoneTone.REFERENCE),
                HeartRateReferenceBand("Elevated", referenceUpper, elevatedUpper, HeartRateZoneTone.ELEVATED),
                HeartRateReferenceBand("High", elevatedUpper, highUpper, HeartRateZoneTone.HIGH)
            )
        )
    }

    fun estimateRestingHeartRate(
        bpmSamples: List<Double>,
        rangeLabel: String,
        recordedBpm: Double? = null
    ): RestingHeartRateEstimate {
        val clean = bpmSamples
            .filter { it in 25.0..240.0 }
            .sorted()
        if (clean.size < 5) {
            return RestingHeartRateEstimate(
                estimatedBpm = null,
                recordedBpm = recordedBpm,
                sampleCount = clean.size,
                rangeLabel = rangeLabel,
                method = if (recordedBpm != null) {
                    "Recorded resting HR from Health Connect is shown; local estimate needs at least 5 valid HR samples"
                } else {
                    "Needs at least 5 valid local HR samples in range"
                }
            )
        }
        val lowCount = ceil(clean.size * 0.10).roundToInt().coerceAtLeast(3)
        val lowestSamples = clean.take(lowCount)
        return RestingHeartRateEstimate(
            estimatedBpm = median(lowestSamples),
            recordedBpm = recordedBpm,
            sampleCount = clean.size,
            rangeLabel = rangeLabel,
            method = if (recordedBpm != null) {
                "Recorded resting HR from Health Connect is shown; local estimate uses the median of the lowest 10% of valid HR samples"
            } else {
                "Estimated from the median of the lowest 10% of valid local HR samples"
            }
        )
    }

    fun stableChartBounds(values: List<Double>, zones: HeartRateReferenceZones): Pair<Double, Double> {
        val clean = values.filter { it in 1.0..260.0 }
        val dataMin = clean.minOrNull()
        val dataMax = clean.maxOrNull()
        val zoneMax = zones.bands.maxOfOrNull { it.upperBpm } ?: 180.0
        val rawMin = minOf(40.0, dataMin ?: 40.0)
        val rawMax = maxOf(180.0, zoneMax, dataMax ?: 180.0)
        val lower = ((rawMin - 5.0) / 10.0).toInt().coerceAtLeast(3) * 10.0
        val upper = ceil((rawMax + 5.0) / 10.0) * 10.0
        return lower to upper
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val middle = values.size / 2
        return if (values.size % 2 == 1) {
            values[middle]
        } else {
            (values[middle - 1] + values[middle]) / 2.0
        }
    }
}

private fun formatBpm(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)
