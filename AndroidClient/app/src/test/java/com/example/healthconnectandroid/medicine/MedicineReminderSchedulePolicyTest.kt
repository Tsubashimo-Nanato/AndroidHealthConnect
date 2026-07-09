package com.example.healthconnectandroid.medicine

import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MedicineReminderSchedulePolicyTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val now = OffsetDateTime.parse("2026-07-05T07:00:00+09:00").toInstant()

    @Test
    fun disabledSlotCancelsWorkAndAlarm() {
        val plan = MedicineReminderSchedulePolicy.plan(
            slot = MedicineSlot.MORNING,
            activeScheduleCount = 2,
            reminderTimes = reminders(MedicineSlot.MORNING, enabled = false, alarmEnabled = true),
            now = now,
            zoneId = zone
        )

        assertEquals(MedicineReminderScheduleMode.Off, plan.mode)
        assertNull(plan.nextReminder)
    }

    @Test
    fun emptyScheduleCancelsWorkAndAlarm() {
        val plan = MedicineReminderSchedulePolicy.plan(
            slot = MedicineSlot.MORNING,
            activeScheduleCount = 0,
            reminderTimes = reminders(MedicineSlot.MORNING, enabled = true, alarmEnabled = true),
            now = now,
            zoneId = zone
        )

        assertEquals(MedicineReminderScheduleMode.Off, plan.mode)
        assertNull(plan.nextReminder)
    }

    @Test
    fun enabledReminderWithoutAlarmUsesWorkManager() {
        val plan = MedicineReminderSchedulePolicy.plan(
            slot = MedicineSlot.MORNING,
            activeScheduleCount = 1,
            reminderTimes = reminders(MedicineSlot.MORNING, enabled = true, alarmEnabled = false),
            now = now,
            zoneId = zone
        )

        assertEquals(MedicineReminderScheduleMode.Work, plan.mode)
        assertNotNull(plan.nextReminder)
    }

    @Test
    fun alarmModeUsesAlarmPath() {
        val plan = MedicineReminderSchedulePolicy.plan(
            slot = MedicineSlot.MORNING,
            activeScheduleCount = 1,
            reminderTimes = reminders(MedicineSlot.MORNING, enabled = true, alarmEnabled = true),
            now = now,
            zoneId = zone
        )

        assertEquals(MedicineReminderScheduleMode.Alarm, plan.mode)
        assertNotNull(plan.nextReminder)
    }

    @Test
    fun asNeededDoesNotSchedule() {
        val plan = MedicineReminderSchedulePolicy.plan(
            slot = MedicineSlot.AS_NEEDED,
            activeScheduleCount = 1,
            reminderTimes = reminders(MedicineSlot.MORNING, enabled = true, alarmEnabled = true),
            now = now,
            zoneId = zone
        )

        assertEquals(MedicineReminderScheduleMode.Off, plan.mode)
        assertNull(plan.nextReminder)
    }

    private fun reminders(
        slot: MedicineSlot,
        enabled: Boolean,
        alarmEnabled: Boolean
    ): Map<MedicineSlot, MedicineReminderTime> =
        MedicineSlotPolicy.defaultReminderTimes + mapOf(
            slot to MedicineReminderTime(
                slot = slot,
                hour = 8,
                minute = 0,
                enabled = enabled,
                alarmEnabled = alarmEnabled
            )
        )
}
