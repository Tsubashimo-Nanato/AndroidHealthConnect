package com.example.healthconnectandroid.medicine

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.healthconnectandroid.LocalProfileStore
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object MedicineReminderScheduler {
    suspend fun scheduleAll(
        context: Context,
        repository: MedicineRepository,
        profileId: String = LocalProfileStore.activeProfile(context).id,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now()
    ) {
        for (slot in MedicineSlot.scheduledSlots) {
            scheduleSlot(context, repository, slot, profileId, zoneId, now)
        }
    }

    suspend fun scheduleSlot(
        context: Context,
        repository: MedicineRepository,
        slot: MedicineSlot,
        profileId: String = LocalProfileStore.activeProfile(context).id,
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
        val exactAlarmAccessGranted = canScheduleExactAlarms(context)
        when (MedicineExactAlarmPolicy.resolveMode(plan.mode, exactAlarmAccessGranted)) {
            MedicineReminderScheduleMode.Off -> cancelSlot(context, slot, profileId)
            MedicineReminderScheduleMode.Work -> {
                cancelAlarmSlot(context, slot, profileId)
                scheduleWork(context, slot, profileId, zoneId, now, plan.nextReminder ?: return)
            }
            MedicineReminderScheduleMode.Alarm -> {
                val next = plan.nextReminder ?: return
                if (scheduleAlarm(context, slot, profileId, next)) {
                    cancelWorkSlot(context, slot, profileId)
                } else {
                    cancelAlarmSlot(context, slot, profileId)
                    scheduleWork(context, slot, profileId, zoneId, now, next)
                }
            }
        }
    }

    private fun scheduleWork(
        context: Context,
        slot: MedicineSlot,
        profileId: String,
        zoneId: ZoneId,
        now: Instant,
        next: Instant
    ) {
        val delayMillis = Duration.between(now, next).toMillis().coerceAtLeast(0L)
        val localDate = next.atZone(zoneId).toLocalDate()
        val request = OneTimeWorkRequestBuilder<MedicineReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    MedicineReminderWorker.KEY_SLOT to slot.id,
                    MedicineReminderWorker.KEY_PROFILE_ID to profileId
                )
            )
            .addTag(slotTag(slot, profileId))
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(slot, profileId, localDate.toString()),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelSlot(
        context: Context,
        slot: MedicineSlot,
        profileId: String = LocalProfileStore.activeProfile(context).id
    ) {
        cancelWorkSlot(context, slot, profileId)
        cancelAlarmSlot(context, slot, profileId)
    }

    private fun cancelWorkSlot(context: Context, slot: MedicineSlot, profileId: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelAllWorkByTag(slotTag(slot, profileId))
    }

    private fun scheduleAlarm(
        context: Context,
        slot: MedicineSlot,
        profileId: String,
        next: Instant
    ): Boolean {
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
            ?: return false
        if (!canScheduleExactAlarms(alarmManager)) return false
        val operation = alarmOperation(
            context = context,
            slot = slot,
            profileId = profileId,
            flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: return false
        return try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(
                    next.toEpochMilli(),
                    MedicineReminderNotifier.promptIntent(context, slot, profileId)
                ),
                operation
            )
            true
        } catch (error: SecurityException) {
            // Exact-alarm access can be revoked after the settings screen closes.
            Log.w(TAG, "Exact medicine alarm denied; using background reminder", error)
            false
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java)
            ?: return false
        return canScheduleExactAlarms(alarmManager)
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        val specialAccessGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            false
        }
        return MedicineExactAlarmPolicy.canScheduleExactAlarm(
            sdkInt = Build.VERSION.SDK_INT,
            specialAccessGranted = specialAccessGranted
        )
    }

    private fun cancelAlarmSlot(context: Context, slot: MedicineSlot, profileId: String) {
        val alarmManager = context.applicationContext.getSystemService(AlarmManager::class.java) ?: return
        val operation = alarmOperation(
            context = context,
            slot = slot,
            profileId = profileId,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(operation)
        operation.cancel()
    }

    fun notificationId(profileId: String, slot: MedicineSlot): Int =
        NOTIFICATION_BASE_ID + profileOffset(profileId) + slot.ordinal

    private fun alarmOperation(
        context: Context,
        slot: MedicineSlot,
        profileId: String,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(context, MedicineReminderAlarmReceiver::class.java).apply {
            action = MedicineReminderIntents.ACTION_MEDICINE_ALARM
            putExtra(MedicineReminderIntents.EXTRA_SLOT, slot.id)
            putExtra(MedicineReminderIntents.EXTRA_PROFILE_ID, profileId)
        }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode(slot, profileId),
            intent,
            flags
        )
    }

    private fun alarmRequestCode(slot: MedicineSlot, profileId: String): Int =
        NOTIFICATION_BASE_ID + 300 + profileOffset(profileId) + slot.ordinal

    private fun slotTag(slot: MedicineSlot, profileId: String): String =
        "$WORK_TAG_PREFIX${profileId}_${slot.id}"

    private fun workName(slot: MedicineSlot, profileId: String, localDate: String): String =
        "$WORK_NAME_PREFIX${profileId}_${slot.id}_$localDate"

    private fun profileOffset(profileId: String): Int =
        (profileId.hashCode() and 0x003f_ffff) * 10

    private const val WORK_TAG_PREFIX = "medicine_reminder_slot_"
    private const val WORK_NAME_PREFIX = "medicine_reminder_"
    private const val NOTIFICATION_BASE_ID = 41_300
    private const val TAG = "MedicineReminder"
}
