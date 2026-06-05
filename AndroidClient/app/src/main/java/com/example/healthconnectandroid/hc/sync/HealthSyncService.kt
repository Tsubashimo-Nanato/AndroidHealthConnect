package com.example.healthconnectandroid.hc.sync

import android.content.Context
import android.util.Log
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthSyncCoverageEntity
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.HrSample
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.coroutineContext

private const val TAG = "HCHRSyncService"

class HealthSyncService(
    context: Context,
    private val db: AppDb,
    private val timeoutConfig: SyncTimeoutConfig = SyncTimeoutConfig()
) {
    private val appContext = context.applicationContext
    private val syncer = HealthDataTypeSyncer(appContext, db)
    private val syncDao = db.healthSyncRunDao()
    private val coverageDao = db.healthSyncCoverageDao()

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
        zoneId: ZoneId = ZoneId.systemDefault(),
        onProgress: (SyncProgress) -> Unit = {}
    ): HealthDataTypeSyncResult {
        val descriptor = HealthDataTypeRegistry.require(key)
        val coveredWindows = coverageWindowsForRange(key, start, end)
        val totalDailyWindows = SyncWindowPlanner.dailyWindowCount(start, end, zoneId)
        val missingWindows = SyncWindowPlanner.missingDailyWindows(
            requestedStart = start,
            requestedEnd = end,
            zoneId = zoneId,
            coveredWindows = coveredWindows
        )
        val coveredDailyWindows = (totalDailyWindows - missingWindows.size).coerceAtLeast(0)
        onProgress(
            SyncProgress(
                mode = SyncMode.SELECTED_TYPE,
                currentType = descriptor.displayName,
                completedTypes = 0,
                totalTypes = missingWindows.size.coerceAtLeast(1),
                inserted = 0,
                updated = 0,
                duplicates = 0,
                errors = 0,
                rangeStart = start,
                rangeEnd = end,
                isCancellable = true,
                isIndeterminate = false,
                phase = SyncProgressPhase.PREPARING,
                message = "Checking sync coverage: $coveredDailyWindows/$totalDailyWindows days covered"
            )
        )
        if (missingWindows.isEmpty()) {
            val result = syncer.recordSyntheticSyncResult(
                result = HealthDataTypeSyncResult(
                    key = descriptor.key,
                    requestedStart = start,
                    requestedEnd = end,
                    localDaysChecked = totalDailyWindows,
                    localDaysRequested = 0
                ),
                startedAt = Instant.now()
            )
            recordCoverageIfSuccessful(SyncMode.SELECTED_TYPE, result)
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
                    phase = SyncProgressPhase.NO_NEW_DATA,
                    message = "Selected sync found no missing local days"
                )
            )
            return result
        }

        val results = mutableListOf<HealthDataTypeSyncResult>()
        missingWindows.forEachIndexed { index, window ->
            coroutineContext.ensureActive()
            val result = runOneTypeWithTimeout(
                mode = SyncMode.SELECTED_TYPE,
                descriptor = descriptor,
                start = window.start,
                end = window.end,
                zoneId = zoneId,
                requireBackgroundReadPermission = false,
                timeout = timeoutConfig.selectedSyncTimeout,
                onTypeProgress = { typeProgress ->
                    onProgress(
                        progressForTypeStep(
                            mode = SyncMode.SELECTED_TYPE,
                            descriptor = descriptor,
                            completedTypes = index,
                            totalTypes = missingWindows.size,
                            previousResults = results,
                            typeProgress = typeProgress,
                            rangeStart = start,
                            rangeEnd = end,
                            isCancellable = true,
                            messagePrefix = "Day ${index + 1}/${missingWindows.size}: "
                        )
                    )
                }
            )
            results += result
            recordCoverageIfSuccessful(SyncMode.SELECTED_TYPE, result)
            onProgress(
                progressForResults(
                    mode = SyncMode.SELECTED_TYPE,
                    currentType = null,
                    completedTypes = results.size,
                    totalTypes = missingWindows.size,
                    results = results,
                    rangeStart = start,
                    rangeEnd = end,
                    isCancellable = true,
                    phase = terminalPhase(result),
                    message = typeCompletionMessage(descriptor, result)
                )
            )
        }
        val result = combineSelectedTypeResults(
            key = descriptor.key,
            requestedStart = start,
            requestedEnd = end,
            localDaysChecked = totalDailyWindows,
            localDaysRequested = missingWindows.size,
            results = results
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
                phase = terminalPhase(result),
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
                        phase = SyncProgressPhase.TIMEOUT,
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
                    phase = SyncProgressPhase.PREPARING,
                    message = "Syncing ${descriptor.displayName}"
                )
            )
            val result = runOneTypeWithTimeout(
                mode = mode,
                descriptor = descriptor,
                start = start,
                end = end,
                zoneId = ZoneId.systemDefault(),
                requireBackgroundReadPermission = requireBackgroundReadPermission,
                timeout = perTypeTimeout,
                onTypeProgress = { typeProgress ->
                    onProgress(
                        progressForTypeStep(
                            mode = mode,
                            descriptor = descriptor,
                            completedTypes = index,
                            totalTypes = descriptors.size,
                            previousResults = results,
                            typeProgress = typeProgress,
                            rangeStart = start,
                            rangeEnd = end,
                            isCancellable = isCancellable
                        )
                    )
                }
            )
            results += result
            recordCoverageIfSuccessful(mode, result)
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
                    phase = terminalPhase(result),
                    message = typeCompletionMessage(descriptor, result)
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
        zoneId: ZoneId,
        requireBackgroundReadPermission: Boolean,
        timeout: Duration,
        onTypeProgress: (SyncTypeProgress) -> Unit = {}
    ): HealthDataTypeSyncResult {
        val startedAt = Instant.now()
        return try {
            withTimeout(timeout.toMillis()) {
                syncer.syncDataType(
                    key = descriptor.key,
                    start = start,
                    end = end,
                    zoneId = zoneId,
                    requireBackgroundReadPermission = requireBackgroundReadPermission,
                    onProgress = onTypeProgress
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
        phase: SyncProgressPhase = if (currentType != null) SyncProgressPhase.PREPARING else SyncProgressPhase.COMPLETE,
        message: String?
    ): SyncProgress =
        SyncProgress(
            mode = mode,
            currentType = currentType,
            completedTypes = completedTypes,
            totalTypes = totalTypes,
            read = results.sumOf { it.recordsRead + it.aggregateRowsRead },
            inserted = results.sumOf { it.recordsInserted },
            updated = results.sumOf { it.recordsUpdated },
            duplicates = results.sumOf { it.recordsSkippedDuplicate },
            errors = results.count {
                it.errorMessage != null ||
                    it.aggregateErrorMessage != null ||
                    it.terminalStatus == SyncRunStatus.TIMEOUT
            },
            sourceBytesRead = results.sumOf { it.sourceBytesRead },
            localBytesWritten = results.sumOf { it.localBytesWritten },
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isCancellable = isCancellable,
            isIndeterminate = totalTypes <= 0,
            phase = phase,
            message = message
        )

    private suspend fun coverageWindowsForRange(
        key: String,
        start: Instant,
        end: Instant
    ): List<SyncCoverageWindow> =
        coverageDao.successfulCoverageForTypeRange(
            recordType = key,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli()
        ).map { coverage ->
            SyncCoverageWindow(
                start = Instant.ofEpochMilli(coverage.coveredStartEpochMillis),
                end = Instant.ofEpochMilli(coverage.coveredEndEpochMillis)
            )
        }

    private suspend fun recordCoverageIfSuccessful(
        mode: SyncMode,
        result: HealthDataTypeSyncResult
    ) {
        if (!result.isSuccessfulCoverageWindow()) return
        val start = result.requestedStart ?: return
        val end = result.requestedEnd ?: return

        try {
            coverageDao.insert(
                HealthSyncCoverageEntity(
                    recordType = result.key,
                    coveredStartEpochMillis = start.toEpochMilli(),
                    coveredEndEpochMillis = end.toEpochMilli(),
                    status = SyncRunStatus.SUCCESS.id,
                    mode = mode.name,
                    updatedAtEpochMillis = Instant.now().toEpochMilli(),
                    recordsRead = result.recordsRead + result.aggregateRowsRead,
                    recordsInserted = result.recordsInserted + result.recordsUpdated + result.aggregateRowsStored,
                    recordsSkippedDuplicate = result.recordsSkippedDuplicate,
                    errorMessage = null
                )
            )
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to record sync coverage for type=${result.key} mode=${mode.name}", t)
        }
    }

    private fun combineSelectedTypeResults(
        key: String,
        requestedStart: Instant,
        requestedEnd: Instant,
        localDaysChecked: Int,
        localDaysRequested: Int,
        results: List<HealthDataTypeSyncResult>
    ): HealthDataTypeSyncResult {
        val terminalStatus = when {
            results.any { it.terminalStatus == SyncRunStatus.TIMEOUT } -> SyncRunStatus.TIMEOUT
            results.any { it.terminalStatus == SyncRunStatus.CANCELLED } -> SyncRunStatus.CANCELLED
            results.any { it.errorMessage != null || it.aggregateErrorMessage != null } -> SyncRunStatus.ERROR
            else -> null
        }
        val errorMessage = results.mapNotNull { it.errorMessage }.distinct().joinToString("; ")
            .ifBlank { null }
        val aggregateErrorMessage = results.mapNotNull { it.aggregateErrorMessage }.distinct().joinToString("; ")
            .ifBlank { null }

        return HealthDataTypeSyncResult(
            key = key,
            requestedStart = requestedStart,
            requestedEnd = requestedEnd,
            recordsRead = results.sumOf { it.recordsRead },
            recordsInserted = results.sumOf { it.recordsInserted },
            recordsUpdated = results.sumOf { it.recordsUpdated },
            recordsSkippedDuplicate = results.sumOf { it.recordsSkippedDuplicate },
            valuesStored = results.sumOf { it.valuesStored },
            aggregateRowsRead = results.sumOf { it.aggregateRowsRead },
            aggregateRowsStored = results.sumOf { it.aggregateRowsStored },
            sourceBytesRead = results.sumOf { it.sourceBytesRead },
            localBytesWritten = results.sumOf { it.localBytesWritten },
            sourceStart = results.mapNotNull { it.sourceStart }.minOrNull(),
            sourceEnd = results.mapNotNull { it.sourceEnd }.maxOrNull(),
            localDaysChecked = localDaysChecked,
            localDaysRequested = localDaysRequested,
            aggregateErrorMessage = aggregateErrorMessage,
            errorMessage = errorMessage,
            terminalStatus = terminalStatus
        )
    }

    private fun progressForTypeStep(
        mode: SyncMode,
        descriptor: HealthDataTypeDescriptor,
        completedTypes: Int,
        totalTypes: Int,
        previousResults: List<HealthDataTypeSyncResult>,
        typeProgress: SyncTypeProgress,
        rangeStart: Instant?,
        rangeEnd: Instant?,
        isCancellable: Boolean,
        messagePrefix: String = ""
    ): SyncProgress =
        SyncProgress(
            mode = mode,
            currentType = descriptor.displayName,
            completedTypes = completedTypes,
            totalTypes = totalTypes,
            read = previousResults.sumOf { it.recordsRead + it.aggregateRowsRead } + typeProgress.recordsRead,
            inserted = previousResults.sumOf { it.recordsInserted } + typeProgress.inserted,
            updated = previousResults.sumOf { it.recordsUpdated } + typeProgress.updated,
            duplicates = previousResults.sumOf { it.recordsSkippedDuplicate } + typeProgress.duplicates,
            errors = previousResults.count {
                it.errorMessage != null ||
                    it.aggregateErrorMessage != null ||
                    it.terminalStatus == SyncRunStatus.TIMEOUT
            } +
                typeProgress.errors,
            sourceBytesRead = previousResults.sumOf { it.sourceBytesRead } + typeProgress.sourceBytesRead,
            localBytesWritten = previousResults.sumOf { it.localBytesWritten } + typeProgress.localBytesWritten,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isCancellable = isCancellable,
            isIndeterminate = totalTypes <= 0,
            phase = typeProgress.phase,
            message = messagePrefix + (typeProgress.message ?: typeProgress.phase.label)
        )

    private fun terminalPhase(result: HealthDataTypeSyncResult): SyncProgressPhase =
        when {
            result.terminalStatus == SyncRunStatus.TIMEOUT -> SyncProgressPhase.TIMEOUT
            result.terminalStatus == SyncRunStatus.CANCELLED -> SyncProgressPhase.CANCELLED
            result.errorMessage != null || result.aggregateErrorMessage != null -> SyncProgressPhase.FAILED
            result.skippedReason != null -> SyncProgressPhase.SKIPPED
            result.recordsInserted + result.recordsUpdated + result.aggregateRowsStored + result.valuesStored > 0 ->
                SyncProgressPhase.INSERTED_DATA
            result.localDaysChecked > 0 && result.localDaysRequested == 0 -> SyncProgressPhase.NO_NEW_DATA
            result.recordsRead + result.aggregateRowsRead == 0 -> SyncProgressPhase.NO_SOURCE_DATA
            result.recordsSkippedDuplicate > 0 -> SyncProgressPhase.NO_NEW_DATA
            else -> SyncProgressPhase.COMPLETE
        }

    private fun selectedCompletionMessage(result: HealthDataTypeSyncResult): String =
        when {
            result.terminalStatus == SyncRunStatus.TIMEOUT -> "Selected sync timed out"
            result.terminalStatus == SyncRunStatus.CANCELLED -> "Selected sync cancelled"
            result.errorMessage != null -> "Selected sync failed: ${result.errorMessage}"
            result.aggregateErrorMessage != null -> "Selected sync failed: ${result.aggregateErrorMessage}"
            result.skippedReason != null -> "Selected sync skipped"
            result.localDaysChecked > 0 && result.localDaysRequested == 0 ->
                "Selected sync found no missing local days"
            terminalPhase(result) == SyncProgressPhase.INSERTED_DATA -> "Selected sync inserted data"
            terminalPhase(result) == SyncProgressPhase.NO_SOURCE_DATA -> "Selected sync found no source data"
            terminalPhase(result) == SyncProgressPhase.NO_NEW_DATA -> "Selected sync found no new data"
            else -> "Selected sync complete"
        }

    private fun typeCompletionMessage(
        descriptor: HealthDataTypeDescriptor,
        result: HealthDataTypeSyncResult
    ): String =
        when (terminalPhase(result)) {
            SyncProgressPhase.INSERTED_DATA -> "${descriptor.displayName} inserted data"
            SyncProgressPhase.NO_SOURCE_DATA -> "${descriptor.displayName} no source data"
            SyncProgressPhase.NO_NEW_DATA -> "${descriptor.displayName} no new data"
            SyncProgressPhase.FAILED -> "${descriptor.displayName} failed"
            SyncProgressPhase.SKIPPED -> "${descriptor.displayName} skipped"
            SyncProgressPhase.TIMEOUT -> "${descriptor.displayName} timed out"
            SyncProgressPhase.CANCELLED -> "${descriptor.displayName} cancelled"
            else -> "${descriptor.displayName} complete"
        }
}
