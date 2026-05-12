package com.example.healthconnectandroid.ui.sleep

import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.ReadableHealthRecord
import com.example.healthconnectandroid.hc.SleepQualityBand
import com.example.healthconnectandroid.hc.VisualizationType
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SleepSessionModelsTest {
    @Test
    fun analysisFallsBackToStageBoundsWhenSessionSummaryTimesAreMissing() {
        val start = Instant.parse("2026-05-11T20:26:00Z")
        val end = Instant.parse("2026-05-12T00:54:00Z")
        val rows = listOf(
            sleepRow(
                metric = "duration",
                primaryText = "Sleep session - 4h 28m",
                start = null,
                end = null,
                valueId = 1
            ),
            sleepRow(
                metric = "sleep_stage",
                primaryText = "Light sleep - 4h 28m",
                start = start,
                end = end,
                valueId = 2
            )
        )

        val model = sleepSessionModels(rows, ZoneOffset.UTC).single()
        val input = model.toSleepInput()

        assertEquals(start, model.analysisStart)
        assertEquals(end, model.analysisEnd)
        assertEquals(start, input.start)
        assertEquals(end, input.end)
        assertNotEquals(SleepQualityBand.UNKNOWN, model.analysis.qualityBand)
    }

    private fun sleepRow(
        metric: String,
        primaryText: String,
        start: Instant?,
        end: Instant?,
        valueId: Long
    ): ReadableHealthRecord =
        ReadableHealthRecord(
            rowKey = "1:$valueId:$metric",
            localRecordId = 1,
            localValueId = valueId,
            recordTypeKey = HealthDataTypeKeys.SLEEP_SESSION,
            metric = metric,
            displayName = "Sleep session",
            primaryText = primaryText,
            secondaryText = "",
            value = null,
            value2 = null,
            unit = null,
            startTime = start,
            endTime = end,
            localDate = null,
            durationText = null,
            sourceText = null,
            metadataText = null,
            rawDetailsText = null,
            detailFields = emptyList(),
            visualizationType = VisualizationType.SESSION_TIMELINE
        )
}
