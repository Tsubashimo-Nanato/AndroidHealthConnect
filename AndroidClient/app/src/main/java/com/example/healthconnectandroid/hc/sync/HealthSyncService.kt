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
            onProgress(
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
        onProgress(
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
        // A full sync can span many record types; the global deadline keeps one slow type from hiding the rest.
        val globalDeadline = Instant.now().plus(globalTimeout)
        onProgress(
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
                onProgress(
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

            onProgress(
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
                    onProgress(
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
            onProgress(
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

}
