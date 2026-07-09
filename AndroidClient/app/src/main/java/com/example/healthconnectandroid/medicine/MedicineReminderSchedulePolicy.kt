package com.example.healthconnectandroid.medicine

import java.time.Instant
import java.time.ZoneId

enum class MedicineReminderScheduleMode {
    Off,
    Work,
    Alarm
}

data class MedicineReminderSchedulePlan(
    val slot: MedicineSlot,
    val mode: MedicineReminderScheduleMode,
    val nextReminder: Instant?
)

object MedicineReminderSchedulePolicy {
    fun plan(
        slot: MedicineSlot,
        activeScheduleCount: Int,
        reminderTimes: Map<MedicineSlot, MedicineReminderTime>,
        now: Instant,
        zoneId: ZoneId
    ): MedicineReminderSchedulePlan {
        if (!slot.supportsReminder || activeScheduleCount <= 0) {
            return MedicineReminderSchedulePlan(slot, MedicineReminderScheduleMode.Off, nextReminder = null)
        }

        val reminder = MedicineSlotPolicy.reminderTime(slot, reminderTimes)
            ?: return MedicineReminderSchedulePlan(slot, MedicineReminderScheduleMode.Off, nextReminder = null)
        val next = MedicineSlotPolicy.nextReminderInstant(slot, now, zoneId, reminderTimes)
            ?: return MedicineReminderSchedulePlan(slot, MedicineReminderScheduleMode.Off, nextReminder = null)
        val mode = if (reminder.alarmEnabled) {
            MedicineReminderScheduleMode.Alarm
        } else {
            MedicineReminderScheduleMode.Work
        }
        return MedicineReminderSchedulePlan(slot, mode, next)
    }
}
