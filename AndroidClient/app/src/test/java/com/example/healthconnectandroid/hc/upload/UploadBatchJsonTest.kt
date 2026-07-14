package com.example.healthconnectandroid.hc.upload

import com.example.healthconnectandroid.data.HealthAggregateEntity
import com.example.healthconnectandroid.data.HealthRecordEntity
import com.example.healthconnectandroid.data.HealthValueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadBatchJsonTest {
    @Test
    fun batchJsonKeepsPublicWireFields() {
        val batch = UploadBatch.fromRows(
            schemaVersion = 1,
            deviceId = "device-1",
            batchId = "batch-1",
            createdAtEpochMillis = 1000,
            rows = PendingUploadRows(
                records = listOf(record()),
                values = listOf(value()),
                aggregates = listOf(aggregate())
            )
        )

        val json = batch.toJson()

        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals("batch-1", json.getString("batchId"))
        assertEquals(1000, json.getLong("createdAtEpochMillis"))

        val record = json.getJSONArray("records").getJSONObject(0)
        assertEquals(11, record.getLong("localId"))
        assertTrue(record.isNull("recordUid"))
        assertEquals("heart_rate:1", record.getString("dedupeKey"))
        assertEquals("heart_rate", record.getString("recordType"))
        assertEquals("sampled_series", record.getString("recordKind"))
        assertEquals("local", record.getString("syncStatus"))
        assertEquals("pending", record.getString("exportStatus"))

        val value = json.getJSONArray("values").getJSONObject(0)
        assertEquals(21, value.getLong("localId"))
        assertEquals(11, value.getLong("recordLocalId"))
        assertEquals("bpm", value.getString("metric"))
        assertEquals(72.0, value.getDouble("numericValue"), 0.001)
        assertEquals(900, value.getLong("sampleEpochMillis"))

        val aggregate = json.getJSONArray("aggregates").getJSONObject(0)
        assertEquals(31, aggregate.getLong("localId"))
        assertEquals("steps", aggregate.getString("recordType"))
        assertEquals("daily", aggregate.getString("bucketPeriod"))
        assertEquals(1200.0, aggregate.getDouble("value"), 0.001)
    }

    @Test
    fun pendingRowsPrimaryKindUsesFirstAvailableGroup() {
        assertEquals(
            "values",
            PendingUploadRows(records = emptyList(), values = listOf(value()), aggregates = listOf(aggregate())).primaryKind
        )
        assertEquals(
            "aggregates",
            PendingUploadRows(records = emptyList(), values = emptyList(), aggregates = listOf(aggregate())).primaryKind
        )
        assertEquals(
            "none",
            PendingUploadRows(records = emptyList(), values = emptyList(), aggregates = emptyList()).primaryKind
        )
    }

    @Test
    fun readCursorAdvancesOnlyPastUploadedHealthRows() {
        val rows = PendingUploadRows(
            records = listOf(record().copy(localId = 15), record().copy(localId = -4)),
            values = listOf(value().copy(localId = 28)),
            aggregates = listOf(aggregate().copy(localId = 37))
        )

        val cursor = UploadReadCursor(recordLocalId = 12, valueLocalId = 22, aggregateLocalId = 32)
            .advance(rows)

        assertEquals(15, cursor.recordLocalId)
        assertEquals(28, cursor.valueLocalId)
        assertEquals(37, cursor.aggregateLocalId)
    }

    private fun record(): HealthRecordEntity =
        HealthRecordEntity(
            localId = 11,
            recordUid = null,
            dedupeKey = "heart_rate:1",
            recordType = "heart_rate",
            recordKind = "sampled_series",
            startEpochMillis = 900,
            endEpochMillis = 1200,
            localDate = "2026-06-10",
            startZoneOffsetSeconds = 32400,
            endZoneOffsetSeconds = 32400,
            sourcePackage = "test.package",
            metadataJson = "{}",
            rawJson = "{}",
            createdEpochMillis = 100,
            updatedEpochMillis = 200,
            lastReadEpochMillis = 300
        )

    private fun value(): HealthValueEntity =
        HealthValueEntity(
            localId = 21,
            recordLocalId = 11,
            valueKey = "heart_rate:0",
            metric = "bpm",
            unit = "bpm",
            label = "Heart rate",
            category = "sample",
            numericValue = 72.0,
            secondaryNumericValue = null,
            valueFloat = null,
            valueInt = null,
            valueText = null,
            valueJson = null,
            startEpochMillis = null,
            endEpochMillis = null,
            localDate = "2026-06-10",
            sampleEpochMillis = 900,
            sequence = 0
        )

    private fun aggregate(): HealthAggregateEntity =
        HealthAggregateEntity(
            localId = 31,
            recordType = "steps",
            metric = "count",
            bucketPeriod = "daily",
            bucketStartEpochMillis = 0,
            bucketEndEpochMillis = 86_400_000,
            localDate = "2026-06-10",
            timezoneId = "Asia/Tokyo",
            value = 1200.0,
            unit = "count",
            source = "local",
            computedEpochMillis = 1000,
            requestedStartEpochMillis = 0,
            requestedEndEpochMillis = 86_400_000,
            rawJson = "{}"
        )
}
