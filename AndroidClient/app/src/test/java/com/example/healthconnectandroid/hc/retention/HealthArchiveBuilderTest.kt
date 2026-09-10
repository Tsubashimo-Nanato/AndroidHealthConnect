package com.example.healthconnectandroid.hc.retention

import com.example.healthconnectandroid.data.HealthRecordEntity
import com.example.healthconnectandroid.data.HealthValueEntity
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthArchiveBuilderTest {
    @Test
    fun compactsNumericValuesPerRecordAndMetric() {
        val record = record(localId = 7, recordType = HealthDataTypeKeys.HEART_RATE)
        val values = listOf(
            value(localId = 70, recordLocalId = 7, metric = "heart_rate", numericValue = 60.0),
            value(localId = 71, recordLocalId = 7, metric = "heart_rate", numericValue = 80.0)
        )

        val archive = HealthArchiveBuilder.build(listOf(record), values, archivedAtEpochMillis = 500)

        assertEquals(1, archive.daily.size)
        assertEquals(2, archive.daily.single().sampleCount)
        assertEquals(140.0, archive.daily.single().totalValue, 0.0)
        assertEquals(60.0, archive.daily.single().minValue, 0.0)
        assertEquals(80.0, archive.daily.single().maxValue, 0.0)
        assertTrue(archive.sleep.isEmpty())
    }

    @Test
    fun keepsSleepStagesAndAddsMissingDuration() {
        val record = record(
            localId = 9,
            recordType = HealthDataTypeKeys.SLEEP_SESSION,
            startEpochMillis = 1_000,
            endEpochMillis = 3_601_000
        )
        val stage = value(
            localId = 90,
            recordLocalId = 9,
            metric = "sleep_stage",
            numericValue = null,
            valueJson = "{\"stage\":\"deep\"}"
        )

        val archive = HealthArchiveBuilder.build(listOf(record), listOf(stage), archivedAtEpochMillis = 500)

        assertEquals(2, archive.sleep.size)
        assertTrue(archive.sleep.any { it.metric == "sleep_stage" && it.jsonValue?.contains("deep") == true })
        assertEquals(60.0, archive.sleep.single { it.metric == "duration" }.numericValue ?: -1.0, 0.0)
    }

    @Test
    fun retentionOnlyAcceptsProductionAndUsesThirtyDayCutoff() {
        val now = Instant.parse("2026-07-12T00:00:00Z")

        assertTrue(HealthRetentionPolicy.acceptsServerKey("PRODUCTION:https://example.test/"))
        assertFalse(HealthRetentionPolicy.acceptsServerKey("LOCAL_DEBUG:http://10.23.45.68/"))
        assertEquals(Instant.parse("2026-06-12T00:00:00Z").toEpochMilli(), HealthRetentionPolicy.cutoffEpochMillis(now))
    }

    @Test
    fun compactionRequiresReclaimablePagesAndVacuumWorkspace() {
        val gib = 1024L * 1024L * 1024L

        assertTrue(DatabaseCompactionPolicy.shouldCompact(2 * gib, gib, 3 * gib))
        assertFalse(DatabaseCompactionPolicy.shouldCompact(2 * gib, 10, 3 * gib))
        assertFalse(DatabaseCompactionPolicy.shouldCompact(2 * gib, gib, 2 * gib))
    }

    private fun record(
        localId: Long,
        recordType: String,
        startEpochMillis: Long = 1_000,
        endEpochMillis: Long? = 2_000
    ) = HealthRecordEntity(
        localId = localId,
        recordUid = "uid-$localId",
        dedupeKey = "dedupe-$localId",
        recordType = recordType,
        recordKind = "interval",
        startEpochMillis = startEpochMillis,
        endEpochMillis = endEpochMillis,
        localDate = "2026-06-01",
        startZoneOffsetSeconds = 0,
        endZoneOffsetSeconds = 0,
        sourcePackage = "test",
        metadataJson = null,
        rawJson = null,
        createdEpochMillis = 1,
        updatedEpochMillis = 1,
        lastReadEpochMillis = 1
    )

    private fun value(
        localId: Long,
        recordLocalId: Long,
        metric: String,
        numericValue: Double?,
        valueJson: String? = null
    ) = HealthValueEntity(
        localId = localId,
        recordLocalId = recordLocalId,
        valueKey = "value-$localId",
        metric = metric,
        unit = "bpm",
        label = null,
        category = null,
        numericValue = numericValue,
        secondaryNumericValue = null,
        valueFloat = numericValue,
        valueInt = null,
        valueText = null,
        valueJson = valueJson,
        startEpochMillis = 1_000,
        endEpochMillis = 2_000,
        localDate = "2026-06-01",
        sampleEpochMillis = 1_000,
        sequence = 0
    )
}
