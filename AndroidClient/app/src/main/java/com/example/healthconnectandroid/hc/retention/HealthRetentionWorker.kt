package com.example.healthconnectandroid.hc.retention

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.query.CatalogRefreshWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class HealthRetentionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val serverKey = inputData.getString(KEY_SERVER_KEY) ?: return Result.failure()
        val cutoff = HealthRetentionPolicy.cutoffEpochMillis()
        val db = AppDb.get(applicationContext)
        var removed = 0
        var hasMore = true
        var batches = 0
        while (hasMore && batches < MAX_BATCHES_PER_RUN) {
            val result = try {
                HealthRetentionService(db).archiveUploadedRecords(serverKey, cutoff)
            } catch (exception: CancellationException) {
                throw exception
            } catch (throwable: Throwable) {
                Log.w(TAG, "Retention batch failed serverKey=$serverKey cutoff=$cutoff", throwable)
                return Result.retry()
            }
            removed += result.recordsRemoved
            hasMore = result.hasMore
            batches++
        }
        if (removed > 0) {
            // PASSIVE releases WAL pages without blocking foreground readers.
            db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(PASSIVE)")
            CatalogRefreshWorker.enqueue(applicationContext)
        }
        if (!hasMore) HealthDatabaseCompactionWorker.enqueue(applicationContext)
        return if (hasMore) Result.retry() else Result.success()
    }

    companion object {
        const val WORK_NAME = "health_uploaded_record_retention"
        private const val KEY_SERVER_KEY = "server_key"
        private const val MAX_BATCHES_PER_RUN = 10
        private const val BACKOFF_MINUTES = 10L
        private const val TAG = "HCRetention"

        fun enqueue(context: Context, productionServerKey: String) {
            if (!HealthRetentionPolicy.acceptsServerKey(productionServerKey)) return
            val request = OneTimeWorkRequestBuilder<HealthRetentionWorker>()
                .setInputData(workDataOf(KEY_SERVER_KEY to productionServerKey))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, BACKOFF_MINUTES, TimeUnit.MINUTES)
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
