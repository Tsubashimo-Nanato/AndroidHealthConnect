package com.example.healthconnectandroid.medicine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.healthconnectandroid.data.AppDb
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedicineReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleAction(appContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handleAction(context: Context, intent: Intent) {
        val slot = intent.getStringExtra(MedicineReminderIntents.EXTRA_SLOT)
            ?.let(MedicineSlot::fromId)
            ?: return
        val status = when (intent.action) {
            MedicineReminderIntents.ACTION_MARK_TAKEN -> MedicineDoseStatus.TAKEN
            MedicineReminderIntents.ACTION_MARK_MISSED -> MedicineDoseStatus.MISSED
            else -> return
        }
        val result = MedicineRepository(AppDb.get(context)).logReminderResponse(
            slot = slot,
            status = status,
            zoneId = ZoneId.systemDefault()
        )
        MedicineReminderNotifier.cancel(context, slot)
        Log.i(
            TAG,
            "Medicine reminder response slot=${slot.id} status=${status.id} changed=${result.changedRows}"
        )
    }

    private companion object {
        const val TAG = "MedicineReminder"
    }
}
