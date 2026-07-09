package com.example.healthconnectandroid.medicine

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineDoseEventsTest {
    @Test
    fun groupsRowsFromOneAnswerIntoOneDoseEvent() {
        val date = LocalDate.parse("2026-07-07")
        val logs = listOf(
            doseLog(1, "Morning sample A", date, 1234L),
            doseLog(2, "Morning sample B", date, 1234L),
            doseLog(3, "As-needed sample", date, 9999L)
        )

        val events = logs.toDoseEvents()

        assertEquals(2, events.size)
        assertEquals(listOf(1L, 2L), events[1].logIds)
        assertEquals(listOf("Morning sample A", "Morning sample B"), events[1].medicineNames)
        assertEquals(listOf(3L), events[0].logIds)
    }

    private fun doseLog(
        id: Long,
        name: String,
        date: LocalDate,
        recordedAt: Long
    ): MedicineDoseLog =
        MedicineDoseLog(
            localId = id,
            medicineLocalId = id,
            medicineName = name,
            slot = MedicineSlot.MORNING,
            localDate = date,
            scheduledEpochMillis = null,
            recordedEpochMillis = recordedAt,
            status = MedicineDoseStatus.TAKEN,
            source = MedicineLogSource.MANUAL,
            note = null
        )
}
