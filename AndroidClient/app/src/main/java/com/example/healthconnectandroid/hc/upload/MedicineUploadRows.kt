package com.example.healthconnectandroid.hc.upload

import com.example.healthconnectandroid.data.HealthRecordEntity
import com.example.healthconnectandroid.data.HealthValueEntity
import com.example.healthconnectandroid.data.MedicineDoseLogEntity
import com.example.healthconnectandroid.data.MedicineItemEntity
import com.example.healthconnectandroid.data.MedicineScheduleEntity
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import org.json.JSONArray

internal object MedicineUploadRows {
    const val ITEM_MEDICINE_ITEM = "medicine_item"
    const val ITEM_MEDICINE_DOSE_LOG = "medicine_dose_log"
    const val MEDICINE_ITEM_VALUE_COUNT = 7
    const val MEDICINE_DOSE_LOG_VALUE_COUNT = 7

    fun counts(itemCount: Int, doseLogCount: Int): UploadPendingCounts =
        UploadPendingCounts(
            records = itemCount + doseLogCount,
            values = itemCount * MEDICINE_ITEM_VALUE_COUNT + doseLogCount * MEDICINE_DOSE_LOG_VALUE_COUNT
        )

    fun fromRows(
        items: List<MedicineItemEntity>,
        schedules: List<MedicineScheduleEntity>,
        doseLogs: List<MedicineDoseLogEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): PendingUploadRows {
        val schedulesByMedicine = schedules.groupBy { it.medicineLocalId }
        val records = mutableListOf<HealthRecordEntity>()
        val values = mutableListOf<HealthValueEntity>()
        val acks = mutableListOf<PendingUploadAck>()

        items.forEach { item ->
            val record = item.toUploadRecord(zoneId)
            val slots = schedulesByMedicine[item.localId].orEmpty().map { it.slot }

            records += record
            values += item.toUploadValues(record.localId, slots, zoneId)
            acks += PendingUploadAck(ITEM_MEDICINE_ITEM, item.localId)
        }

        doseLogs.forEach { log ->
            val record = log.toUploadRecord()

            records += record
            values += log.toUploadValues(record.localId)
            acks += PendingUploadAck(ITEM_MEDICINE_DOSE_LOG, log.localId)
        }

        return PendingUploadRows(records = records, values = values, ackItems = acks)
    }

    private fun MedicineItemEntity.toUploadRecord(zoneId: ZoneId): HealthRecordEntity =
        HealthRecordEntity(
            localId = syntheticLocalId(ITEM_MEDICINE_ITEM, localId, updatedEpochMillis),
            recordUid = null,
            dedupeKey = "$ITEM_MEDICINE_ITEM:$localId:$updatedEpochMillis",
            recordType = ITEM_MEDICINE_ITEM,
            recordKind = RECORD_KIND_PROFILE,
            startEpochMillis = updatedEpochMillis,
            endEpochMillis = null,
            localDate = updatedEpochMillis.localDate(zoneId),
            startZoneOffsetSeconds = null,
            endZoneOffsetSeconds = null,
            sourcePackage = MEDICINE_SOURCE,
            metadataJson = null,
            rawJson = null,
            createdEpochMillis = createdEpochMillis,
            updatedEpochMillis = updatedEpochMillis,
            lastReadEpochMillis = updatedEpochMillis
        )

    private fun MedicineItemEntity.toUploadValues(
        recordLocalId: Long,
        slots: List<String>,
        zoneId: ZoneId
    ): List<HealthValueEntity> {
        val localDate = updatedEpochMillis.localDate(zoneId)
        val slotText = slots.joinToString(",")
        val slotJson = JSONArray(slots).toString()

        return listOf(
            uploadValue(
                sourceKind = ITEM_MEDICINE_ITEM,
                sourceId = localId,
                sourceVersion = updatedEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 0,
                valueKey = "medicine_item:name",
                metric = "medicine_name",
                label = "Name",
                category = ITEM_MEDICINE_ITEM,
                valueText = name,
                localDate = localDate,
                sampleEpochMillis = updatedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_ITEM,
                sourceId = localId,
                sourceVersion = updatedEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 1,
                valueKey = "medicine_item:dose_text",
                metric = "dose_text",
                label = "Dose",
                category = ITEM_MEDICINE_ITEM,
                valueText = doseText,
                localDate = localDate,
                sampleEpochMillis = updatedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_ITEM,
                sourceId = localId,
                sourceVersion = updatedEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 2,
                valueKey = "medicine_item:summary",
                metric = "summary",
                label = "Summary",
                category = ITEM_MEDICINE_ITEM,
                valueText = summary,
                localDate = localDate,
                sampleEpochMillis = updatedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_ITEM,
                sourceId = localId,
                sourceVersion = updatedEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 3,
                valueKey = "medicine_item:details",
                metric = "details",
                label = "Details",
                category = ITEM_MEDICINE_ITEM,
                valueText = details,
                localDate = localDate,
                sampleEpochMillis = updatedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_ITEM,
                sourceId = localId,
                sourceVersion = updatedEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 4,
                valueKey = "medicine_item:notes",
                metric = "notes",
                label = "Notes",
                category = ITEM_MEDICINE_ITEM,
                valueText = notes,
                localDate = localDate,
                sampleEpochMillis = updatedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_ITEM,
                sourceId = localId,
                sourceVersion = updatedEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 5,
                valueKey = "medicine_item:active",
                metric = "active",
                label = "Active",
                category = ITEM_MEDICINE_ITEM,
                valueInt = if (active) 1L else 0L,
                valueText = if (active) "active" else "inactive",
                localDate = localDate,
                sampleEpochMillis = updatedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_ITEM,
                sourceId = localId,
                sourceVersion = updatedEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 6,
                valueKey = "medicine_item:slots",
                metric = "schedule_slots",
                label = "Schedule slots",
                category = ITEM_MEDICINE_ITEM,
                valueText = slotText,
                valueJson = slotJson,
                localDate = localDate,
                sampleEpochMillis = updatedEpochMillis
            )
        )
    }

    private fun MedicineDoseLogEntity.toUploadRecord(): HealthRecordEntity =
        HealthRecordEntity(
            localId = syntheticLocalId(ITEM_MEDICINE_DOSE_LOG, localId, createdEpochMillis),
            recordUid = null,
            dedupeKey = "$ITEM_MEDICINE_DOSE_LOG:$localId",
            recordType = ITEM_MEDICINE_DOSE_LOG,
            recordKind = RECORD_KIND_EVENT,
            startEpochMillis = recordedEpochMillis,
            endEpochMillis = null,
            localDate = localDate,
            startZoneOffsetSeconds = null,
            endZoneOffsetSeconds = null,
            sourcePackage = MEDICINE_SOURCE,
            metadataJson = null,
            rawJson = null,
            createdEpochMillis = createdEpochMillis,
            updatedEpochMillis = recordedEpochMillis,
            lastReadEpochMillis = recordedEpochMillis
        )

    private fun MedicineDoseLogEntity.toUploadValues(recordLocalId: Long): List<HealthValueEntity> =
        listOf(
            uploadValue(
                sourceKind = ITEM_MEDICINE_DOSE_LOG,
                sourceId = localId,
                sourceVersion = createdEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 0,
                valueKey = "medicine_dose_log:medicine_local_id",
                metric = "medicine_local_id",
                label = "Medicine local ID",
                category = ITEM_MEDICINE_DOSE_LOG,
                valueInt = medicineLocalId,
                localDate = localDate,
                sampleEpochMillis = recordedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_DOSE_LOG,
                sourceId = localId,
                sourceVersion = createdEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 1,
                valueKey = "medicine_dose_log:medicine_name",
                metric = "medicine_name",
                label = "Medicine",
                category = ITEM_MEDICINE_DOSE_LOG,
                valueText = medicineName,
                localDate = localDate,
                sampleEpochMillis = recordedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_DOSE_LOG,
                sourceId = localId,
                sourceVersion = createdEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 2,
                valueKey = "medicine_dose_log:slot",
                metric = "slot",
                label = "Slot",
                category = ITEM_MEDICINE_DOSE_LOG,
                valueText = slot,
                localDate = localDate,
                sampleEpochMillis = recordedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_DOSE_LOG,
                sourceId = localId,
                sourceVersion = createdEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 3,
                valueKey = "medicine_dose_log:status",
                metric = "status",
                label = "Status",
                category = ITEM_MEDICINE_DOSE_LOG,
                valueText = status,
                localDate = localDate,
                sampleEpochMillis = recordedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_DOSE_LOG,
                sourceId = localId,
                sourceVersion = createdEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 4,
                valueKey = "medicine_dose_log:source",
                metric = "source",
                label = "Source",
                category = ITEM_MEDICINE_DOSE_LOG,
                valueText = source,
                localDate = localDate,
                sampleEpochMillis = recordedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_DOSE_LOG,
                sourceId = localId,
                sourceVersion = createdEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 5,
                valueKey = "medicine_dose_log:note",
                metric = "note",
                label = "Note",
                category = ITEM_MEDICINE_DOSE_LOG,
                valueText = note,
                localDate = localDate,
                sampleEpochMillis = recordedEpochMillis
            ),
            uploadValue(
                sourceKind = ITEM_MEDICINE_DOSE_LOG,
                sourceId = localId,
                sourceVersion = createdEpochMillis,
                recordLocalId = recordLocalId,
                sequence = 6,
                valueKey = "medicine_dose_log:scheduled_epoch_millis",
                metric = "scheduled_epoch_millis",
                label = "Scheduled time",
                category = ITEM_MEDICINE_DOSE_LOG,
                valueInt = scheduledEpochMillis,
                localDate = localDate,
                sampleEpochMillis = recordedEpochMillis
            )
        )

    private fun uploadValue(
        sourceKind: String,
        sourceId: Long,
        sourceVersion: Long,
        recordLocalId: Long,
        sequence: Int,
        valueKey: String,
        metric: String,
        label: String,
        category: String,
        valueInt: Long? = null,
        valueText: String? = null,
        valueJson: String? = null,
        localDate: String,
        sampleEpochMillis: Long
    ): HealthValueEntity =
        HealthValueEntity(
            localId = syntheticLocalId("${sourceKind}_value", sourceId, sourceVersion, sequence),
            recordLocalId = recordLocalId,
            valueKey = valueKey,
            metric = metric,
            unit = null,
            label = label,
            category = category,
            numericValue = null,
            secondaryNumericValue = null,
            valueFloat = null,
            valueInt = valueInt,
            valueText = valueText,
            valueJson = valueJson,
            startEpochMillis = null,
            endEpochMillis = null,
            localDate = localDate,
            sampleEpochMillis = sampleEpochMillis,
            sequence = sequence
        )

    private fun Long.localDate(zoneId: ZoneId): String =
        Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate().toString()

    // The server dedupe path currently keys generic uploaded rows by localId only.
    // Negative synthetic IDs keep medicine rows away from Health Connect row IDs.
    private fun syntheticLocalId(
        sourceKind: String,
        sourceId: Long,
        sourceVersion: Long,
        sequence: Int = 0
    ): Long {
        val digestInput = "$sourceKind:$sourceId:$sourceVersion:$sequence".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(digestInput)
        var hashPrefix = 0L

        for (index in 0 until Long.SIZE_BYTES) {
            hashPrefix = (hashPrefix shl Byte.SIZE_BITS) or (digest[index].toLong() and 0xffL)
        }

        val positiveId = hashPrefix and Long.MAX_VALUE
        return if (positiveId == 0L) -1L else -positiveId
    }

    private const val MEDICINE_SOURCE = "com.example.healthconnectandroid.medicine"
    private const val RECORD_KIND_PROFILE = "medicine_profile"
    private const val RECORD_KIND_EVENT = "medicine_event"
}
