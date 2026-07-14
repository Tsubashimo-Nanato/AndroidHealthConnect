package com.example.healthconnectandroid.hc.upload

import com.example.healthconnectandroid.data.HealthAggregateEntity
import com.example.healthconnectandroid.data.HealthRecordEntity
import com.example.healthconnectandroid.data.HealthValueEntity
import org.json.JSONArray
import org.json.JSONObject

internal data class PendingUploadRows(
    val records: List<HealthRecordEntity> = emptyList(),
    val values: List<HealthValueEntity> = emptyList(),
    val aggregates: List<HealthAggregateEntity> = emptyList(),
    val ackItems: List<PendingUploadAck> = emptyList()
) {
    val isEmpty: Boolean get() = records.isEmpty() && values.isEmpty() && aggregates.isEmpty()
    val primaryKind: String
        get() = when {
            records.isNotEmpty() -> "records"
            values.isNotEmpty() -> "values"
            aggregates.isNotEmpty() -> "aggregates"
            else -> "none"
        }

    operator fun plus(other: PendingUploadRows): PendingUploadRows =
        PendingUploadRows(
            records = records + other.records,
            values = values + other.values,
            aggregates = aggregates + other.aggregates,
            ackItems = ackItems + other.ackItems
        )
}

internal data class PendingUploadAck(
    val itemKind: String,
    val localId: Long
)

internal data class UploadReadCursor(
    val recordLocalId: Long = 0,
    val valueLocalId: Long = 0,
    val aggregateLocalId: Long = 0
) {
    fun advance(rows: PendingUploadRows): UploadReadCursor =
        copy(
            recordLocalId = rows.records.maxOfOrNull { it.localId }?.coerceAtLeast(recordLocalId) ?: recordLocalId,
            valueLocalId = rows.values.maxOfOrNull { it.localId }?.coerceAtLeast(valueLocalId) ?: valueLocalId,
            aggregateLocalId = rows.aggregates.maxOfOrNull { it.localId }?.coerceAtLeast(aggregateLocalId)
                ?: aggregateLocalId
        )
}

internal data class UploadBatch(
    val schemaVersion: Int,
    val deviceId: String,
    val batchId: String,
    val createdAtEpochMillis: Long,
    val records: List<HealthRecordEntity>,
    val values: List<HealthValueEntity>,
    val aggregates: List<HealthAggregateEntity>
) {
    companion object {
        fun fromRows(
            schemaVersion: Int,
            deviceId: String,
            batchId: String,
            createdAtEpochMillis: Long,
            rows: PendingUploadRows
        ): UploadBatch =
            UploadBatch(
                schemaVersion = schemaVersion,
                deviceId = deviceId,
                batchId = batchId,
                createdAtEpochMillis = createdAtEpochMillis,
                records = rows.records,
                values = rows.values,
                aggregates = rows.aggregates
            )
    }
}

internal fun UploadBatch.toJson(): JSONObject =
    JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("deviceId", deviceId)
        .put("batchId", batchId)
        .put("createdAtEpochMillis", createdAtEpochMillis)
        .put("records", records.toJsonArray { it.toUploadJson() })
        .put("values", values.toJsonArray { it.toUploadJson() })
        .put("aggregates", aggregates.toJsonArray { it.toUploadJson() })

private inline fun <T> Iterable<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray =
    JSONArray().apply {
        for (item in this@toJsonArray) {
            put(transform(item))
        }
    }

private fun HealthRecordEntity.toUploadJson(): JSONObject =
    JSONObject()
        .put("localId", localId)
        .putNullable("recordUid", recordUid)
        .put("dedupeKey", dedupeKey)
        .put("recordType", recordType)
        .put("recordKind", recordKind)
        .put("startEpochMillis", startEpochMillis)
        .putNullable("endEpochMillis", endEpochMillis)
        .put("localDate", localDate)
        .putNullable("startZoneOffsetSeconds", startZoneOffsetSeconds)
        .putNullable("endZoneOffsetSeconds", endZoneOffsetSeconds)
        .putNullable("sourcePackage", sourcePackage)
        .put("syncStatus", syncStatus)
        .put("exportStatus", exportStatus)
        .put("createdEpochMillis", createdEpochMillis)
        .put("updatedEpochMillis", updatedEpochMillis)
        .put("lastReadEpochMillis", lastReadEpochMillis)
        .putNullable("syncedEpochMillis", syncedEpochMillis)
        .putNullable("exportedEpochMillis", exportedEpochMillis)

private fun HealthValueEntity.toUploadJson(): JSONObject =
    JSONObject()
        .put("localId", localId)
        .put("recordLocalId", recordLocalId)
        .put("valueKey", valueKey)
        .put("metric", metric)
        .putNullable("unit", unit)
        .putNullable("label", label)
        .putNullable("category", category)
        .putNullable("numericValue", numericValue)
        .putNullable("secondaryNumericValue", secondaryNumericValue)
        .putNullable("valueFloat", valueFloat)
        .putNullable("valueInt", valueInt)
        .putNullable("valueText", valueText)
        .putNullable("valueJson", valueJson)
        .putNullable("startEpochMillis", startEpochMillis)
        .putNullable("endEpochMillis", endEpochMillis)
        .putNullable("localDate", localDate)
        .putNullable("sampleEpochMillis", sampleEpochMillis)
        .putNullable("sequence", sequence)

private fun HealthAggregateEntity.toUploadJson(): JSONObject =
    JSONObject()
        .put("localId", localId)
        .put("recordType", recordType)
        .put("metric", metric)
        .put("bucketPeriod", bucketPeriod)
        .put("bucketStartEpochMillis", bucketStartEpochMillis)
        .put("bucketEndEpochMillis", bucketEndEpochMillis)
        .put("localDate", localDate)
        .putNullable("timezoneId", timezoneId)
        .put("value", value)
        .putNullable("unit", unit)
        .put("source", source)
        .put("computedEpochMillis", computedEpochMillis)
        .put("requestedStartEpochMillis", requestedStartEpochMillis)
        .put("requestedEndEpochMillis", requestedEndEpochMillis)

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)
