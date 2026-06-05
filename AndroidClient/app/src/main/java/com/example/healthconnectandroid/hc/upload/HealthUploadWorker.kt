package com.example.healthconnectandroid.hc.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.healthconnectandroid.AppPreferences
import com.example.healthconnectandroid.data.AppDb
import java.util.concurrent.TimeUnit

class HealthUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = AppPreferences.uploadSettings(applicationContext)
        val service = HealthUploadService(AppDb.get(applicationContext))
        val result = service.uploadPending(
            settings = settings,
            maxBatches = HealthUploadService.BACKGROUND_MAX_BATCHES_PER_RUN
        ) { progress ->
            setProgress(
                workDataOf(
                    KEY_PHASE to progress.phase,
                    KEY_BATCH to progress.currentBatch,
                    KEY_UPLOADED to progress.uploadedItems,
                    KEY_TOTAL to progress.totalPendingItems,
                    KEY_TYPE to (progress.currentType ?: "")
                )
            )
        }
        AppPreferences.setUploadStatus(applicationContext, result.toStatus())
        return when {
            result.success -> Result.success()
            result.retryable -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val WORK_NAME = "health_connect_upload_pending"
        const val KEY_PHASE = "phase"
        const val KEY_BATCH = "batch"
        const val KEY_UPLOADED = "uploaded"
        const val KEY_TOTAL = "total"
        const val KEY_TYPE = "type"
        private const val BACKOFF_MINUTES = 15L

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<HealthUploadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
