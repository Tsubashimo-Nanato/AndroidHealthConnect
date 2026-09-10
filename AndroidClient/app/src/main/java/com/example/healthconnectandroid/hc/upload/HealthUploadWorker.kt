package com.example.healthconnectandroid.hc.upload

import android.content.Context
import android.util.Log
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
import com.example.healthconnectandroid.LocalProfile
import com.example.healthconnectandroid.LocalProfileStore
import com.example.healthconnectandroid.UploadSettingsLoadResult
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.retention.HealthRetentionWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HealthUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = workerMutex.withLock {
        runUpload()
    }

    private suspend fun runUpload(): Result {
        val profile = workerProfile() ?: return Result.failure()
        val settingsLoad = AppPreferences.loadUploadSettings(applicationContext, profile.id)
        if (settingsLoad is UploadSettingsLoadResult.SecureStorageUnavailable) {
            AppPreferences.setUploadStatus(
                applicationContext,
                AppPreferences.uploadStatus(applicationContext, profile.id).copy(
                    lastResult = "Upload deferred: secure settings are temporarily unavailable",
                    severity = UploadResultSeverity.WARNING
                ),
                profile.id
            )
            return Result.retry()
        }
        if (settingsLoad is UploadSettingsLoadResult.ReentryRequired) {
            AppPreferences.setUploadStatus(
                applicationContext,
                AppPreferences.uploadStatus(applicationContext, profile.id).copy(
                    lastResult = "Upload stopped: secure settings must be entered again",
                    severity = UploadResultSeverity.ERROR
                ),
                profile.id
            )
            return Result.failure()
        }
        val settings = settingsLoad.settings
        val service = HealthUploadService(AppDb.get(applicationContext, profile.id))
        val pending = service.pendingCounts(settings)
        val constrainedRun = inputData.getBoolean(KEY_CONSTRAINED_RUN, false)
        if (UploadWorkPolicy.shouldDeferToConstrainedWork(pending.total, constrainedRun)) {
            enqueueConstrained(applicationContext, profile.id)
            val status = AppPreferences.uploadStatus(applicationContext, profile.id).copy(
                lastResult = "Large upload waiting for charging and unmetered network",
                severity = UploadResultSeverity.WARNING,
                pendingCount = pending.total,
                serverMode = settings.serverMode
            )
            AppPreferences.setUploadStatus(applicationContext, status, profile.id)
            return Result.success()
        }
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
        AppPreferences.setUploadStatus(applicationContext, result.toStatus(), profile.id)
        Log.i(
            TAG,
            "Upload result success=${result.success} rows=${result.uploadedItems} " +
                "requestBodyBytes=${result.requestBodyBytesSent} " +
                "responseBodyBytes=${result.responseBodyBytesReceived} pending=${result.pendingCounts.total}"
        )
        UploadRetentionPolicy.productionServerKey(
            settings = settings,
            uploadSucceeded = result.success,
            profileOwnsHealthConnect = profile.ownsHealthConnect
        )?.let { serverKey ->
            HealthRetentionWorker.enqueue(applicationContext, serverKey, profile.id)
        }
        return when {
            result.success -> Result.success()
            result.retryable -> Result.retry()
            else -> Result.failure()
        }
    }

    private fun workerProfile(): LocalProfile? {
        val requestedId = inputData.getString(KEY_PROFILE_ID)
        if (requestedId != null) {
            return LocalProfileStore.profile(applicationContext, requestedId)
        }
        // Work created before profile support had no identity. Only that legacy shape may use the owner.
        return runCatching { LocalProfileStore.healthConnectProfile(applicationContext) }.getOrNull()
    }

    companion object {
        const val WORK_NAME = "health_connect_upload_pending"
        const val KEY_PHASE = "phase"
        const val KEY_BATCH = "batch"
        const val KEY_UPLOADED = "uploaded"
        const val KEY_TOTAL = "total"
        const val KEY_TYPE = "type"
        private const val KEY_CONSTRAINED_RUN = "constrained_run"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val CONSTRAINED_WORK_NAME = "health_connect_upload_large_backlog"
        private const val BACKOFF_MINUTES = 15L
        private const val TAG = "HCUploadWorker"

        fun enqueue(
            context: Context,
            profileId: String = LocalProfileStore.activeProfile(context).id
        ) = enqueue(context, profileId, ExistingWorkPolicy.KEEP)

        fun enqueueAfterCurrent(
            context: Context,
            profileId: String
        ) = enqueue(context, profileId, ExistingWorkPolicy.APPEND_OR_REPLACE)

        private fun enqueue(
            context: Context,
            profileId: String,
            existingWorkPolicy: ExistingWorkPolicy
        ) {
            LocalProfileStore.profile(context, profileId) ?: return
            val request = OneTimeWorkRequestBuilder<HealthUploadWorker>()
                .setInputData(workDataOf(KEY_PROFILE_ID to profileId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                workName(profileId),
                existingWorkPolicy,
                request
            )
        }

        private fun enqueueConstrained(context: Context, profileId: String) {
            val request = OneTimeWorkRequestBuilder<HealthUploadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_CONSTRAINED_RUN to true,
                        KEY_PROFILE_ID to profileId
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresCharging(true)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .addTag(CONSTRAINED_WORK_NAME)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                constrainedWorkName(profileId),
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        internal fun workName(profileId: String): String = "$WORK_NAME:$profileId"

        internal fun constrainedWorkName(profileId: String): String =
            "$CONSTRAINED_WORK_NAME:$profileId"

        private val workerMutex = Mutex()
    }
}
