package com.example.healthconnectandroid.medicine

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object MedicineSlotPolicy {
    val defaultReminderTimes: Map<MedicineSlot, MedicineReminderTime> = mapOf(
        MedicineSlot.MORNING to MedicineReminderTime(MedicineSlot.MORNING, hour = 8, minute = 0, enabled = true),
        MedicineSlot.MIDDAY to MedicineReminderTime(MedicineSlot.MIDDAY, hour = 12, minute = 30, enabled = true),
        MedicineSlot.EVENING to MedicineReminderTime(MedicineSlot.EVENING, hour = 18, minute = 0, enabled = true),
        MedicineSlot.BEDTIME to MedicineReminderTime(MedicineSlot.BEDTIME, hour = 22, minute = 0, enabled = true)
    )

    fun reminderTime(slot: MedicineSlot, overrides: Map<MedicineSlot, MedicineReminderTime>): MedicineReminderTime? {
        if (!slot.supportsReminder) return null
        return overrides[slot] ?: defaultReminderTimes[slot]
    }

    fun parseClockTime(raw: String): Pair<Int, Int>? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    fun formatClockTime(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour, minute)

    fun nearestSlot(
        now: Instant,
        zoneId: ZoneId,
        reminderTimes: Map<MedicineSlot, MedicineReminderTime>
    ): MedicineSlot {
        val minuteOfDay = now.atZone(zoneId).toLocalTime().toSecondOfDay() / 60
        return MedicineSlot.scheduledSlots.minByOrNull { slot ->
            val reminder = reminderTime(slot, reminderTimes)
            if (reminder == null) {
                Int.MAX_VALUE
            } else {
                circularMinuteDistance(
                    minuteOfDay,
                    reminder.hour * 60 + reminder.minute
                )
            }
        } ?: MedicineSlot.MORNING
    }

    fun scheduledInstant(
        slot: MedicineSlot,
        localDate: LocalDate,
        zoneId: ZoneId,
        reminderTimes: Map<MedicineSlot, MedicineReminderTime>
    ): Instant? {
        val reminder = reminderTime(slot, reminderTimes) ?: return null
        return localDate
            .atTime(LocalTime.of(reminder.hour, reminder.minute))
            .atZone(zoneId)
            .toInstant()
    }

    fun nextReminderInstant(
        slot: MedicineSlot,
        now: Instant,
        zoneId: ZoneId,
        reminderTimes: Map<MedicineSlot, MedicineReminderTime>
    ): Instant? {
        val reminder = reminderTime(slot, reminderTimes)?.takeIf { it.enabled } ?: return null
        val today = now.atZone(zoneId).toLocalDate()
        val todayReminder = today
            .atTime(LocalTime.of(reminder.hour, reminder.minute))
            .atZone(zoneId)
            .toInstant()
        return if (todayReminder.isAfter(now.plus(1, ChronoUnit.MINUTES))) {
            todayReminder
        } else {
            today.plusDays(1)
                .atTime(LocalTime.of(reminder.hour, reminder.minute))
                .atZone(zoneId)
                .toInstant()
        }
    }

    private fun circularMinuteDistance(left: Int, right: Int): Int {
        val direct = abs(left - right)
        return minOf(direct, MINUTES_PER_DAY - direct)
    }

    private const val MINUTES_PER_DAY = 24 * 60
}
