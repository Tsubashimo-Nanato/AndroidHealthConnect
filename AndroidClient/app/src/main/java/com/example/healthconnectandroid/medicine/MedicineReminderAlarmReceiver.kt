package com.example.healthconnectandroid.medicine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.healthconnectandroid.data.AppDb
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicineReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAlarm(appContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleAlarm(context: Context, intent: Intent) {
        if (intent.action != MedicineReminderIntents.ACTION_MEDICINE_ALARM) return
        val slot = intent.getStringExtra(MedicineReminderIntents.EXTRA_SLOT)
            ?.let(MedicineSlot::fromId)
            ?.takeIf { it.supportsReminder }
            ?: return
        val repository = MedicineRepository(AppDb.get(context))
        val medicines = repository.activeScheduledMedicines(slot)
        if (medicines.isNotEmpty()) {
            MedicineReminderNotifier.showReminder(context, slot, medicines)
            Log.i(TAG, "Medicine alarm reminder shown slot=${slot.id} count=${medicines.size}")
        }
        MedicineReminderScheduler.scheduleSlot(
            context = context,
            repository = repository,
            slot = slot,
            zoneId = ZoneId.systemDefault(),
            now = Instant.now()
        )
    }

    private companion object {
        const val TAG = "MedicineReminder"
    }
}
