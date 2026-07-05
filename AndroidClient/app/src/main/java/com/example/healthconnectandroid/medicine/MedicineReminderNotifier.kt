package com.example.healthconnectandroid.medicine

import android.annotation.SuppressLint
import android.Manifest
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
import com.example.healthconnectandroid.MainActivity
import com.example.healthconnectandroid.R

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
        medicineNames: List<String>
    ) {
        if (!canNotify(context)) return
        ensureChannel(context)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Medicine check")
            .setContentText(reminderText(slot, medicineNames))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminderText(slot, medicineNames)))
            .setContentIntent(openMedicineIntent(context, slot))
            .setAutoCancel(true)
            .addAction(
                android.R.drawable.checkbox_on_background,
                "Taken",
                responseIntent(context, slot, MedicineReminderIntents.ACTION_MARK_TAKEN)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "No",
                responseIntent(context, slot, MedicineReminderIntents.ACTION_MARK_MISSED)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notify(context, slot, notification)
    }

    fun cancel(context: Context, slot: MedicineSlot) {
        NotificationManagerCompat.from(context)
            .cancel(MedicineReminderScheduler.notificationId(slot))
    }

    private fun reminderText(slot: MedicineSlot, medicineNames: List<String>): String =
        when (medicineNames.size) {
            0 -> "Did you take your ${slot.label.lowercase()} medicine?"
            1 -> "Did you take ${medicineNames.first()}?"
            else -> "Did you take your ${slot.label.lowercase()} medicines? ${medicineNames.joinToString(", ")}"
        }

    private fun openMedicineIntent(context: Context, slot: MedicineSlot): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MedicineReminderIntents.ACTION_OPEN_MEDICINE
            putExtra(MedicineReminderIntents.EXTRA_SLOT, slot.id)
        }
        return PendingIntent.getActivity(
            context,
            MedicineReminderScheduler.notificationId(slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun responseIntent(context: Context, slot: MedicineSlot, action: String): PendingIntent {
        val intent = Intent(context, MedicineReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra(MedicineReminderIntents.EXTRA_SLOT, slot.id)
        }
        val requestCode = MedicineReminderScheduler.notificationId(slot) +
            if (action == MedicineReminderIntents.ACTION_MARK_TAKEN) 100 else 200
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Medicine checks",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Asks whether scheduled medicine was taken."
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

    private const val CHANNEL_ID = "medicine_checks"
}
