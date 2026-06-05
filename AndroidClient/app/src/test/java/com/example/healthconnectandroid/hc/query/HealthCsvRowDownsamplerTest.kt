package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.data.HealthCsvRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCsvRowDownsamplerTest {
    @Test
    fun emptyInputReturnsEmptyRows() {
        val downsampler = HealthCsvRowMinMaxDownsampler(
            startEpochMillis = 0L,
            endEpochMillis = 1_000L,
            targetCount = 10
        )

        assertTrue(downsampler.rows().isEmpty())
    }

    @Test
    fun denseInputKeepsBoundariesAndBoundsResultSize() {
        val rows = (0 until 1_000).map { index -> row(index, 80.0 + (index % 5)) }
        val downsampler = HealthCsvRowMinMaxDownsampler(
            startEpochMillis = 0L,
            endEpochMillis = 1_000_000L,
            targetCount = 120
        )

        rows.forEach(downsampler::offer)
        val result = downsampler.rows()

        assertTrue(result.size <= 120)
        assertEquals(rows.first(), result.first())
        assertEquals(rows.last(), result.last())
    }

    @Test
    fun denseInputKeepsMinAndMaxSpikes() {
        val rows = (0 until 1_000).map { index ->
            when (index) {
                400 -> row(index, 210.0)
                610 -> row(index, 38.0)
                else -> row(index, 82.0)
            }
        }
        val downsampler = HealthCsvRowMinMaxDownsampler(
            startEpochMillis = 0L,
            endEpochMillis = 1_000_000L,
            targetCount = 120
        )

        rows.forEach(downsampler::offer)
        val result = downsampler.rows()

        assertTrue(result.any { it.localValueId == 400L && it.numericValue == 210.0 })
        assertTrue(result.any { it.localValueId == 610L && it.numericValue == 38.0 })
    }

    private fun row(index: Int, value: Double): HealthCsvRow {
        val epochMillis = index * 1_000L
        return HealthCsvRow(
            localRecordId = index.toLong(),
            localValueId = index.toLong(),
            valueKey = "heart_rate:$index",
            recordType = "heart_rate",
            recordKind = "sample",
            healthConnectUid = null,
            dedupeKey = "heart_rate:$index",
            sourcePackage = null,
            recordStartEpochMillis = epochMillis,
            recordEndEpochMillis = null,
            valueStartEpochMillis = epochMillis,
            valueEndEpochMillis = null,
            localDate = null,
            zoneOffsetSeconds = null,
            metric = "heart_rate",
            numericValue = value,
            secondaryNumericValue = null,
            unit = "bpm",
            categoryOrStage = null,
            label = null,
            textValue = null,
            jsonValue = null,
            metadataJson = null,
            rawJson = null
        )
    }
}
