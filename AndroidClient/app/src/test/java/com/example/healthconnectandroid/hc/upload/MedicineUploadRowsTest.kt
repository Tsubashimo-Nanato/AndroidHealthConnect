package com.example.healthconnectandroid.hc.upload

import com.example.healthconnectandroid.data.MedicineDoseLogEntity
import com.example.healthconnectandroid.data.MedicineItemEntity
import com.example.healthconnectandroid.data.MedicineScheduleEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineUploadRowsTest {
    @Test
    fun medicineItemsMapToRecordsValuesAndOriginalAcks() {
        val updatedAt = epoch("2026-06-10T01:30:00Z")
        val rows = MedicineUploadRows.fromRows(
            items = listOf(
                MedicineItemEntity(
                    localId = 7,
                    name = "Morning tablet",
                    notes = "Take after food",
                    doseText = "1 tablet",
                    summary = "Daily medicine",
                    details = "Keep the description short.",
                    active = true,
                    createdEpochMillis = epoch("2026-06-01T00:00:00Z"),
                    updatedEpochMillis = updatedAt
                )
            ),
            schedules = listOf(
                MedicineScheduleEntity(
                    medicineLocalId = 7,
                    slot = "morning",
                    createdEpochMillis = updatedAt
                ),
                MedicineScheduleEntity(
                    medicineLocalId = 7,
                    slot = "night",
                    createdEpochMillis = updatedAt
                )
            ),
            doseLogs = emptyList(),
            zoneId = ZoneId.of("Asia/Tokyo")
        )

        assertEquals(1, rows.records.size)
        assertEquals(7, rows.values.size)
        assertEquals(listOf(PendingUploadAck(MedicineUploadRows.ITEM_MEDICINE_ITEM, 7)), rows.ackItems)

        val record = rows.records.single()
        assertTrue(record.localId < 0)
        assertEquals("medicine_item", record.recordType)
        assertEquals("medicine_profile", record.recordKind)
        assertEquals("medicine_item:7:$updatedAt", record.dedupeKey)
        assertEquals("2026-06-10", record.localDate)

        val values = rows.values.associateBy { it.metric }
        assertEquals("Morning tablet", values.getValue("medicine_name").valueText)
        assertEquals("1 tablet", values.getValue("dose_text").valueText)
        assertEquals(1L, values.getValue("active").valueInt)
        assertEquals("morning,night", values.getValue("schedule_slots").valueText)
        assertEquals("""["morning","night"]""", values.getValue("schedule_slots").valueJson)
    }

    @Test
    fun medicineDoseLogsMapToRecordsValuesAndOriginalAcks() {
        val recordedAt = epoch("2026-06-11T12:10:00Z")
        val rows = MedicineUploadRows.fromRows(
            items = emptyList(),
            schedules = emptyList(),
            doseLogs = listOf(
                MedicineDoseLogEntity(
                    localId = 11,
                    medicineLocalId = 7,
                    medicineName = "Morning tablet",
                    slot = "afternoon",
                    localDate = "2026-06-11",
                    scheduledEpochMillis = epoch("2026-06-11T12:00:00Z"),
                    recordedEpochMillis = recordedAt,
                    status = "taken",
                    source = "manual",
                    note = "Logged early",
                    createdEpochMillis = recordedAt
                )
            ),
            zoneId = ZoneId.of("Asia/Tokyo")
        )

        assertEquals(1, rows.records.size)
        assertEquals(7, rows.values.size)
        assertEquals(listOf(PendingUploadAck(MedicineUploadRows.ITEM_MEDICINE_DOSE_LOG, 11)), rows.ackItems)

        val record = rows.records.single()
        assertTrue(record.localId < 0)
        assertEquals("medicine_dose_log", record.recordType)
        assertEquals("medicine_event", record.recordKind)
        assertEquals("medicine_dose_log:11", record.dedupeKey)

        val values = rows.values.associateBy { it.metric }
        assertEquals(7L, values.getValue("medicine_local_id").valueInt)
        assertEquals("taken", values.getValue("status").valueText)
        assertEquals("manual", values.getValue("source").valueText)
        assertEquals("Logged early", values.getValue("note").valueText)
    }

    @Test
    fun pendingCountsUseOneRecordAndKnownValueShapePerMedicineRow() {
        assertEquals(
            UploadPendingCounts(records = 3, values = 21),
            MedicineUploadRows.counts(itemCount = 2, doseLogCount = 1)
        )
    }

    private fun epoch(value: String): Long =
        Instant.parse(value).toEpochMilli()
}
