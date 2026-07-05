package com.example.healthconnectandroid.medicine

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object MedicineReminderScheduler {
    suspend fun scheduleAll(
        context: Context,
        repository: MedicineRepository,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ) {
        for (slot in MedicineSlot.scheduledSlots) {
            scheduleSlot(context, repository, slot, zoneId, now)
        }
    }

    suspend fun scheduleSlot(
        context: Context,
        repository: MedicineRepository,
        slot: MedicineSlot,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ) {
        if (!slot.supportsReminder || repository.activeScheduleCount(slot) == 0) {
            cancelSlot(context, slot)
            return
        }
        val next = MedicineSlotPolicy.nextReminderInstant(slot, now, zoneId, repository.reminderTimes())
        if (next == null) {
            cancelSlot(context, slot)
            return
        }

        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(0L)
        val localDate = next.atZone(zoneId).toLocalDate()
        val request = OneTimeWorkRequestBuilder<MedicineReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(MedicineReminderWorker.KEY_SLOT to slot.id))
            .addTag(slotTag(slot))
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(slot, localDate.toString()),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelSlot(context: Context, slot: MedicineSlot) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(slotTag(slot))
    }

    fun notificationId(slot: MedicineSlot): Int =
        NOTIFICATION_BASE_ID + slot.ordinal

    private fun slotTag(slot: MedicineSlot): String =
        "$WORK_TAG_PREFIX${slot.id}"

    private fun workName(slot: MedicineSlot, localDate: String): String =
        "$WORK_NAME_PREFIX${slot.id}_$localDate"

    private const val WORK_TAG_PREFIX = "medicine_reminder_slot_"
    private const val WORK_NAME_PREFIX = "medicine_reminder_"
    private const val NOTIFICATION_BASE_ID = 41_300
}
