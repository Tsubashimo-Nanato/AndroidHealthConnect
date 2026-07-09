package com.example.healthconnectandroid.medicine

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.AppPreferences
import com.example.healthconnectandroid.R
import com.example.healthconnectandroid.ui.medicine.translateMedicineUiText

object MedicineReminderNotifier {
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun showReminder(
        context: Context,
        slot: MedicineSlot,
        medicines: List<MedicineItem>
    ) {
        if (medicines.isEmpty()) return

        val strongReminderEnabled = AppPreferences.medicineOverlayReminderEnabled(context)
        val overlayAllowed = MedicineReminderOverlay.canShow(context)
        if (
            MedicineOverlayReminderPolicy.shouldShowOverlay(
                enabled = strongReminderEnabled,
                overlayPermissionGranted = overlayAllowed,
                medicineCount = medicines.size
            ) &&
            MedicineReminderOverlay.show(context, slot, medicines)
        ) {
            return
        }

        if (!canNotify(context)) return
        ensureChannel(context)

        val language = AppPreferences.userPreferences(context).language
        val text = reminderText(slot, medicines.size, language)

        val promptIntent = promptIntent(context, slot)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(translateMedicineUiText("Medicine check", language))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(promptIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (MedicineOverlayReminderPolicy.shouldUseFullScreenFallback(strongReminderEnabled, medicines.size)) {
            builder.setFullScreenIntent(promptIntent, true)
        }

        val notification = builder.build()

        notify(context, slot, notification)
    }

    fun cancel(context: Context, slot: MedicineSlot) {
        NotificationManagerCompat.from(context)
            .cancel(MedicineReminderScheduler.notificationId(slot))
    }

    private fun reminderText(
        slot: MedicineSlot,
        medicineCount: Int,
        language: AppLanguagePreference
    ): String =
        if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
            val slotText = slot.displayLabel(language)
            when (medicineCount) {
                0 -> "请确认是否已服用${slotText}药物。"
                1 -> "请确认是否已服用 1 个${slotText}药物。"
                else -> "请确认是否已服用 $medicineCount 个${slotText}药物。"
            }
        } else {
            val slotText = slot.label.lowercase()
            when (medicineCount) {
                0 -> "Did you take your $slotText medicine?"
                1 -> "Did you take your $slotText medicine? 1 scheduled."
                else -> "Did you take your $slotText medicines? $medicineCount scheduled."
            }
        }

    fun promptIntent(context: Context, slot: MedicineSlot): PendingIntent {
        val intent = Intent(context, MedicineReminderActivity::class.java).apply {
            action = MedicineReminderIntents.ACTION_OPEN_MEDICINE_PROMPT
            putExtra(MedicineReminderIntents.EXTRA_SLOT, slot.id)
        }
        return PendingIntent.getActivity(
            context,
            MedicineReminderScheduler.notificationId(slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Medicine checks urgent",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Asks whether scheduled medicine was taken."
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    private fun notify(
        context: Context,
        slot: MedicineSlot,
        notification: android.app.Notification
    ) {
        if (!canNotify(context)) return
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(MedicineReminderScheduler.notificationId(slot), notification)
        }
    }

    private const val CHANNEL_ID = "medicine_checks_urgent"
}
