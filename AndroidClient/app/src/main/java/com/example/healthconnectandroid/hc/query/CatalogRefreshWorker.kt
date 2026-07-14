package com.example.healthconnectandroid.hc.query

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class CatalogRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val zoneId = inputData.getString(KEY_ZONE_ID)
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val db = AppDb.get(applicationContext)
        val snapshots = db.healthCatalogSnapshotDao().all()
        val recordTypes = CatalogRefreshPolicy.recordTypesToRefresh(
            snapshots = snapshots,
            recordTypes = HealthDataTypeRegistry.descriptors.map { it.key },
            localDate = LocalDate.now(zoneId).toString(),
            zoneId = zoneId.id,
            now = Instant.now()
        )
        if (recordTypes.isEmpty()) return Result.success()

        val refresh = CatalogSnapshotRefresher(db).refresh(recordTypes, zoneId)
        return if (refresh.failedTypes.isEmpty()) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "health_catalog_progressive_refresh"
        private const val KEY_ZONE_ID = "zone_id"

        fun enqueue(context: Context, zoneId: ZoneId = ZoneId.systemDefault()) {
            val request = OneTimeWorkRequestBuilder<CatalogRefreshWorker>()
                .setInputData(workDataOf(KEY_ZONE_ID to zoneId.id))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }
    }
}
