package com.example.healthconnectandroid.medicine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineReminderPromptPolicyTest {
    @Test
    fun defaultSelectionIncludesEveryScheduledMedicine() {
        val medicines = listOf(
            medicine(localId = 1L),
            medicine(localId = 2L),
            medicine(localId = 3L)
        )

        assertEquals(
            setOf(1L, 2L, 3L),
            MedicineReminderPromptPolicy.defaultSelectedIds(medicines)
        )
    }

    @Test
    fun invalidLocalIdsAreNotSelected() {
        val medicines = listOf(
            medicine(localId = 0L),
            medicine(localId = -4L),
            medicine(localId = 5L)
        )

        assertEquals(
            setOf(5L),
            MedicineReminderPromptPolicy.defaultSelectedIds(medicines)
        )
    }

    @Test
    fun takenRequiresAtLeastOneValidSelection() {
        assertFalse(MedicineReminderPromptPolicy.canConfirmTaken(emptySet()))
        assertFalse(MedicineReminderPromptPolicy.canConfirmTaken(setOf(0L, -1L)))
        assertTrue(MedicineReminderPromptPolicy.canConfirmTaken(setOf(8L)))
    }

    private fun medicine(localId: Long): MedicineItem =
        MedicineItem(
            localId = localId,
            name = "Test medicine $localId",
            notes = null,
            doseText = null,
            summary = null,
            details = null,
            active = true,
            slots = listOf(MedicineSlot.MORNING)
        )
}
