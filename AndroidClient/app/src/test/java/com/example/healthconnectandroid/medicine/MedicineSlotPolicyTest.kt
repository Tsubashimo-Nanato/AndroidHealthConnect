package com.example.healthconnectandroid.medicine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicineSlotPolicyTest {
    private val zone = ZoneId.of("Asia/Tokyo")

    @Test
    fun parseClockTimeAcceptsStrictHourMinute() {
        assertEquals(8 to 0, MedicineSlotPolicy.parseClockTime("08:00"))
        assertEquals(23 to 59, MedicineSlotPolicy.parseClockTime("23:59"))
    }

    @Test
    fun parseClockTimeRejectsInvalidValues() {
        assertNull(MedicineSlotPolicy.parseClockTime("8"))
        assertNull(MedicineSlotPolicy.parseClockTime("24:00"))
        assertNull(MedicineSlotPolicy.parseClockTime("12:60"))
        assertNull(MedicineSlotPolicy.parseClockTime("noon"))
    }

    @Test
    fun nearestSlotUsesConfiguredReminderTimes() {
        val reminders = MedicineSlotPolicy.defaultReminderTimes

        assertEquals(
            MedicineSlot.MORNING,
            MedicineSlotPolicy.nearestSlot(instant("2026-07-05T07:45:00+09:00"), zone, reminders)
        )
        assertEquals(
            MedicineSlot.MIDDAY,
            MedicineSlotPolicy.nearestSlot(instant("2026-07-05T13:00:00+09:00"), zone, reminders)
        )
        assertEquals(
            MedicineSlot.BEDTIME,
            MedicineSlotPolicy.nearestSlot(instant("2026-07-05T23:20:00+09:00"), zone, reminders)
        )
    }

    @Test
    fun nextReminderUsesTomorrowWhenTodayAlreadyPassed() {
        val next = MedicineSlotPolicy.nextReminderInstant(
            slot = MedicineSlot.MORNING,
            now = instant("2026-07-05T09:00:00+09:00"),
            zoneId = zone,
            reminderTimes = MedicineSlotPolicy.defaultReminderTimes
        )

        assertEquals(
            LocalDate.of(2026, 7, 6),
            next?.atZone(zone)?.toLocalDate()
        )
        assertEquals(8, next?.atZone(zone)?.hour)
        assertEquals(0, next?.atZone(zone)?.minute)
    }

    @Test
    fun asNeededDoesNotCreateScheduledReminder() {
        assertNull(
            MedicineSlotPolicy.nextReminderInstant(
                slot = MedicineSlot.AS_NEEDED,
                now = instant("2026-07-05T09:00:00+09:00"),
                zoneId = zone,
                reminderTimes = MedicineSlotPolicy.defaultReminderTimes
            )
        )
    }

    private fun instant(value: String): Instant =
        java.time.OffsetDateTime.parse(value).toInstant()
}
