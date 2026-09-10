package com.example.healthconnectandroid.hc

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateSampleRetentionTest {
    @Test
    fun keepsLastRealSampleFromEachMinute() {
        val samples = listOf(
            Sample(0, 61),
            Sample(15_000, 62),
            Sample(59_999, 63),
            Sample(60_000, 64),
            Sample(119_999, 65)
        )

        val retained = retainedHeartRateSamples(samples, Sample::epochMillis)

        assertEquals(listOf(Sample(59_999, 63), Sample(119_999, 65)), retained)
    }

    @Test
    fun sortsUnexpectedOutOfOrderSamplesBeforeReducing() {
        val samples = listOf(
            Sample(120_000, 72),
            Sample(30_000, 60),
            Sample(90_000, 68)
        )

        val retained = retainedHeartRateSamples(samples, Sample::epochMillis)

        assertEquals(
            listOf(Sample(30_000, 60), Sample(90_000, 68), Sample(120_000, 72)),
            retained
        )
    }

    @Test
    fun reducesSixHoursOfFiveSecondSamplesToOnePerMinute() {
        val samples = (0 until 4_320).map { index ->
            Sample(epochMillis = index * 5_000L, bpm = 60 + index % 20)
        }

        val retained = retainedHeartRateSamples(samples, Sample::epochMillis)

        assertEquals(360, retained.size)
        assertEquals(samples.last(), retained.last())
    }

    private data class Sample(
        val epochMillis: Long,
        val bpm: Int
    )
}
