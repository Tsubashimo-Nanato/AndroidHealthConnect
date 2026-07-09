package com.example.healthconnectandroid.medicine

object MedicineReminderPromptPolicy {
    fun defaultSelectedIds(medicines: List<MedicineItem>): Set<Long> =
        medicines.asSequence()
            .map { it.localId }
            .filter { it > 0L }
            .toSet()

    fun canConfirmTaken(selectedMedicineIds: Set<Long>): Boolean =
        selectedMedicineIds.any { it > 0L }
}
