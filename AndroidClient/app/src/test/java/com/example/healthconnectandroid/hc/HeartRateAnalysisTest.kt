package com.example.healthconnectandroid.hc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateAnalysisTest {
    @Test
    fun restingEstimateIgnoresInvalidSamplesAndRequiresMinimumCount() {
        val estimate = HeartRateAnalysis.estimateRestingHeartRate(
            bpmSamples = listOf(0.0, 20.0, 60.0, 61.0, 62.0, 241.0),
            rangeLabel = "test"
        )

        assertNull(estimate.estimatedBpm)
        assertEquals(3, estimate.sampleCount)
        assertTrue(estimate.displayText.contains("Not enough data"))
    }

    @Test
    fun restingEstimateUsesMedianOfLowestTenPercent() {
        val estimate = HeartRateAnalysis.estimateRestingHeartRate(
            bpmSamples = listOf(50.0, 52.0, 54.0, 80.0, 82.0, 84.0, 86.0, 88.0, 90.0, 92.0),
            rangeLabel = "test"
        )

        assertEquals(52.0, estimate.estimatedBpm!!, 0.001)
        assertEquals(10, estimate.sampleCount)
    }

    @Test
    fun recordedRestingHeartRateIsPreferredForDisplay() {
        val estimate = HeartRateAnalysis.estimateRestingHeartRate(
            bpmSamples = listOf(50.0, 52.0, 54.0, 80.0, 82.0, 84.0, 86.0, 88.0, 90.0, 92.0),
            rangeLabel = "test",
            recordedBpm = 58.0
        )

        assertEquals(58.0, estimate.recordedBpm!!, 0.001)
        assertTrue(estimate.displayText.startsWith("Recorded 58 bpm"))
    }

    @Test
    fun referenceZonesUseGenericBandsWhenAgeIsMissing() {
        val zones = HeartRateAnalysis.referenceZones(age = null)

        assertNull(zones.age)
        assertNull(zones.estimatedMaxBpm)
        assertEquals(listOf("Reference", "Elevated", "High"), zones.bands.map { it.label })
        assertTrue(zones.bands.all { it.upperBpm > it.lowerBpm })
    }

    @Test
    fun referenceZonesAreAgeAwareWithoutMedicalDangerLabels() {
        val zones = HeartRateAnalysis.referenceZones(age = 40)
        val labels = zones.bands.joinToString(" ") { it.label }.lowercase()

        assertEquals(40, zones.age)
        assertEquals(180, zones.estimatedMaxBpm)
        assertFalse(labels.contains("danger"))
        assertFalse(labels.contains("disease"))
        assertFalse(labels.contains("diagnosis"))
    }
}
