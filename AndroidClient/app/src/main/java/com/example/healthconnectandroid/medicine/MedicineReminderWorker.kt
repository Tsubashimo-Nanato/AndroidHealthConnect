package com.example.healthconnectandroid.medicine

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.healthconnectandroid.data.AppDb
import java.time.Instant
import java.time.ZoneId

class MedicineReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val slot = inputData.getString(KEY_SLOT)
            ?.let(MedicineSlot::fromId)
            ?.takeIf { it.supportsReminder }
            ?: return Result.failure()
        val repository = MedicineRepository(AppDb.get(applicationContext))
        val medicines = repository.activeScheduledMedicines(slot)
        if (medicines.isNotEmpty()) {
            MedicineReminderNotifier.showReminder(applicationContext, slot, medicines)
            Log.i(TAG, "Medicine reminder shown slot=${slot.id} count=${medicines.size}")
        }
        MedicineReminderScheduler.scheduleSlot(
            context = applicationContext,
            repository = repository,
            slot = slot,
            zoneId = ZoneId.systemDefault(),
            now = Instant.now()
        )
        return Result.success()
    }

    companion object {
        const val KEY_SLOT = "slot"
        private const val TAG = "MedicineReminder"
    }
}
