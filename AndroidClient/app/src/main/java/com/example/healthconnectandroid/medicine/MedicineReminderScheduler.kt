package com.example.healthconnectandroid.medicine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        val plan = MedicineReminderSchedulePolicy.plan(
            slot = slot,
            activeScheduleCount = repository.activeScheduleCount(slot),
            reminderTimes = repository.reminderTimes(),
            now = now,
            zoneId = zoneId
        )
        when (plan.mode) {
            MedicineReminderScheduleMode.Off -> cancelSlot(context, slot)
            MedicineReminderScheduleMode.Work -> {
                cancelAlarmSlot(context, slot)
                scheduleWork(context, slot, zoneId, now, plan.nextReminder ?: return)
            }
            MedicineReminderScheduleMode.Alarm -> {
                cancelWorkSlot(context, slot)
                scheduleAlarm(context, slot, plan.nextReminder ?: return)
            }
        }
    }

    private fun scheduleWork(
        context: Context,
        slot: MedicineSlot,
        zoneId: ZoneId,
        now: Instant,
        next: Instant
    ) {
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
        cancelWorkSlot(context, slot)
        cancelAlarmSlot(context, slot)
    }

    private fun cancelWorkSlot(context: Context, slot: MedicineSlot) {
        WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(slotTag(slot))
    }

    private fun scheduleAlarm(context: Context, slot: MedicineSlot, next: Instant) {
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        val operation = alarmOperation(
            context = context,
            slot = slot,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        // AlarmClock is intentionally user-visible and does not depend on exact-alarm permission.
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(
                next.toEpochMilli(),
                MedicineReminderNotifier.promptIntent(context, slot)
            ),
            operation
        )
    }

    private fun cancelAlarmSlot(context: Context, slot: MedicineSlot) {
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        val operation = alarmOperation(
            context = context,
            slot = slot,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    fun notificationId(slot: MedicineSlot): Int =
        NOTIFICATION_BASE_ID + slot.ordinal

    private fun alarmOperation(context: Context, slot: MedicineSlot, flags: Int): PendingIntent? {
        val intent = Intent(context, MedicineReminderAlarmReceiver::class.java).apply {
            action = MedicineReminderIntents.ACTION_MEDICINE_ALARM
            putExtra(MedicineReminderIntents.EXTRA_SLOT, slot.id)
        }
        return PendingIntent.getBroadcast(context, alarmRequestCode(slot), intent, flags)
    }

    private fun alarmRequestCode(slot: MedicineSlot): Int =
        NOTIFICATION_BASE_ID + 300 + slot.ordinal

    private fun slotTag(slot: MedicineSlot): String =
        "$WORK_TAG_PREFIX${slot.id}"

    private fun workName(slot: MedicineSlot, localDate: String): String =
        "$WORK_NAME_PREFIX${slot.id}_$localDate"

    private const val WORK_TAG_PREFIX = "medicine_reminder_slot_"
    private const val WORK_NAME_PREFIX = "medicine_reminder_"
    private const val NOTIFICATION_BASE_ID = 41_300
}
