package com.example.healthconnectandroid.hc.retention

import com.example.healthconnectandroid.data.HealthDailyArchiveEntity
import com.example.healthconnectandroid.data.HealthRecordEntity
import com.example.healthconnectandroid.data.HealthSleepArchiveEntity
import com.example.healthconnectandroid.data.HealthValueEntity
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import kotlin.math.max

internal data class HealthArchiveRows(
    val daily: List<HealthDailyArchiveEntity>,
    val sleep: List<HealthSleepArchiveEntity>
)

internal object HealthArchiveBuilder {
    fun build(
        records: List<HealthRecordEntity>,
        values: List<HealthValueEntity>,
        archivedAtEpochMillis: Long
    ): HealthArchiveRows {
        if (records.isEmpty()) return HealthArchiveRows(emptyList(), emptyList())
        val recordsById = records.associateBy { it.localId }
        val numericGroups = values.asSequence()
            .filter { it.numericValue != null }
            .groupBy { it.recordLocalId to it.metric }
        val daily = numericGroups.mapNotNull { (key, group) ->
            val record = recordsById[key.first] ?: return@mapNotNull null
            val numbers = group.mapNotNull { it.numericValue }
            if (numbers.isEmpty()) return@mapNotNull null
            HealthDailyArchiveEntity(
                sourceRecordLocalId = record.localId,
                metric = key.second,
                recordType = record.recordType,
                localDate = group.firstNotNullOfOrNull { it.localDate } ?: record.localDate,
                sampleCount = numbers.size,
                totalValue = numbers.sum(),
                minValue = numbers.min(),
                maxValue = numbers.max(),
                unit = group.mapNotNull { it.unit }.firstOrNull(),
                lastSampleEpochMillis = group.mapNotNull { valueEpochMillis(it) }.maxOrNull(),
                archivedAtEpochMillis = archivedAtEpochMillis
            )
        }
        val sleep = buildSleepRows(records, values, archivedAtEpochMillis)
        return HealthArchiveRows(daily, sleep)
    }

    private fun buildSleepRows(
        records: List<HealthRecordEntity>,
        values: List<HealthValueEntity>,
        archivedAtEpochMillis: Long
    ): List<HealthSleepArchiveEntity> {
        val sleepRecords = records.filter { it.recordType == HealthDataTypeKeys.SLEEP_SESSION }
        if (sleepRecords.isEmpty()) return emptyList()
        val valuesByRecord = values.groupBy { it.recordLocalId }
        return buildList {
            sleepRecords.forEach { record ->
                val recordValues = valuesByRecord[record.localId].orEmpty()
                recordValues.forEach { value ->
                    add(value.toSleepArchive(record, archivedAtEpochMillis))
                }
                if (recordValues.none { it.metric == "duration" }) {
                    add(syntheticDuration(record, archivedAtEpochMillis))
                }
            }
        }
    }

    private fun HealthValueEntity.toSleepArchive(
        record: HealthRecordEntity,
        archivedAtEpochMillis: Long
    ) = HealthSleepArchiveEntity(
        sourceRecordLocalId = record.localId,
        sourceValueLocalId = localId,
        valueKey = valueKey,
        recordStartEpochMillis = record.startEpochMillis,
        recordEndEpochMillis = record.endEpochMillis,
        valueStartEpochMillis = startEpochMillis ?: sampleEpochMillis,
        valueEndEpochMillis = endEpochMillis,
        localDate = localDate ?: record.localDate,
        metric = metric,
        numericValue = numericValue,
        secondaryNumericValue = secondaryNumericValue,
        unit = unit,
        category = category,
        label = label,
        textValue = valueText,
        jsonValue = valueJson,
        sourcePackage = record.sourcePackage,
        archivedAtEpochMillis = archivedAtEpochMillis
    )

    private fun syntheticDuration(
        record: HealthRecordEntity,
        archivedAtEpochMillis: Long
    ): HealthSleepArchiveEntity {
        val durationMinutes = record.endEpochMillis
            ?.let { end -> max(0L, end - record.startEpochMillis) / 60_000.0 }
        return HealthSleepArchiveEntity(
            sourceRecordLocalId = record.localId,
            sourceValueLocalId = 0,
            valueKey = "archived_duration",
            recordStartEpochMillis = record.startEpochMillis,
            recordEndEpochMillis = record.endEpochMillis,
            valueStartEpochMillis = record.startEpochMillis,
            valueEndEpochMillis = record.endEpochMillis,
            localDate = record.localDate,
            metric = "duration",
            numericValue = durationMinutes,
            secondaryNumericValue = null,
            unit = "minutes",
            category = null,
            label = null,
            textValue = null,
            jsonValue = null,
            sourcePackage = record.sourcePackage,
            archivedAtEpochMillis = archivedAtEpochMillis
        )
    }

    private fun valueEpochMillis(value: HealthValueEntity): Long? =
        value.endEpochMillis ?: value.startEpochMillis ?: value.sampleEpochMillis
}
