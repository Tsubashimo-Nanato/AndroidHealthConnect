package com.example.healthconnectandroid.hc.sync

import android.content.Context
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.HrSample
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

class HealthSyncService(
    context: Context,
    private val db: AppDb,
    private val timeoutConfig: SyncTimeoutConfig = SyncTimeoutConfig()
) {
    private val appContext = context.applicationContext
    private val syncer = HealthDataTypeSyncer(appContext, db)
    private val syncDao = db.healthSyncRunDao()

    suspend fun runSmartSync(
        requireBackgroundReadPermission: Boolean = false,
        onProgress: (SyncProgress) -> Unit = {}
    ): List<HealthDataTypeSyncResult> {
        val end = Instant.now()
        return runDescriptorSync(
            mode = SyncMode.SMART,
            descriptors = HealthDataTypeRegistry.implementedDescriptors,
            end = end,
            requireBackgroundReadPermission = requireBackgroundReadPermission,
            isCancellable = false,
            perTypeTimeout = timeoutConfig.perTypeTimeout,
            globalTimeout = timeoutConfig.smartSyncTimeout,
            onProgress = onProgress
        ) { descriptor ->
            val latestSuccess = syncDao.latestSuccessfulFinishedEpochMillis(descriptor.key)
                ?.takeIf { it > 0L }
                ?.let(Instant::ofEpochMilli)
            SyncRangePolicy.smartStart(
                end = end,
                latestSuccessfulFinishedAt = latestSuccess
            )
        }
    }

    suspend fun runFullHistorySync(
        onProgress: (SyncProgress) -> Unit = {}
    ): List<HealthDataTypeSyncResult> {
        val end = Instant.now()
        return runDescriptorSync(
            mode = SyncMode.FULL_HISTORY,
            descriptors = HealthDataTypeRegistry.implementedDescriptors,
            end = end,
            requireBackgroundReadPermission = false,
            isCancellable = true,
            perTypeTimeout = timeoutConfig.perTypeTimeout,
            globalTimeout = timeoutConfig.fullSyncGlobalTimeout,
            onProgress = onProgress
        ) {
            SyncRangePolicy.fullHistoryStart()
        }
    }

    suspend fun runSelectedTypeSync(
        key: String,
        start: Instant,
        end: Instant,
        onProgress: (SyncProgress) -> Unit = {}
    ): HealthDataTypeSyncResult {
        val descriptor = HealthDataTypeRegistry.require(key)
        onProgress(
            SyncProgress(
                mode = SyncMode.SELECTED_TYPE,
                currentType = descriptor.displayName,
                completedTypes = 0,
                totalTypes = 1,
                inserted = 0,
                updated = 0,
                duplicates = 0,
                errors = 0,
                rangeStart = start,
                rangeEnd = end,
                isCancellable = true,
                isIndeterminate = false,
                message = "Syncing ${descriptor.displayName}"
            )
        )
        val result = runOneTypeWithTimeout(
            mode = SyncMode.SELECTED_TYPE,
            descriptor = descriptor,
            start = start,
            end = end,
            requireBackgroundReadPermission = false,
            timeout = timeoutConfig.selectedSyncTimeout
        )
        onProgress(
            progressForResults(
                mode = SyncMode.SELECTED_TYPE,
                currentType = null,
                completedTypes = 1,
                totalTypes = 1,
                results = listOf(result),
                rangeStart = start,
                rangeEnd = end,
                isCancellable = false,
                message = selectedCompletionMessage(result)
            )
        )
        return result
    }

    suspend fun runPeriodicSmartSync(
        requireBackgroundReadPermission: Boolean = true,
        onProgress: (SyncProgress) -> Unit = {}
    ): List<HealthDataTypeSyncResult> {
        val end = Instant.now()
        return runDescriptorSync(
            mode = SyncMode.PERIODIC,
            descriptors = HealthDataTypeRegistry.implementedDescriptors,
            end = end,
            requireBackgroundReadPermission = requireBackgroundReadPermission,
            isCancellable = false,
            perTypeTimeout = timeoutConfig.perTypeTimeout,
            globalTimeout = timeoutConfig.smartSyncTimeout,
            onProgress = onProgress
        ) { descriptor ->
            val latestSuccess = syncDao.latestSuccessfulFinishedEpochMillis(descriptor.key)
                ?.takeIf { it > 0L }
                ?.let(Instant::ofEpochMilli)
            SyncRangePolicy.smartStart(
                end = end,
                latestSuccessfulFinishedAt = latestSuccess
            )
        }
    }

    suspend fun runLegacyHrDebugSync(hours: Long): Int =
        syncer.syncLastHours(hours)

    suspend fun queryLegacyHeartRate(at: Instant): HrSample? =
        syncer.getHrAt(at)

    fun backgroundReadFeatureAvailable(): Boolean =
        syncer.backgroundReadFeatureAvailable()

    private suspend fun runDescriptorSync(
        mode: SyncMode,
        descriptors: List<HealthDataTypeDescriptor>,
        end: Instant,
        requireBackgroundReadPermission: Boolean,
        isCancellable: Boolean,
        perTypeTimeout: Duration,
        globalTimeout: Duration,
        onProgress: (SyncProgress) -> Unit,
        startForDescriptor: suspend (HealthDataTypeDescriptor) -> Instant
    ): List<HealthDataTypeSyncResult> {
        val results = mutableListOf<HealthDataTypeSyncResult>()
        val globalDeadline = Instant.now().plus(globalTimeout)
        onProgress(
            progressForResults(
                mode = mode,
                currentType = null,
                completedTypes = 0,
                totalTypes = descriptors.size,
                results = results,
                rangeStart = null,
                rangeEnd = end,
                isCancellable = isCancellable,
                message = "Preparing ${mode.label}"
            )
        )

        descriptors.forEachIndexed { index, descriptor ->
            coroutineContext.ensureActive()
            val start = startForDescriptor(descriptor)
            if (Instant.now().isAfter(globalDeadline)) {
                val timeoutResult = syncer.recordSyntheticSyncResult(
                    result = HealthDataTypeSyncResult(
                        key = descriptor.key,
                        requestedStart = start,
                        requestedEnd = end,
                        errorMessage = "${mode.label} exceeded ${globalTimeout.toMinutes()} minute global timeout",
                        terminalStatus = SyncRunStatus.TIMEOUT
                    ),
                    startedAt = Instant.now()
                )
                results += timeoutResult
                onProgress(
                    progressForResults(
                        mode = mode,
                        currentType = null,
                        completedTypes = results.size,
                        totalTypes = descriptors.size,
                        results = results,
                        rangeStart = results.mapNotNull { it.requestedStart }.minOrNull(),
                        rangeEnd = end,
                        isCancellable = false,
                        message = "${mode.label} timed out"
                    )
                )
                return results
            }

            onProgress(
                progressForResults(
                    mode = mode,
                    currentType = descriptor.displayName,
                    completedTypes = index,
                    totalTypes = descriptors.size,
                    results = results,
                    rangeStart = start,
                    rangeEnd = end,
                    isCancellable = isCancellable,
                    message = "Syncing ${descriptor.displayName}"
                )
            )
            val result = runOneTypeWithTimeout(
                mode = mode,
                descriptor = descriptor,
                start = start,
                end = end,
                requireBackgroundReadPermission = requireBackgroundReadPermission,
                timeout = perTypeTimeout
            )
            results += result
            onProgress(
                progressForResults(
                    mode = mode,
                    currentType = null,
                    completedTypes = results.size,
                    totalTypes = descriptors.size,
                    results = results,
                    rangeStart = results.mapNotNull { it.requestedStart }.minOrNull(),
                    rangeEnd = end,
                    isCancellable = isCancellable,
                    message = "${descriptor.displayName} ${result.terminalStatus?.id ?: "done"}"
                )
            )
        }
        return results
    }

    private suspend fun runOneTypeWithTimeout(
        mode: SyncMode,
        descriptor: HealthDataTypeDescriptor,
        start: Instant,
        end: Instant,
        requireBackgroundReadPermission: Boolean,
        timeout: Duration
    ): HealthDataTypeSyncResult {
        val startedAt = Instant.now()
        return try {
            withTimeout(timeout.toMillis()) {
                syncer.syncDataType(
                    key = descriptor.key,
                    start = start,
                    end = end,
                    requireBackgroundReadPermission = requireBackgroundReadPermission
                )
            }
        } catch (t: TimeoutCancellationException) {
            syncer.recordSyntheticSyncResult(
                result = HealthDataTypeSyncResult(
                    key = descriptor.key,
                    requestedStart = start,
                    requestedEnd = end,
                    errorMessage = "${mode.label} timed out for ${descriptor.displayName} after ${timeout.toMinutes()} minutes",
                    terminalStatus = SyncRunStatus.TIMEOUT
                ),
                startedAt = startedAt
            )
        } catch (t: CancellationException) {
            syncer.recordSyntheticSyncResult(
                result = HealthDataTypeSyncResult(
                    key = descriptor.key,
                    requestedStart = start,
                    requestedEnd = end,
                    errorMessage = "${mode.label} cancelled while syncing ${descriptor.displayName}",
                    terminalStatus = SyncRunStatus.CANCELLED
                ),
                startedAt = startedAt
            )
            throw t
        }
    }

    private fun progressForResults(
        mode: SyncMode,
        currentType: String?,
        completedTypes: Int,
        totalTypes: Int,
        results: List<HealthDataTypeSyncResult>,
        rangeStart: Instant?,
        rangeEnd: Instant?,
        isCancellable: Boolean,
        message: String?
    ): SyncProgress =
        SyncProgress(
            mode = mode,
            currentType = currentType,
            completedTypes = completedTypes,
            totalTypes = totalTypes,
            inserted = results.sumOf { it.recordsInserted },
            updated = results.sumOf { it.recordsUpdated },
            duplicates = results.sumOf { it.recordsSkippedDuplicate },
            errors = results.count { it.errorMessage != null || it.terminalStatus == SyncRunStatus.TIMEOUT },
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isCancellable = isCancellable,
            isIndeterminate = totalTypes <= 0,
            message = message
        )

    private fun selectedCompletionMessage(result: HealthDataTypeSyncResult): String =
        when {
            result.terminalStatus == SyncRunStatus.TIMEOUT -> "Selected sync timed out"
            result.terminalStatus == SyncRunStatus.CANCELLED -> "Selected sync cancelled"
            result.errorMessage != null -> "Selected sync failed"
            result.skippedReason != null -> "Selected sync skipped"
            else -> "Selected sync complete"
        }
}
