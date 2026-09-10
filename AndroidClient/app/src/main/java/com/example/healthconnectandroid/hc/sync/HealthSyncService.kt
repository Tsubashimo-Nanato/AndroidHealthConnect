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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val coverageDao = db.healthSyncCoverageDao()
    private val changeTokenDao = db.healthChangeTokenDao()
    private val retentionDao = db.healthRetentionDao()

    suspend fun runSmartSync(
        requireBackgroundReadPermission: Boolean = false,
        onProgress: (SyncProgress) -> Unit = {}
    ): List<HealthDataTypeSyncResult> = syncMutex.withLock {
        runIncrementalSync(
            mode = SyncMode.SMART,
            requireBackgroundReadPermission = requireBackgroundReadPermission,
            onProgress = onProgress
        )
    }

    suspend fun runFullHistorySync(
        onProgress: (SyncProgress) -> Unit = {}
    ): List<HealthDataTypeSyncResult> = syncMutex.withLock {
        val end = Instant.now()
        runDescriptorSync(
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
    ): HealthDataTypeSyncResult = syncMutex.withLock {
        val progressReporter = SyncProgressReporter(onProgress)
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
        progressReporter.emit(
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
            progressReporter.emit(
                SyncProgressPolicy.progressForResults(
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
            return@withLock result
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
                    progressReporter.emit(
                        SyncProgressPolicy.progressForTypeStep(
                            mode = SyncMode.SELECTED_TYPE,
                            typeName = descriptor.displayName,
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
            progressReporter.emit(
                SyncProgressPolicy.progressForResults(
                    mode = SyncMode.SELECTED_TYPE,
                    currentType = null,
                    completedTypes = results.size,
                    totalTypes = missingWindows.size,
                    results = results,
                    rangeStart = start,
                    rangeEnd = end,
                    isCancellable = true,
                    phase = SyncProgressPolicy.terminalPhase(result),
                    message = SyncProgressPolicy.typeCompletionMessage(descriptor.displayName, result)
                )
            )
        }
        val result = SyncProgressPolicy.combineSelectedTypeResults(
            key = descriptor.key,
            requestedStart = start,
            requestedEnd = end,
            localDaysChecked = totalDailyWindows,
            localDaysRequested = missingWindows.size,
            results = results
        )
        progressReporter.emit(
            SyncProgressPolicy.progressForResults(
                mode = SyncMode.SELECTED_TYPE,
                currentType = null,
                completedTypes = 1,
                totalTypes = 1,
                results = listOf(result),
                rangeStart = start,
                rangeEnd = end,
                isCancellable = false,
                phase = SyncProgressPolicy.terminalPhase(result),
                message = SyncProgressPolicy.selectedCompletionMessage(result)
            )
        )
        result
    }

    suspend fun runPeriodicSmartSync(
        requireBackgroundReadPermission: Boolean = true,
        onProgress: (SyncProgress) -> Unit = {}
    ): List<HealthDataTypeSyncResult> = syncMutex.withLock {
        runIncrementalSync(
            mode = SyncMode.PERIODIC,
            requireBackgroundReadPermission = requireBackgroundReadPermission,
            onProgress = onProgress
        )
    }

    internal suspend fun runHistoryBackfillBatch(
        requireBackgroundReadPermission: Boolean = true,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        startIndex: Int = 0
    ): HistoryBackfillBatchResult = syncMutex.withLock {
        val target = HistoryBackfillPolicy.targetRange(now, zoneId)
        val descriptors = HealthDataTypeRegistry.implementedDescriptors
        val results = mutableListOf<HealthDataTypeSyncResult>()
        var hasMore = false
        var nextStartIndex = HistoryBackfillPolicy.typeIndex(startIndex, 0, descriptors.size)

        for (offset in descriptors.indices) {
            coroutineContext.ensureActive()
            if (!HistoryBackfillPolicy.hasTypeCapacity(results.size)) {
                hasMore = true
                nextStartIndex = HistoryBackfillPolicy.typeIndex(startIndex, offset, descriptors.size)
                break
            }
            val descriptorIndex = HistoryBackfillPolicy.typeIndex(startIndex, offset, descriptors.size)
            val descriptor = descriptors[descriptorIndex]
            nextStartIndex = HistoryBackfillPolicy.typeIndex(startIndex, offset + 1, descriptors.size)
            val archivedBefore = retentionDao.archivedBeforeEpochMillis(descriptor.key)
                ?.let(Instant::ofEpochMilli)
            val start = archivedBefore
                ?.takeIf { it.isAfter(target.start) }
                ?: target.start
            if (!start.isBefore(target.end)) continue

            val coveredWindows = coverageWindowsForRange(descriptor.key, start, target.end)
            val missingWindows = SyncWindowPlanner.missingDailyWindows(
                requestedStart = start,
                requestedEnd = target.end,
                zoneId = zoneId,
                coveredWindows = coveredWindows
            )
            val chunk = HistoryBackfillPolicy.latestChunk(
                missingWindows = missingWindows,
                maxDays = HistoryBackfillPolicy.maxDaysPerBatch(descriptor.key)
            ) ?: continue

            val result = runOneTypeWithTimeout(
                mode = SyncMode.HISTORY_BACKFILL,
                descriptor = descriptor,
                start = chunk.start,
                end = chunk.end,
                zoneId = zoneId,
                requireBackgroundReadPermission = requireBackgroundReadPermission,
                timeout = timeoutConfig.historyBackfillTypeTimeout
            )
            results += result
            recordCoverageIfSuccessful(SyncMode.HISTORY_BACKFILL, result)
            hasMore = hasMore ||
                HistoryBackfillPolicy.hasMore(missingWindows, chunk) ||
                !result.isSuccessfulCoverageWindow()
        }

        HistoryBackfillBatchResult(
            results = results,
            hasMore = hasMore,
            nextStartIndex = nextStartIndex
        )
    }

    suspend fun runLegacyHrDebugSync(hours: Long): Int =
        syncMutex.withLock { syncer.syncLastHours(hours) }

    suspend fun queryLegacyHeartRate(at: Instant): HrSample? =
        syncer.getHrAt(at)

    fun backgroundReadFeatureAvailable(): Boolean =
        syncer.backgroundReadFeatureAvailable()

    private suspend fun runIncrementalSync(
        mode: SyncMode,
        requireBackgroundReadPermission: Boolean,
        onProgress: (SyncProgress) -> Unit
    ): List<HealthDataTypeSyncResult> {
        val descriptors = HealthDataTypeRegistry.implementedDescriptors
        val results = mutableListOf<HealthDataTypeSyncResult>()
        val progressReporter = SyncProgressReporter(onProgress)
        val end = Instant.now()
        val deadline = end.plus(timeoutConfig.smartSyncTimeout)
        progressReporter.emit(
            SyncProgressPolicy.progressForResults(
                mode = mode,
                currentType = null,
                completedTypes = 0,
                totalTypes = descriptors.size,
                results = results,
                rangeStart = null,
                rangeEnd = end,
                isCancellable = false,
                message = "Preparing incremental sync"
            )
        )

        descriptors.forEachIndexed { index, descriptor ->
            coroutineContext.ensureActive()
            if (Instant.now().isAfter(deadline)) {
                results += syncer.recordSyntheticSyncResult(
                    result = HealthDataTypeSyncResult(
                        key = descriptor.key,
                        errorMessage = "Incremental sync exceeded ${timeoutConfig.smartSyncTimeout.toMinutes()} minute timeout",
                        terminalStatus = SyncRunStatus.TIMEOUT
                    ),
                    startedAt = Instant.now()
                )
                return results
            }
            progressReporter.emit(
                SyncProgressPolicy.progressForResults(
                    mode = mode,
                    currentType = descriptor.displayName,
                    completedTypes = index,
                    totalTypes = descriptors.size,
                    results = results,
                    rangeStart = null,
                    rangeEnd = end,
                    isCancellable = false,
                    phase = SyncProgressPhase.FETCHING,
                    message = "Checking ${descriptor.displayName} changes"
                )
            )
            val result = runIncrementalTypeWithTimeout(
                mode = mode,
                descriptor = descriptor,
                end = end,
                requireBackgroundReadPermission = requireBackgroundReadPermission,
                onTypeProgress = { typeProgress ->
                    progressReporter.emit(
                        SyncProgressPolicy.progressForTypeStep(
                            mode = mode,
                            typeName = descriptor.displayName,
                            completedTypes = index,
                            totalTypes = descriptors.size,
                            previousResults = results,
                            typeProgress = typeProgress,
                            rangeStart = null,
                            rangeEnd = end,
                            isCancellable = false
                        )
                    )
                }
            )
            results += result
            progressReporter.emit(
                SyncProgressPolicy.progressForResults(
                    mode = mode,
                    currentType = null,
                    completedTypes = results.size,
                    totalTypes = descriptors.size,
                    results = results,
                    rangeStart = null,
                    rangeEnd = end,
                    isCancellable = false,
                    phase = SyncProgressPolicy.terminalPhase(result),
                    message = SyncProgressPolicy.typeCompletionMessage(descriptor.displayName, result)
                )
            )
        }
        return results
    }

    private suspend fun runIncrementalTypeWithTimeout(
        mode: SyncMode,
        descriptor: HealthDataTypeDescriptor,
        end: Instant,
        requireBackgroundReadPermission: Boolean,
        onTypeProgress: (SyncTypeProgress) -> Unit
    ): HealthDataTypeSyncResult {
        val startedAt = Instant.now()
        return try {
            withTimeout(timeoutConfig.perTypeTimeout.toMillis()) {
                runIncrementalType(
                    mode = mode,
                    descriptor = descriptor,
                    end = end,
                    requireBackgroundReadPermission = requireBackgroundReadPermission,
                    onTypeProgress = onTypeProgress
                )
            }
        } catch (t: TimeoutCancellationException) {
            syncer.recordSyntheticSyncResult(
                result = HealthDataTypeSyncResult(
                    key = descriptor.key,
                    errorMessage = "Incremental sync timed out for ${descriptor.displayName}",
                    terminalStatus = SyncRunStatus.TIMEOUT
                ),
                startedAt = startedAt
            )
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            syncer.recordSyntheticSyncResult(
                result = HealthDataTypeSyncResult(
                    key = descriptor.key,
                    errorMessage = "Incremental sync failed for ${descriptor.displayName}: " +
                        (t.message ?: t.javaClass.simpleName),
                    terminalStatus = SyncRunStatus.ERROR
                ),
                startedAt = startedAt
            )
        }
    }

    private suspend fun runIncrementalType(
        mode: SyncMode,
        descriptor: HealthDataTypeDescriptor,
        end: Instant,
        requireBackgroundReadPermission: Boolean,
        onTypeProgress: (SyncTypeProgress) -> Unit
    ): HealthDataTypeSyncResult {
        val storedToken = changeTokenDao.tokenForType(descriptor.key)?.token
        if (storedToken != null) {
            when (
                val outcome = syncer.syncChanges(
                    descriptor = descriptor,
                    initialToken = storedToken,
                    requireBackgroundReadPermission = requireBackgroundReadPermission,
                    onProgress = onTypeProgress
                )
            ) {
                is HealthChangeSyncOutcome.Applied -> return outcome.result
                HealthChangeSyncOutcome.TokenExpired -> Unit
            }
        }

        // Capture the token before the baseline read so changes arriving during that read are not lost.
        val baselineToken = syncer.createChangesToken(descriptor)
        val start = SyncRangePolicy.smartStart(end)
        val baseline = syncer.syncDataType(
            key = descriptor.key,
            start = start,
            end = end,
            requireBackgroundReadPermission = requireBackgroundReadPermission,
            replaceChangedRecords = true,
            onProgress = onTypeProgress
        )
        recordCoverageIfSuccessful(mode, baseline)
        if (!baseline.canAdvanceChangesToken()) return baseline

        return when (
            val catchUp = syncer.syncChanges(
                descriptor = descriptor,
                initialToken = baselineToken,
                requireBackgroundReadPermission = requireBackgroundReadPermission,
                onProgress = onTypeProgress
            )
        ) {
            is HealthChangeSyncOutcome.Applied -> combineIncrementalResults(descriptor.key, baseline, catchUp.result)
            HealthChangeSyncOutcome.TokenExpired -> baseline
        }
    }

    private fun combineIncrementalResults(
        key: String,
        baseline: HealthDataTypeSyncResult,
        catchUp: HealthDataTypeSyncResult
    ): HealthDataTypeSyncResult = HealthDataTypeSyncResult(
        key = key,
        requestedStart = listOfNotNull(baseline.requestedStart, catchUp.requestedStart).minOrNull(),
        requestedEnd = listOfNotNull(baseline.requestedEnd, catchUp.requestedEnd).maxOrNull(),
        recordsRead = baseline.recordsRead + catchUp.recordsRead,
        recordsInserted = baseline.recordsInserted + catchUp.recordsInserted,
        recordsUpdated = baseline.recordsUpdated + catchUp.recordsUpdated,
        recordsDeleted = baseline.recordsDeleted + catchUp.recordsDeleted,
        recordsSkippedDuplicate = baseline.recordsSkippedDuplicate + catchUp.recordsSkippedDuplicate,
        valuesStored = baseline.valuesStored + catchUp.valuesStored,
        aggregateRowsRead = baseline.aggregateRowsRead + catchUp.aggregateRowsRead,
        aggregateRowsStored = baseline.aggregateRowsStored + catchUp.aggregateRowsStored,
        sourceBytesRead = baseline.sourceBytesRead + catchUp.sourceBytesRead,
        localBytesWritten = baseline.localBytesWritten + catchUp.localBytesWritten,
        sourceStart = listOfNotNull(baseline.sourceStart, catchUp.sourceStart).minOrNull(),
        sourceEnd = listOfNotNull(baseline.sourceEnd, catchUp.sourceEnd).maxOrNull(),
        aggregateErrorMessage = catchUp.aggregateErrorMessage ?: baseline.aggregateErrorMessage,
        skippedReason = catchUp.skippedReason ?: baseline.skippedReason,
        errorMessage = catchUp.errorMessage ?: baseline.errorMessage,
        terminalStatus = catchUp.terminalStatus ?: baseline.terminalStatus
    )

    private fun HealthDataTypeSyncResult.canAdvanceChangesToken(): Boolean =
        terminalStatus == null && errorMessage == null && skippedReason == null

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
        val progressReporter = SyncProgressReporter(onProgress)
        // A full sync can span many record types; the global deadline keeps one slow type from hiding the rest.
        val globalDeadline = Instant.now().plus(globalTimeout)
        progressReporter.emit(
            SyncProgressPolicy.progressForResults(
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
                progressReporter.emit(
                    SyncProgressPolicy.progressForResults(
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

            progressReporter.emit(
                SyncProgressPolicy.progressForResults(
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
                    progressReporter.emit(
                        SyncProgressPolicy.progressForTypeStep(
                            mode = mode,
                            typeName = descriptor.displayName,
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
            progressReporter.emit(
                SyncProgressPolicy.progressForResults(
                    mode = mode,
                    currentType = null,
                    completedTypes = results.size,
                    totalTypes = descriptors.size,
                    results = results,
                    rangeStart = results.mapNotNull { it.requestedStart }.minOrNull(),
                    rangeEnd = end,
                    isCancellable = isCancellable,
                    phase = SyncProgressPolicy.terminalPhase(result),
                    message = SyncProgressPolicy.typeCompletionMessage(descriptor.displayName, result)
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

    private companion object {
        // Manual and WorkManager entry points share the same Health Connect and Room write path.
        val syncMutex = Mutex()
    }
}
