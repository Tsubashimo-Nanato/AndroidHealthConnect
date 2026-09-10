package com.example.healthconnectandroid.hc

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.healthconnectandroid.AppPreferences
import com.example.healthconnectandroid.LocalProfile
import com.example.healthconnectandroid.LocalProfileStore
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.sync.HealthSyncService
import com.example.healthconnectandroid.hc.sync.HistoryBackfillBatchResult
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import com.example.healthconnectandroid.hc.query.CatalogRefreshPolicy
import com.example.healthconnectandroid.hc.query.CatalogRefreshWorker
import com.example.healthconnectandroid.hc.upload.HealthUploadWorker
import com.example.healthconnectandroid.hc.upload.UploadAutoQueueDecision
import com.example.healthconnectandroid.hc.upload.UploadAutoQueuePolicy
import com.example.healthconnectandroid.hc.upload.UploadResultSeverity
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PeriodicHealthSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = workerMutex.withLock {
        runSync()
    }

    private suspend fun runSync(): Result {
        val profile = workerProfile() ?: return Result.failure()
        val end = Instant.now()
        Log.i(TAG, "Periodic incremental sync profile=${profile.id} end=$end")

        val db = AppDb.get(applicationContext, profile.id)
        val syncService = HealthSyncService(applicationContext, db)
        val results = try {
            syncService.runPeriodicSmartSync(requireBackgroundReadPermission = true)
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
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

        refreshChangedCatalogTypes(profile.id, results)
        queueAutoUploadIfEnabled(profile.id)
        val backfillStartIndex = PeriodicSyncPreferences.historyBackfillStartIndex(applicationContext)
        val backfill = runHistoryBackfill(syncService, backfillStartIndex)
        PeriodicSyncPreferences.setHistoryBackfillStartIndex(
            applicationContext,
            backfill.nextStartIndex
        )
        val backfillChanged = refreshChangedCatalogTypes(profile.id, backfill.results)
        if (backfillChanged) queueAutoUploadIfEnabled(profile.id, afterCurrent = true)

        val summary = periodicSummary(results) + "; " + historySummary(backfill)
        val errorCount = results.count {
            it.errorMessage != null || it.terminalStatus == SyncRunStatus.TIMEOUT
        }
        val historyErrorCount = backfill.results.count {
            it.errorMessage != null || it.terminalStatus == SyncRunStatus.TIMEOUT
        }
        val status = if (errorCount + historyErrorCount > 0) "partial_error" else "success"
        PeriodicSyncPreferences.markFinished(
            context = applicationContext,
            finishedAt = Instant.now(),
            status = status,
            summary = summary
        )
        Log.i(TAG, "Periodic sync status=$status $summary")

        return if (errorCount > 0) Result.retry() else Result.success()
    }

    private suspend fun runHistoryBackfill(
        syncService: HealthSyncService,
        startIndex: Int
    ): HistoryBackfillBatchResult =
        try {
            syncService.runHistoryBackfillBatch(
                requireBackgroundReadPermission = true,
                startIndex = startIndex
            )
        } catch (throwable: CancellationException) {
            throw throwable
        } catch (throwable: Throwable) {
            Log.w(TAG, "Background history batch failed; the next periodic run will resume it", throwable)
            HistoryBackfillBatchResult(
                results = emptyList(),
                hasMore = true,
                nextStartIndex = startIndex
            )
        }

    private fun workerProfile(): LocalProfile? {
        val requestedId = inputData.getString(KEY_PROFILE_ID)
        if (requestedId != null) {
            return LocalProfileStore.profile(applicationContext, requestedId)
                ?.takeIf { it.ownsHealthConnect }
        }
        // Work created before profile support had no identity. Only that legacy shape may use the owner.
        return runCatching { LocalProfileStore.healthConnectProfile(applicationContext) }.getOrNull()
    }

    private suspend fun refreshChangedCatalogTypes(
        profileId: String,
        results: List<HealthDataTypeSyncResult>
    ): Boolean {
        val changedTypes = CatalogRefreshPolicy.changedRecordTypes(results)
        if (changedTypes.isEmpty()) return false
        AppDb.get(applicationContext, profileId).healthCatalogSnapshotDao().markDirty(changedTypes)
        CatalogRefreshWorker.enqueue(applicationContext, profileId = profileId)
        return true
    }

    private fun queueAutoUploadIfEnabled(profileId: String, afterCurrent: Boolean = false) {
        val settings = AppPreferences.uploadSettings(applicationContext, profileId)
        when (val decision = UploadAutoQueuePolicy.decide(settings)) {
            UploadAutoQueueDecision.Disabled -> Unit
            is UploadAutoQueueDecision.Queue -> {
                if (afterCurrent) {
                    HealthUploadWorker.enqueueAfterCurrent(applicationContext, profileId)
                } else {
                    HealthUploadWorker.enqueue(applicationContext, profileId)
                }
                Log.i(TAG, "Auto upload queued mode=${decision.endpoint.mode}")
            }
            is UploadAutoQueueDecision.Invalid -> {
                val message = "Auto upload not queued: ${decision.reason}"
                AppPreferences.setUploadStatus(
                    applicationContext,
                    AppPreferences.uploadStatus(applicationContext, profileId).copy(
                        connectionResult = message,
                        severity = UploadResultSeverity.WARNING,
                        serverMode = settings.serverMode
                    ),
                    profileId
                )
                Log.w(TAG, message)
            }
        }
    }

    companion object {
        const val WORK_NAME = "health_connect_periodic_sync"
        const val DEFAULT_INTERVAL_HOURS = 1L
        private const val IMMEDIATE_WORK_NAME = "health_connect_foreground_sync"
        private const val BACKOFF_MINUTES = 30L
        private const val TAG = "HCPeriodicSync"
        private const val KEY_PROFILE_ID = "profile_id"

        fun schedule(context: Context) {
            PeriodicSyncPreferences.setEnabled(context, true)
            enqueuePeriodic(context, ExistingPeriodicWorkPolicy.UPDATE)
            enqueueImmediateIfStale(context)
        }

        fun refreshScheduleIfEnabled(context: Context) {
            if (!PeriodicSyncPreferences.isEnabled(context)) return
            val intervalChanged =
                PeriodicSyncPreferences.scheduledIntervalHours(context) != DEFAULT_INTERVAL_HOURS
            enqueuePeriodic(
                context = context,
                policy = if (intervalChanged) {
                    ExistingPeriodicWorkPolicy.UPDATE
                } else {
                    ExistingPeriodicWorkPolicy.KEEP
                }
            )
        }

        private fun enqueuePeriodic(context: Context, policy: ExistingPeriodicWorkPolicy) {
            val profileId = LocalProfileStore.healthConnectProfile(context).id
            val request = PeriodicWorkRequestBuilder<PeriodicHealthSyncWorker>(
                DEFAULT_INTERVAL_HOURS,
                TimeUnit.HOURS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setInputData(workDataOf(KEY_PROFILE_ID to profileId))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WORK_NAME,
                policy,
                request
            )
            PeriodicSyncPreferences.setScheduledIntervalHours(context, DEFAULT_INTERVAL_HOURS)
        }

        fun enqueueImmediateIfStale(context: Context) {
            if (!PeriodicSyncPreferences.shouldRequestImmediate(context)) return
            val profileId = LocalProfileStore.healthConnectProfile(context).id
            val request = OneTimeWorkRequestBuilder<PeriodicHealthSyncWorker>()
                .setInputData(workDataOf(KEY_PROFILE_ID to profileId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .addTag(IMMEDIATE_WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
            PeriodicSyncPreferences.markRequested(context)
        }

        fun cancel(context: Context) {
            suspendSchedule(context)
            PeriodicSyncPreferences.setEnabled(context, false)
        }

        fun suspendSchedule(context: Context) {
            WorkManager.getInstance(context.applicationContext).apply {
                cancelUniqueWork(WORK_NAME)
                cancelUniqueWork(IMMEDIATE_WORK_NAME)
            }
            PeriodicSyncPreferences.setScheduledIntervalHours(context, 0L)
        }

        private fun periodicSummary(results: List<HealthDataTypeSyncResult>): String {
            val read = results.sumOf { it.recordsRead }
            val inserted = results.sumOf { it.recordsInserted }
            val updated = results.sumOf { it.recordsUpdated }
            val deleted = results.sumOf { it.recordsDeleted }
            val duplicates = results.sumOf { it.recordsSkippedDuplicate }
            val summaries = results.sumOf { it.aggregateRowsStored }
            val skipped = results.count { it.skippedReason != null }
            val timeouts = results.count { it.terminalStatus == SyncRunStatus.TIMEOUT }
            val errors = results.count {
                it.errorMessage != null && it.terminalStatus != SyncRunStatus.TIMEOUT
            }
            return "types=${results.size}, read=$read, inserted=$inserted, updated=$updated, deleted=$deleted, " +
                "duplicates=$duplicates, summaries=$summaries, skipped=$skipped, errors=$errors"
                .let { if (timeouts > 0) "$it, timeouts=$timeouts" else it }
        }

        private fun historySummary(batch: HistoryBackfillBatchResult): String {
            val stored = batch.results.sumOf {
                it.recordsInserted + it.recordsUpdated + it.valuesStored + it.aggregateRowsStored
            }
            return "historyTypes=${batch.results.size}, historyStored=$stored, historyPending=${batch.hasMore}"
        }

        private val workerMutex = Mutex()
    }
}
