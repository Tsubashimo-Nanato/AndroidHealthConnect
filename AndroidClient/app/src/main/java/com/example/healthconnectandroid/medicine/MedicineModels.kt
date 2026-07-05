package com.example.healthconnectandroid.medicine

import java.time.LocalDate

enum class MedicineSlot(val id: String, val label: String, val supportsReminder: Boolean) {
    MORNING("morning", "Morning", true),
    MIDDAY("midday", "Midday", true),
    EVENING("evening", "Evening", true),
    BEDTIME("bedtime", "Bedtime", true),
    AS_NEEDED("as_needed", "As needed", false);

    companion object {
        val scheduledSlots: List<MedicineSlot> = entries.filter { it.supportsReminder }

        fun fromId(id: String): MedicineSlot? = entries.firstOrNull { it.id == id }
    }
}

enum class MedicineDoseStatus(val id: String, val label: String) {
    TAKEN("taken", "Taken"),
    MISSED("missed", "Missed"),
    SKIPPED("skipped", "Skipped");

    companion object {
        fun fromId(id: String): MedicineDoseStatus =
            entries.firstOrNull { it.id == id } ?: MISSED
    }
}

enum class MedicineLogSource(val id: String) {
    MANUAL("manual"),
    REMINDER("reminder");

    companion object {
        fun fromId(id: String): MedicineLogSource =
            entries.firstOrNull { it.id == id } ?: MANUAL
    }
}

data class MedicineReminderTime(
    val slot: MedicineSlot,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean
) {
    val label: String
        get() = "%02d:%02d".format(hour, minute)
}

data class MedicineItem(
    val localId: Long,
    val name: String,
    val notes: String?,
    val doseText: String?,
    val summary: String?,
    val details: String?,
    val active: Boolean,
    val slots: List<MedicineSlot>
)

data class MedicineDoseLog(
    val localId: Long,
    val medicineLocalId: Long?,
    val medicineName: String,
    val slot: MedicineSlot,
    val localDate: LocalDate,
    val scheduledEpochMillis: Long?,
    val recordedEpochMillis: Long,
    val status: MedicineDoseStatus,
    val source: MedicineLogSource,
    val note: String?
)

data class MedicineSnapshot(
    val medicines: List<MedicineItem>,
    val schedules: Map<MedicineSlot, List<MedicineItem>>,
    val reminderTimes: Map<MedicineSlot, MedicineReminderTime>,
    val todayLogs: List<MedicineDoseLog>,
    val yesterdayLogs: List<MedicineDoseLog>
)

val EmptyMedicineSnapshot = MedicineSnapshot(
    medicines = emptyList(),
    schedules = emptyMap(),
    reminderTimes = MedicineSlotPolicy.defaultReminderTimes,
    todayLogs = emptyList(),
    yesterdayLogs = emptyList()
)
