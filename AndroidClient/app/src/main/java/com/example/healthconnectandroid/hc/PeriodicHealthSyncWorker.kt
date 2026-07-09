package com.example.healthconnectandroid.hc

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.healthconnectandroid.AppPreferences
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.sync.HealthSyncService
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import com.example.healthconnectandroid.hc.upload.HealthUploadWorker
import com.example.healthconnectandroid.hc.upload.UploadAutoQueueDecision
import com.example.healthconnectandroid.hc.upload.UploadAutoQueuePolicy
import com.example.healthconnectandroid.hc.upload.UploadResultSeverity
import java.time.Instant
import java.util.concurrent.TimeUnit

class PeriodicHealthSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val end = Instant.now()
        Log.i(TAG, "Periodic smart sync end=$end")

        val results = runCatching {
            HealthSyncService(applicationContext, AppDb.get(applicationContext))
                .runPeriodicSmartSync(
                    requireBackgroundReadPermission = true
                )
        }.getOrElse { throwable ->
            Log.e(TAG, "Periodic sync failed before per-type sync", throwable)
            val summary = "Periodic sync failed: ${throwable.message ?: throwable.javaClass.simpleName}"
            PeriodicSyncPreferences.markFinished(
                context = applicationContext,
                finishedAt = Instant.now(),
                status = "error",
                summary = summary
            )
            return Result.retry()
        }

        val summary = periodicSummary(results)
        val errorCount = results.count {
            it.errorMessage != null || it.terminalStatus == SyncRunStatus.TIMEOUT
        }
        val status = if (errorCount > 0) "partial_error" else "success"
        PeriodicSyncPreferences.markFinished(
            context = applicationContext,
            finishedAt = Instant.now(),
            status = status,
            summary = summary
        )
        Log.i(TAG, "Periodic sync status=$status $summary")
        queueAutoUploadIfEnabled()

        return if (errorCount > 0) Result.retry() else Result.success()
    }

    private fun queueAutoUploadIfEnabled() {
        val settings = AppPreferences.uploadSettings(applicationContext)
        when (val decision = UploadAutoQueuePolicy.decide(settings)) {
            UploadAutoQueueDecision.Disabled -> Unit
            is UploadAutoQueueDecision.Queue -> {
                HealthUploadWorker.enqueue(applicationContext)
                Log.i(TAG, "Auto upload queued mode=${decision.endpoint.mode}")
            }
            is UploadAutoQueueDecision.Invalid -> {
                val message = "Auto upload not queued: ${decision.reason}"
                AppPreferences.setUploadStatus(
                    applicationContext,
                    AppPreferences.uploadStatus(applicationContext).copy(
                        connectionResult = message,
                        severity = UploadResultSeverity.WARNING,
                        serverMode = settings.serverMode
                    )
                )
                Log.w(TAG, message)
            }
        }
    }

    companion object {
        const val WORK_NAME = "health_connect_periodic_sync"
        const val DEFAULT_INTERVAL_HOURS = 6L
        private const val BACKOFF_MINUTES = 30L
        private const val TAG = "HCPeriodicSync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PeriodicHealthSyncWorker>(
                DEFAULT_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            PeriodicSyncPreferences.setEnabled(context, true)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            PeriodicSyncPreferences.setEnabled(context, false)
        }

        private fun periodicSummary(results: List<HealthDataTypeSyncResult>): String {
            val read = results.sumOf { it.recordsRead }
            val inserted = results.sumOf { it.recordsInserted }
            val updated = results.sumOf { it.recordsUpdated }
            val duplicates = results.sumOf { it.recordsSkippedDuplicate }
            val summaries = results.sumOf { it.aggregateRowsStored }
            val skipped = results.count { it.skippedReason != null }
            val timeouts = results.count { it.terminalStatus == SyncRunStatus.TIMEOUT }
            val errors = results.count {
                it.errorMessage != null && it.terminalStatus != SyncRunStatus.TIMEOUT
            }
            return "types=${results.size}, read=$read, inserted=$inserted, updated=$updated, " +
                "duplicates=$duplicates, summaries=$summaries, skipped=$skipped, errors=$errors"
                .let { if (timeouts > 0) "$it, timeouts=$timeouts" else it }
        }
    }
}
