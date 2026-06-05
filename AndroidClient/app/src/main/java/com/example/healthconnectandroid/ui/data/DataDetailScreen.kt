package com.example.healthconnectandroid.ui.data

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthconnectandroid.UserPreferences
import com.example.healthconnectandroid.debug.DeviceSmokeDiagnostics
import com.example.healthconnectandroid.hc.HealthDataImplementationStatus
import com.example.healthconnectandroid.hc.HealthDataPermissionStatus
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.HealthDisplayFormatter
import com.example.healthconnectandroid.hc.HeartRateAnalysis
import com.example.healthconnectandroid.hc.HeartRateDateAnalysis
import com.example.healthconnectandroid.hc.HrDateSelectionMode
import com.example.healthconnectandroid.hc.InspectorDetailData
import com.example.healthconnectandroid.hc.InspectorTimeRange
import com.example.healthconnectandroid.hc.VisualizationType
import com.example.healthconnectandroid.hc.query.HealthDetailQueryService
import com.example.healthconnectandroid.hc.query.HealthRecordDetailQueryService
import com.example.healthconnectandroid.hc.sync.SyncMode
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyState
import com.example.healthconnectandroid.ui.LoadingStateCard
import com.example.healthconnectandroid.ui.MetricCard
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.SyncProgressCard
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.charts.ChartBucket
import com.example.healthconnectandroid.ui.charts.ChartVisibleRange
import com.example.healthconnectandroid.ui.charts.InspectorVisualization
import com.example.healthconnectandroid.ui.charts.defaultChartBucketFor
import com.example.healthconnectandroid.ui.hr.HeartRateDateSelector
import com.example.healthconnectandroid.ui.hr.HrDateCell
import com.example.healthconnectandroid.ui.records.RecordsSection
import com.example.healthconnectandroid.ui.sleep.SleepInsightSummary
import com.example.healthconnectandroid.ui.sleep.SleepSessionsSection
import com.example.healthconnectandroid.ui.sleep.SleepVisualizationSection
import com.example.healthconnectandroid.ui.sleep.defaultSleepSelection
import com.example.healthconnectandroid.ui.sleep.filterSleepSessionsForSelection
import com.example.healthconnectandroid.ui.sleep.sleepAnchorInsideQueryWindow
import com.example.healthconnectandroid.ui.sleep.sleepDataQueryWindow
import com.example.healthconnectandroid.ui.sleep.sleepMatrixCache
import com.example.healthconnectandroid.ui.sleep.sleepMatrixForAnchor
import com.example.healthconnectandroid.ui.sleep.sleepMatrixKey
import com.example.healthconnectandroid.ui.sleep.sleepSessionModels
import com.example.healthconnectandroid.ui.sleep.sleepShiftWindowDate
import com.example.healthconnectandroid.ui.sleep.sleepVisibleEndDate
import com.example.healthconnectandroid.ui.sleep.sleepVisibleStartDate
import com.example.healthconnectandroid.ui.sleep.sleepWindowEndInstant
import com.example.healthconnectandroid.ui.sleep.sleepWindowLabel
import com.example.healthconnectandroid.ui.sleep.toSleepInput
import com.example.healthconnectandroid.ui.format.DisplayPreferences
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import com.example.healthconnectandroid.ui.format.toDisplayPreferences
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun HealthDataDetailScreen(
    detailQueries: HealthDetailQueryService,
    recordQueries: HealthRecordDetailQueryService,
    dataTypeKey: String,
    grantedPermissions: Set<String>,
    userAge: Int?,
    userPreferences: UserPreferences,
    diagnostics: DeviceSmokeDiagnostics,
    onExportType: (String) -> Unit,
    runSelectedTypeSync: suspend (String, Instant, Instant, ZoneId, (SyncProgress) -> Unit) -> HealthDataTypeSyncResult,
    onLocalDataChanged: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val descriptor = remember(dataTypeKey) {
        com.example.healthconnectandroid.hc.HealthDataTypeRegistry.require(dataTypeKey)
    }
    val scope = rememberCoroutineScope()
    var range by remember(dataTypeKey) { mutableStateOf(DetailRangeOptions.defaultRangeFor(descriptor)) }
    var chartBucket by remember(dataTypeKey) { mutableStateOf(defaultChartBucketFor(descriptor)) }
    var reloadVersion by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<InspectorDetailData?>(null) }
    var status by remember { mutableStateOf("Loading...") }
    val displayPreferences = remember(userPreferences) { userPreferences.toDisplayPreferences() }
    val zoneId = displayPreferences.zoneId
    val today = LocalDate.now(zoneId)
    val weekStart = displayPreferences.weekStart
    var sleepWindowEndDate by remember(dataTypeKey, range, zoneId) { mutableStateOf(LocalDate.now(zoneId)) }
    var sleepDataWindowCenterDate by remember(dataTypeKey, range, zoneId) { mutableStateOf(LocalDate.now(zoneId)) }
    var showRecords by remember(dataTypeKey, range) { mutableStateOf(false) }
    var selectedSleepBoxIds by remember(dataTypeKey, range) { mutableStateOf<Set<String>>(emptySet()) }
    var heartRateMode by remember(dataTypeKey) { mutableStateOf(HrDateSelectionMode.WEEK) }
    var heartRateAnchorDate by remember(dataTypeKey, zoneId) { mutableStateOf(LocalDate.now(zoneId)) }
    var selectedHeartRateDates by remember(dataTypeKey) { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var heartRateSelectionVersion by remember(dataTypeKey) { mutableIntStateOf(0) }
    var detailLoading by remember(dataTypeKey) { mutableStateOf(false) }
    var heartRateChartLoading by remember(dataTypeKey) { mutableStateOf(false) }
    var syncInProgress by remember { mutableStateOf(false) }
    var selectedSyncProgress by remember(dataTypeKey, range) { mutableStateOf<SyncProgress?>(null) }
    var selectedSyncJob by remember(dataTypeKey, range) { mutableStateOf<Job?>(null) }
    var heartRateVisibleRange by remember(dataTypeKey, range) { mutableStateOf<ChartVisibleRange?>(null) }
    val detailScrollState = rememberScrollState()
    var pendingSleepScrollRestore by remember(dataTypeKey) { mutableStateOf<Int?>(null) }
    val permissionStatus = descriptor.permissionStatus(grantedPermissions)
    val permissionGranted = permissionStatus == HealthDataPermissionStatus.GRANTED
    val isSleep = descriptor.key == HealthDataTypeKeys.SLEEP_SESSION
    val isHeartRate = descriptor.key == HealthDataTypeKeys.HEART_RATE

    fun detailEndInstant(): Instant =
        if (isSleep) sleepWindowEndInstant(range, sleepWindowEndDate, weekStart, zoneId) else Instant.now()

    fun selectedSyncRange(): Pair<Instant, Instant> {
        val end = detailEndInstant()
        return range.startBefore(end, zoneId) to end
    }

    fun quickSyncRange(): Pair<Instant, Instant> {
        val end = Instant.now()
        return end.minus(31, ChronoUnit.DAYS) to end
    }

    fun launchSelectedSync(label: String, syncRange: Pair<Instant, Instant>) {
        selectedSyncJob = scope.launch {
            syncInProgress = true
            val (start, end) = syncRange
            selectedSyncProgress = null
            status = "$label ${descriptor.displayName}..."
            val result = try {
                runSelectedTypeSync(dataTypeKey, start, end, zoneId) { progress ->
                    selectedSyncProgress = progress
                    diagnostics.recordSyncProgress(progress)
                }
            } catch (t: CancellationException) {
                status = "${descriptor.displayName} sync cancelled"
                diagnostics.recordSyncCancelled(SyncMode.SELECTED_TYPE)
                selectedSyncProgress = selectedSyncProgress?.copy(
                    isCancellable = false,
                    message = "Selected sync cancelled"
                )
                syncInProgress = false
                selectedSyncJob = null
                return@launch
            } catch (t: Throwable) {
                status = "Sync failed: ${t.message}"
                diagnostics.recordSyncFailure(SyncMode.SELECTED_TYPE, dataTypeKey, start, end)
                syncInProgress = false
                selectedSyncJob = null
                return@launch
            }
            diagnostics.recordSyncResult(SyncMode.SELECTED_TYPE, result)
            status = syncStatusText(result, zoneId)
            reloadVersion++
            onLocalDataChanged()
            syncInProgress = false
            selectedSyncJob = null
        }
    }

    val sleepQueryWindow = remember(isSleep, range, sleepDataWindowCenterDate, weekStart, zoneId) {
        if (isSleep) sleepDataQueryWindow(range, sleepDataWindowCenterDate, weekStart, zoneId) else null
    }
    val sleepLoadKey = sleepQueryWindow?.key ?: "standard"
    val heartRateChartQueryRange = remember(isHeartRate, selectedHeartRateDates, zoneId) {
        if (isHeartRate) HeartRateDateAnalysis.visibleRangeForDates(selectedHeartRateDates, zoneId) else null
    }
    val heartRateChartLoadKey = heartRateChartQueryRange?.let { queryRange ->
        "${queryRange.start.toEpochMilli()}:${queryRange.end.toEpochMilli()}"
    } ?: "latest-day"

    LaunchedEffect(
        dataTypeKey,
        range,
        reloadVersion,
        sleepLoadKey,
        heartRateChartLoadKey,
        zoneId,
        displayPreferences.unitSystem
    ) {
        val chartReload = isHeartRate && detail != null
        if (chartReload) {
            heartRateChartLoading = true
        } else {
            detailLoading = true
        }
        status = "Loading local ${descriptor.displayName} data..."
        if (!isHeartRate || detail == null) {
            detail = null
        }
        try {
            val loaded = if (isSleep && sleepQueryWindow != null) {
                detailQueries.inspectorDetailForWindow(
                    key = dataTypeKey,
                    range = range,
                    start = sleepQueryWindow.start,
                    end = sleepQueryWindow.end,
                    zoneId = zoneId,
                    unitSystem = displayPreferences.unitSystem,
                    weekStart = weekStart
                )
            } else {
                detailQueries.inspectorDetail(
                    key = dataTypeKey,
                    range = range,
                    end = detailEndInstant(),
                    zoneId = zoneId,
                    unitSystem = displayPreferences.unitSystem,
                    weekStart = weekStart,
                    chartStart = heartRateChartQueryRange?.start,
                    chartEnd = heartRateChartQueryRange?.end
                )
            }
            detail = loaded
            diagnostics.recordDetailQuery(
                dataType = dataTypeKey,
                start = loaded.start,
                end = loaded.end,
                chartPointCount = loaded.chartPoints.size,
                sleepSessionCount = if (isSleep) {
                    loaded.readableRows.count { row -> row.recordTypeKey == HealthDataTypeKeys.SLEEP_SESSION }
                } else {
                    null
                }
            )
            status = if (isHeartRate) {
                lastSyncedDataText(loaded.lastSynced, zoneId)
            } else {
                "Last synced data: ${descriptor.displayName} for ${range.label}"
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            status = "Load failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            if (chartReload) {
                heartRateChartLoading = false
            } else {
                detailLoading = false
            }
        }
    }

    LaunchedEffect(pendingSleepScrollRestore, sleepWindowEndDate, selectedSleepBoxIds, detail) {
        val target = pendingSleepScrollRestore ?: return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        detailScrollState.scrollTo(target.coerceIn(0, detailScrollState.maxValue))
        pendingSleepScrollRestore = null
    }

    Column(
        modifier
            .padding(18.dp)
            .verticalScroll(detailScrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppSection(
            modifier = Modifier.rowFadeIn(0),
            title = descriptor.displayName,
            subtitle = if (isHeartRate) {
                detail?.let { lastSyncedDataText(it.lastSynced, zoneId) } ?: "Last synced data: loading"
            } else {
                "Last synced data: ${range.label}"
            }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusBadge(
                    implementationStatusText(descriptor.implementationStatus),
                    implementationTone(descriptor.implementationStatus)
                )
                StatusBadge(permissionStatusText(permissionStatus), permissionTone(permissionStatus))
                StatusBadge(visualizationLabel(descriptor.visualizationType), StatusTone.Info)
            }
            StatusMessageCard(status)
            val syncEnabled = permissionGranted &&
                descriptor.implementationStatus == HealthDataImplementationStatus.IMPLEMENTED &&
                !syncInProgress
            AppActionRow {
                SecondaryActionButton(
                    modifier = Modifier.weight(1f),
                    label = "Quick Sync",
                    enabled = syncEnabled,
                    onClick = { launchSelectedSync("Quick syncing", quickSyncRange()) }
                )
                PrimaryActionButton(
                    modifier = Modifier.weight(1f),
                    label = if (syncInProgress) "Syncing..." else "Sync all",
                    enabled = syncEnabled,
                    onClick = { launchSelectedSync("Syncing", selectedSyncRange()) }
                )
            }
            if (syncInProgress) {
                SecondaryActionButton(
                    label = "Cancel Sync",
                    onClick = { selectedSyncJob?.cancel() }
                )
            }
            SyncProgressCard(selectedSyncProgress)
            SecondaryActionButton(
                label = "Export All ${descriptor.displayName} CSV",
                onClick = { onExportType(dataTypeKey) }
            )
        }

        detail?.let { loaded ->
            val sleepModels = remember(isSleep, loaded.readableRows, zoneId) {
                if (isSleep) sleepSessionModels(loaded.readableRows, zoneId) else emptyList()
            }
            if (isSleep) {
                SleepInsightSummary(
                    detail = loaded,
                    sessionModels = sleepModels,
                    startDate = sleepVisibleStartDate(range, sleepWindowEndDate, weekStart),
                    endDate = sleepVisibleEndDate(range, sleepWindowEndDate, weekStart),
                    zoneId = zoneId
                )
            } else if (!isHeartRate) {
                DetailStatusSummary(loaded, zoneId = zoneId, modifier = Modifier.rowFadeIn(1))
            }

            if (!isSleep && !isHeartRate) {
                AppSection(title = "Time Range", modifier = Modifier.rowFadeIn(2)) {
                    TimeRangeSelector(
                        selected = range,
                        options = DetailRangeOptions.optionsFor(descriptor),
                        onSelected = {
                            if (it != range) {
                                heartRateVisibleRange = null
                                range = it
                            }
                        }
                    )
                }
            }

            if (isSleep) {
                val sleepInputs = remember(sleepModels) { sleepModels.map { it.toSleepInput() } }
                val matrixCache = remember(sleepInputs, range, sleepDataWindowCenterDate) {
                    sleepMatrixCache(
                        range = range,
                        sessions = sleepInputs,
                        centerDate = sleepDataWindowCenterDate,
                        weekStart = weekStart,
                        zoneId = zoneId
                    )
                }
                val matrixKey = sleepMatrixKey(range, sleepWindowEndDate, weekStart)
                val matrixModel = matrixCache[matrixKey]
                    ?: sleepMatrixForAnchor(range, sleepInputs, sleepWindowEndDate, weekStart, zoneId)
                val matrixSessionCount = remember(matrixModel) {
                    matrixModel.boxes.sumOf { it.sessionCount }
                }
                LaunchedEffect(dataTypeKey, loaded.start, loaded.end, matrixKey, sleepModels.size, matrixModel.boxes.size) {
                    diagnostics.recordDetailQuery(
                        dataType = dataTypeKey,
                        start = loaded.start,
                        end = loaded.end,
                        chartPointCount = loaded.chartPoints.size,
                        sleepSessionCount = sleepModels.size,
                        matrixCellCount = matrixModel.boxes.size
                    )
                }
                LaunchedEffect(matrixKey, matrixModel.boxes.size, matrixSessionCount) {
                    val validIds = matrixModel.boxes.mapTo(mutableSetOf()) { it.id }
                    val stillValid = selectedSleepBoxIds.filterTo(mutableSetOf()) { it in validIds }
                    selectedSleepBoxIds = stillValid.ifEmpty { defaultSleepSelection(matrixModel) }
                }
                val selectedSleepModels = remember(sleepModels, matrixModel, selectedSleepBoxIds, zoneId) {
                    filterSleepSessionsForSelection(sleepModels, matrixModel, selectedSleepBoxIds, zoneId)
                }
                SleepVisualizationSection(
                    selectedRange = range,
                    onRangeSelected = { selected ->
                        if (selected != range) {
                            pendingSleepScrollRestore = detailScrollState.value
                            range = selected
                            sleepWindowEndDate = LocalDate.now(zoneId)
                            sleepDataWindowCenterDate = LocalDate.now(zoneId)
                        }
                    },
                    windowLabel = sleepWindowLabel(range, sleepWindowEndDate, weekStart),
                    model = matrixModel,
                    selectedBoxIds = selectedSleepBoxIds,
                    onSelectedBoxIdsChange = {
                        pendingSleepScrollRestore = detailScrollState.value
                        selectedSleepBoxIds = it
                    },
                    onPanCells = { cells ->
                        if (cells != 0) {
                            pendingSleepScrollRestore = detailScrollState.value
                            val shifted = sleepShiftWindowDate(range, sleepWindowEndDate, cells, weekStart, zoneId)
                            sleepWindowEndDate = shifted
                            if (!sleepAnchorInsideQueryWindow(range, shifted, sleepDataWindowCenterDate)) {
                                sleepDataWindowCenterDate = shifted
                            }
                        }
                    },
                    onGestureDiagnostic = { action, deltaSnap, selectedCount ->
                        diagnostics.recordMatrixGesture(
                            matrixType = "Sleep",
                            action = action,
                            deltaSnap = deltaSnap,
                            selectedCount = selectedCount,
                            parentScrollPreserved = true
                        )
                    }
                )
                SleepSessionsSection(selectedSleepModels, zoneId = zoneId)
            } else if (isHeartRate) {
                val heartRateSummaryByDate = remember(loaded.dailyNumericSummaries) {
                    loaded.dailyNumericSummaries.mapNotNull { row ->
                        val date = runCatching { LocalDate.parse(row.localDate) }.getOrNull()
                            ?: return@mapNotNull null
                        date to row
                    }.toMap()
                }
                val latestHeartRateDate = remember(heartRateSummaryByDate, today) {
                    HeartRateDateAnalysis.latestDataDate(
                        dataDates = heartRateSummaryByDate.keys,
                        today = today
                    )
                }
                LaunchedEffect(latestHeartRateDate, loaded.start, loaded.end) {
                    if (selectedHeartRateDates.isEmpty()) {
                        val date = latestHeartRateDate ?: today
                        heartRateChartLoading = true
                        selectedHeartRateDates = setOf(date)
                        heartRateAnchorDate = date
                        heartRateVisibleRange = heartRateVisibleRangeForDates(setOf(date), zoneId)
                        heartRateSelectionVersion++
                    }
                }
                val heartRateZones = remember(userAge) { HeartRateAnalysis.referenceZones(userAge) }
                val heartRateCellByDate = remember(loaded.start, heartRateSummaryByDate, heartRateZones, today, zoneId) {
                    val firstDate = (heartRateSummaryByDate.keys.minOrNull()
                        ?: loaded.start.atZone(zoneId).toLocalDate())
                        .coerceAtMost(today)
                    HeartRateDateAnalysis.weekStripDates(firstDate, today).associateWith { date ->
                        val dailySummary = heartRateSummaryByDate[date]
                        val summary = HeartRateDateAnalysis.qualityForSummary(
                            sampleCount = dailySummary?.sampleCount ?: 0,
                            averageBpm = dailySummary?.averageValue,
                            maxBpm = dailySummary?.maxValue,
                            zones = heartRateZones,
                            minBpm = dailySummary?.minValue
                        )
                        HrDateCell(
                            date = date,
                            label = heartRateDateCellLabel(date),
                            quality = summary.quality,
                            zoneScore = summary.zoneScore,
                            sampleCount = summary.sampleCount
                        )
                    }
                }
                val heartRateWeekCells = remember(heartRateCellByDate) {
                    heartRateCellByDate.values.sortedBy { it.date }
                }
                val preferredHeartRateRange = remember(selectedHeartRateDates, zoneId) {
                    heartRateVisibleRangeForDates(selectedHeartRateDates, zoneId)
                }
                LaunchedEffect(preferredHeartRateRange, heartRateSelectionVersion) {
                    preferredHeartRateRange?.let { heartRateVisibleRange = it }
                }
                HeartRateDateSelector(
                    modifier = Modifier.rowFadeIn(2),
                    mode = heartRateMode,
                    onModeChange = { selected ->
                        heartRateMode = selected
                        heartRateAnchorDate = selectedHeartRateDates.maxOrNull()
                            ?: latestHeartRateDate
                            ?: today
                        if (selectedHeartRateDates.isEmpty()) {
                            val date = latestHeartRateDate ?: today
                            heartRateChartLoading = true
                            selectedHeartRateDates = setOf(date)
                            heartRateAnchorDate = date
                            heartRateSelectionVersion++
                        }
                    },
                    windowLabel = heartRateWindowLabel(
                        mode = heartRateMode,
                        anchorDate = heartRateAnchorDate,
                        selectedDates = selectedHeartRateDates,
                        today = today,
                        weekStart = weekStart
                    ),
                    anchorDate = heartRateAnchorDate,
                    today = today,
                    weekStart = weekStart,
                    weekCells = heartRateWeekCells,
                    cellByDate = heartRateCellByDate,
                    selectedDates = selectedHeartRateDates,
                    onDateSelected = { date ->
                        val cleaned = setOf(date.coerceAtMost(today))
                        if (cleaned != selectedHeartRateDates) {
                            heartRateChartLoading = true
                        }
                        selectedHeartRateDates = cleaned
                        heartRateAnchorDate = cleaned.maxOrNull()?.coerceAtMost(today) ?: today
                        heartRateSelectionVersion++
                    },
                    onAnchorDateChange = { date ->
                        heartRateAnchorDate = date.coerceAtMost(today)
                    },
                    onGestureDiagnostic = { action, deltaSnap, selectedCount ->
                        diagnostics.recordMatrixGesture(
                            matrixType = "HR",
                            action = action,
                            deltaSnap = deltaSnap,
                            selectedCount = selectedCount,
                            parentScrollPreserved = true
                        )
                    }
                )
                if (heartRateChartLoading) {
                    LoadingStateCard(
                        title = "Loading heart-rate chart",
                        message = "Reading and reducing samples for the selected date."
                    )
                } else if (loaded.hasNoDisplayData()) {
                    EmptyStateText(emptyReason(loaded, permissionStatus))
                } else {
                    InspectorVisualization(
                        modifier = Modifier.rowFadeIn(3),
                        detail = loaded,
                        userAge = userAge,
                        displayPreferences = displayPreferences,
                        chartBucket = chartBucket,
                        onChartBucketChange = { chartBucket = it },
                        viewportResetKey = "${loaded.descriptor.key}:${loaded.start.toEpochMilli()}:${loaded.end.toEpochMilli()}:hr-date:$heartRateSelectionVersion",
                        preferredVisibleRange = preferredHeartRateRange,
                        heartRateVisibleRange = heartRateVisibleRange,
                        onHeartRateVisibleRangeChanged = { heartRateVisibleRange = it }
                    )
                }
            } else if (loaded.hasNoDisplayData()) {
                EmptyStateText(emptyReason(loaded, permissionStatus))
            } else {
                InspectorVisualization(
                    modifier = Modifier.rowFadeIn(3),
                    detail = loaded,
                    userAge = userAge,
                    displayPreferences = displayPreferences,
                    chartBucket = chartBucket,
                    onChartBucketChange = { chartBucket = it },
                    viewportResetKey = "${loaded.descriptor.key}:${loaded.start.toEpochMilli()}:${loaded.end.toEpochMilli()}:${range.name}",
                    preferredVisibleRange = null,
                    heartRateVisibleRange = heartRateVisibleRange,
                    onHeartRateVisibleRangeChanged = { heartRateVisibleRange = it }
                )
            }
            if (!isSleep) {
                RecordsSection(
                    listKey = "${loaded.descriptor.key}:${loaded.start.toEpochMilli()}:${loaded.end.toEpochMilli()}",
                    totalCount = loaded.recordListTotalCount,
                    expanded = showRecords,
                    onToggle = { showRecords = !showRecords },
                    loadRecordPage = { offset, limit ->
                        recordQueries.inspectorRecordListPage(
                            key = dataTypeKey,
                            start = loaded.start,
                            end = loaded.end,
                            limit = limit,
                            offset = offset,
                            zoneId = zoneId,
                            unitSystem = displayPreferences.unitSystem,
                            totalCountOverride = loaded.recordListTotalCount
                        )
                    },
                    loadRecordDetails = { localRecordId ->
                        recordQueries.inspectorRecordDetails(
                            key = dataTypeKey,
                            localRecordId = localRecordId,
                            zoneId = zoneId,
                            unitSystem = displayPreferences.unitSystem
                        )
                    },
                    onRecordPageLoaded = { pageSize ->
                        diagnostics.recordRecordPage(dataTypeKey, pageSize)
                    },
                    onExpandedRecordChanged = { localRecordId ->
                        diagnostics.recordExpandedRecord(dataTypeKey, localRecordId)
                    }
                )
            }
            DetailRangeDebugFooter(
                loaded = loaded,
                zoneId = zoneId,
                syncRange = selectedSyncRange(),
                heartRateChartRange = heartRateVisibleRange
            )
        } ?: if (detailLoading) {
            LoadingStateCard(
                title = "Loading ${descriptor.displayName}",
                message = "Reading local rows for the selected range."
            )
        } else {
            EmptyState(
                title = "No ${descriptor.displayName} data",
                message = "No local rows were loaded for the selected range."
            )
        }
    }
}

@Composable
private fun DetailStatusSummary(
    detail: InspectorDetailData,
    zoneId: ZoneId,
    modifier: Modifier = Modifier
) {
    AppSection(
        modifier = modifier,
        title = "Overview",
        subtitle = "Current selected range"
    ) {
        AppActionRow {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "In Range",
                value = MetricDisplayFormatter.formatCount(detail.recordListTotalCount),
                supporting = detail.range.label
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Total Local",
                value = MetricDisplayFormatter.formatCount(detail.totalLocalRecordsForType),
                supporting = "All ranges"
            )
        }
        Text(
            uiText("Range: ${MetricDisplayFormatter.formatShortInstant(detail.start, zoneId)}-${MetricDisplayFormatter.formatShortInstant(detail.end, zoneId)}"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(uiText("Last sync: ${formatInstant(detail.lastSynced, zoneId)}"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        detail.lastSyncStatus?.let { status ->
            StatusBadge(
                "Last sync: $status",
                if (status.contains("failed", ignoreCase = true)) StatusTone.Error else StatusTone.Info
            )
        }
        detail.lastSyncError?.let {
            StatusMessageCard("Sync note: $it", tone = StatusTone.Warning)
        }
    }
}

@Composable
private fun TimeRangeSelector(
    selected: InspectorTimeRange,
    options: List<InspectorTimeRange>,
    onSelected: (InspectorTimeRange) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val visibleOptions = if (selected in options) options else listOf(selected) + options
        visibleOptions.forEach { option ->
            if (option == selected) {
                Button(onClick = { onSelected(option) }) { Text(uiText(option.label)) }
            } else {
                OutlinedButton(onClick = { onSelected(option) }) { Text(uiText(option.label)) }
            }
        }
    }
}

private fun InspectorDetailData.hasNoDisplayData(): Boolean =
    recordListTotalCount == 0 &&
        readableRows.isEmpty() &&
        chartPoints.isEmpty() &&
        dailyTotals.isEmpty() &&
        dailyNumericSummaries.isEmpty()

private fun emptyReason(
    detail: InspectorDetailData,
    permissionStatus: HealthDataPermissionStatus
): String = when {
    detail.descriptor.implementationStatus != HealthDataImplementationStatus.IMPLEMENTED ->
        "0 records. This registry type is planned and does not have a reader yet."
    permissionStatus == HealthDataPermissionStatus.UNSUPPORTED ->
        "0 records. This record type or permission is unsupported by the current SDK/device."
    permissionStatus == HealthDataPermissionStatus.MISSING ->
        "0 records shown for this range. Permission is missing, so sync is disabled."
    detail.totalLocalRecordsForType == 0 && detail.lastSynced == null ->
        "0 records found. Sync has not run for this type yet."
    detail.totalLocalRecordsForType == 0 ->
        "0 records found. Permission appears granted, but Health Connect returned no local data."
    else ->
        "0 records found for ${detail.range.label}. Try a wider range."
}

private fun permissionStatusText(status: HealthDataPermissionStatus): String = status.label

private fun implementationStatusText(status: HealthDataImplementationStatus): String =
    when (status) {
        HealthDataImplementationStatus.IMPLEMENTED -> "Ready"
        HealthDataImplementationStatus.PLANNED -> "Planned"
    }

private fun implementationTone(status: HealthDataImplementationStatus): StatusTone =
    when (status) {
        HealthDataImplementationStatus.IMPLEMENTED -> StatusTone.Success
        HealthDataImplementationStatus.PLANNED -> StatusTone.Info
    }

private fun permissionTone(status: HealthDataPermissionStatus): StatusTone =
    when (status) {
        HealthDataPermissionStatus.GRANTED -> StatusTone.Success
        HealthDataPermissionStatus.MISSING -> StatusTone.Warning
        HealthDataPermissionStatus.UNSUPPORTED -> StatusTone.Warning
        HealthDataPermissionStatus.NOT_IMPLEMENTED -> StatusTone.Info
    }

private fun visualizationLabel(type: VisualizationType): String =
    when (type) {
        VisualizationType.TIME_SERIES -> "Time-series chart"
        VisualizationType.TREND -> "Trend chart"
        VisualizationType.DAILY_AGGREGATE -> "Daily totals"
        VisualizationType.SESSION_TIMELINE -> "Sleep sessions"
        VisualizationType.MEASUREMENT_LIST -> "Measurement list"
        VisualizationType.RAW_TABLE -> "Raw table"
    }

private fun formatInstant(value: Instant?, zoneId: ZoneId): String =
    value?.let { HealthDisplayFormatter.formatInstantForUi(it, zoneId) }?.ifBlank { null } ?: "Never"

private fun lastSyncedDataText(value: Instant?, zoneId: ZoneId): String =
    "Last synced data: ${formatInstant(value, zoneId)}"

private fun syncStatusText(result: HealthDataTypeSyncResult, zoneId: ZoneId): String =
    when {
        result.terminalStatus == SyncRunStatus.TIMEOUT -> "${result.key} sync timed out: ${result.errorMessage.orEmpty()}"
        result.terminalStatus == SyncRunStatus.CANCELLED -> "${result.key} sync cancelled"
        result.errorMessage != null -> "${result.key} sync failed: ${result.errorMessage}"
        result.skippedReason != null -> "${result.key} skipped: ${result.skippedReason}"
        result.recordsInserted + result.recordsUpdated + result.aggregateRowsStored + result.valuesStored > 0 ->
            "${result.key}: inserted data, read ${result.recordsRead}, inserted ${result.recordsInserted}, " +
                "updated ${result.recordsUpdated}, duplicates ${result.recordsSkippedDuplicate}, " +
                "daily summaries ${result.aggregateRowsStored}${result.syncDataSizeText()}${result.sourceRangeText(zoneId)}${result.syncRangeText(zoneId)}"
        result.localDaysChecked > 0 && result.localDaysRequested == 0 ->
            "${result.key}: no missing local days${result.syncDataSizeText()}${result.syncRangeText(zoneId)}"
        result.recordsRead + result.aggregateRowsRead == 0 ->
            "${result.key}: no source data returned${result.syncDataSizeText()}${result.syncRangeText(zoneId)}"
        result.recordsSkippedDuplicate > 0 ->
            "${result.key}: no new data, read ${result.recordsRead}, duplicates ${result.recordsSkippedDuplicate}" +
                result.syncDataSizeText() + result.sourceRangeText(zoneId) + result.syncRangeText(zoneId)
        else -> "${result.key}: read ${result.recordsRead}, inserted ${result.recordsInserted}, " +
            "updated ${result.recordsUpdated}, duplicates ${result.recordsSkippedDuplicate}, " +
            "daily summaries ${result.aggregateRowsStored}${result.syncDataSizeText()}${result.sourceRangeText(zoneId)}${result.syncRangeText(zoneId)}"
    }

private fun HealthDataTypeSyncResult.syncDataSizeText(): String =
    ", read ${formatBytesMb(sourceBytesRead)}, wrote ${formatBytesMb(localBytesWritten)}"

private fun HealthDataTypeSyncResult.sourceRangeText(zoneId: ZoneId): String {
    val start = sourceStart ?: return ""
    val end = sourceEnd ?: return ""
    return ", source ${MetricDisplayFormatter.formatShortInstant(start, zoneId)} to ${MetricDisplayFormatter.formatShortInstant(end, zoneId)}"
}

private fun formatBytesMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb < 0.01) "<0.01 MB" else String.format(java.util.Locale.US, "%.2f MB", mb)
}

private fun HealthDataTypeSyncResult.syncRangeText(zoneId: ZoneId): String {
    val start = requestedStart ?: return ""
    val end = requestedEnd ?: return ""
    return ", range ${MetricDisplayFormatter.formatShortInstant(start, zoneId)} to ${MetricDisplayFormatter.formatShortInstant(end, zoneId)}"
}

@Composable
private fun DetailRangeDebugFooter(
    loaded: InspectorDetailData,
    zoneId: ZoneId,
    syncRange: Pair<Instant, Instant>,
    heartRateChartRange: ChartVisibleRange?
) {
    AppSection(
        title = "Showing",
        subtitle = "Range debug"
    ) {
        Text(
            uiText("Loaded: ${MetricDisplayFormatter.formatShortInstant(loaded.start, zoneId)} to ${MetricDisplayFormatter.formatShortInstant(loaded.end, zoneId)}"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            uiText("Selected sync: ${MetricDisplayFormatter.formatShortInstant(syncRange.first, zoneId)} to ${MetricDisplayFormatter.formatShortInstant(syncRange.second, zoneId)}"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        heartRateChartRange?.let { range ->
            Text(
                uiText("Chart view: ${MetricDisplayFormatter.formatShortInstant(Instant.ofEpochMilli(range.startEpochMillis), zoneId)} to ${MetricDisplayFormatter.formatShortInstant(Instant.ofEpochMilli(range.endEpochMillis), zoneId)}"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(
            uiText("Rows: ${MetricDisplayFormatter.formatCount(loaded.recordListTotalCount)} in loaded range"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            uiText(
                "Chart points: ${MetricDisplayFormatter.formatCount(loaded.chartPoints.size)}" +
                    if (loaded.chartPointsLimited) " (limited)" else ""
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun heartRateVisibleRangeForDates(
    dates: Set<LocalDate>,
    zoneId: ZoneId
): ChartVisibleRange? =
    HeartRateDateAnalysis.visibleRangeForDates(dates, zoneId)?.let { range ->
        ChartVisibleRange(
            startEpochMillis = range.start.toEpochMilli(),
            endEpochMillis = range.end.toEpochMilli()
        )
    }

private fun heartRateWindowLabel(
    mode: HrDateSelectionMode,
    anchorDate: LocalDate,
    selectedDates: Set<LocalDate>,
    today: LocalDate,
    weekStart: DayOfWeek
): String {
    return when (mode) {
        HrDateSelectionMode.WEEK -> {
            val labelAnchor = selectedDates.maxOrNull() ?: anchorDate
            val dates = HeartRateDateAnalysis.visibleDates(
                anchorDate = labelAnchor,
                today = today,
                mode = mode,
                weekStart = weekStart
            )
            compactDateRange(dates.firstOrNull(), dates.lastOrNull())
        }
        HrDateSelectionMode.MONTH -> DateTimeFormatter.ofPattern("MMM yyyy")
            .format(YearMonth.from(anchorDate))
    }
}

private fun heartRateDateCellLabel(date: LocalDate): String =
    DateTimeFormatter.ofPattern("E d").format(date)

private fun compactDateRange(start: LocalDate?, end: LocalDate?): String {
    if (start == null || end == null) return "No dates"
    if (start == end) return compactDate(start)
    return if (start.month == end.month && start.year == end.year) {
        "${DateTimeFormatter.ofPattern("MMM d").format(start)}-${DateTimeFormatter.ofPattern("d").format(end)}"
    } else {
        "${compactDate(start)}-${compactDate(end)}"
    }
}

private fun compactDate(date: LocalDate): String =
    DateTimeFormatter.ofPattern("MMM d").format(date)

@Composable
private fun EmptyStateText(message: String) {
    Text(
        text = uiText(message),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
