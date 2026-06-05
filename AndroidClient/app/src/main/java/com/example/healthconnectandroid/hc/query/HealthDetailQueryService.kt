package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthCsvRow
import com.example.healthconnectandroid.data.HealthDailyAggregateRow
import com.example.healthconnectandroid.data.HealthDailyNumericSummaryRow
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.HealthDisplayFormatter
import com.example.healthconnectandroid.hc.HeartRateAnalysis
import com.example.healthconnectandroid.hc.InspectorChartPoint
import com.example.healthconnectandroid.hc.InspectorDetailData
import com.example.healthconnectandroid.hc.InspectorPeriodTotal
import com.example.healthconnectandroid.hc.InspectorTimeRange
import com.example.healthconnectandroid.hc.PreferredChartSource
import com.example.healthconnectandroid.hc.VisualizationType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val INSPECTOR_TABLE_ROW_LIMIT = 200
private const val INSPECTOR_SLEEP_ROW_LIMIT = 12000
private const val INSPECTOR_CHART_ROW_LIMIT = 1200
private const val INSPECTOR_HEART_RATE_CHART_ROW_LIMIT = 3600
private const val INSPECTOR_HEART_RATE_CHART_PAGE_SIZE = 5000
private const val HEART_RATE_METRIC = "heart_rate"

class HealthDetailQueryService(
    private val db: AppDb
) {
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()
    private val aggregateDao = db.healthAggregateDao()

    suspend fun inspectorDetail(
        key: String,
        range: InspectorTimeRange,
        end: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        chartStart: Instant? = null,
        chartEnd: Instant? = null
    ): InspectorDetailData {
        return inspectorDetailForWindow(
            key = key,
            range = range,
            start = range.startBefore(end, zoneId),
            end = end,
            zoneId = zoneId,
            unitSystem = unitSystem,
            weekStart = weekStart,
            chartStart = chartStart,
            chartEnd = chartEnd
        )
    }

    suspend fun inspectorDetailForWindow(
        key: String,
        range: InspectorTimeRange,
        start: Instant,
        end: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        chartStart: Instant? = null,
        chartEnd: Instant? = null
    ): InspectorDetailData = withContext(Dispatchers.Default) {
        val descriptor = HealthDataTypeRegistry.require(key)
        val syncSummary = syncDao.latestSummaryForType(key)
        val totalLocalRecordsForType = healthDao.countRecordsForType(key)
        val dailyNumericSummaries = if (key == HealthDataTypeKeys.HEART_RATE) {
            heartRateDailySummariesForZone(start, end, zoneId)
        } else {
            emptyList()
        }
        val recordListTotalCount = if (key == HealthDataTypeKeys.HEART_RATE) {
            dailyNumericSummaries.sumOf { it.sampleCount }
        } else {
            healthDao.countInspectorRowsForTypeRange(
                recordType = key,
                startEpochMillis = start.toEpochMilli(),
                endEpochMillis = end.toEpochMilli()
            )
        }
        val displayRowLimit = inspectorDisplayRowLimit(key)
        val displayRows = if (key == HealthDataTypeKeys.SLEEP_SESSION) {
            healthDao.inspectorDisplayRowsForTypeRange(
                recordType = key,
                startEpochMillis = start.toEpochMilli(),
                endEpochMillis = end.toEpochMilli(),
                limit = displayRowLimit + 1
            ).distinctBy { "${it.localRecordId}:${it.valueKey}" }
        } else {
            emptyList()
        }
        val needsNumericRows = !descriptor.aggregationPreferred &&
            descriptor.visualizationType in setOf(
                VisualizationType.TIME_SERIES,
                VisualizationType.TREND,
                VisualizationType.MEASUREMENT_LIST
            )
        val chartWindow = heartRateChartWindow(
            requestedStart = start,
            requestedEnd = end,
            selectedStart = chartStart,
            selectedEnd = chartEnd,
            dailySummaries = dailyNumericSummaries,
            zoneId = zoneId
        )
        val numericStart = chartWindow?.first ?: start
        val numericEnd = chartWindow?.second ?: end
        val numericRows = if (needsNumericRows && key == HealthDataTypeKeys.HEART_RATE) {
            loadHeartRateNumericRowsForChart(numericStart, numericEnd)
        } else if (needsNumericRows) {
            loadGenericNumericRowsForChart(key, numericStart, numericEnd)
        } else {
            NumericChartRows.Empty
        }
        val dailyTotalsWithSource = if (descriptor.aggregationPreferred) {
            val startDate = start.atZone(zoneId).toLocalDate()
            val endDate = end.minusMillis(1).atZone(zoneId).toLocalDate()
            dailyTotalsForInspector(
                descriptor = descriptor,
                startDate = startDate,
                endDate = endDate
            )
        } else {
            emptyList<HealthDailyAggregateRow>() to null
        }
        val dailyTotals = dailyTotalsWithSource.first
        val dailyTotalsSource = dailyTotalsWithSource.second
        val chartPoints = if (descriptor.aggregationPreferred) {
            emptyList()
        } else {
            numericRows.chartRows
                .asChartPoints(descriptor, zoneId, unitSystem)
        }
        val restingHeartRateEstimate = if (key == HealthDataTypeKeys.HEART_RATE) {
            HeartRateAnalysis.estimateRestingHeartRate(
                bpmSamples = emptyList(),
                rangeLabel = range.label,
                recordedBpm = recordedRestingHeartRate(start, end)
            )
        } else {
            null
        }
        InspectorDetailData(
            descriptor = descriptor,
            range = range,
            start = start,
            end = end,
            totalLocalRecordsForType = totalLocalRecordsForType,
            lastSynced = syncSummary?.lastFinishedEpochMillis?.let(Instant::ofEpochMilli),
            lastSyncStatus = syncSummary?.lastStatus,
            lastSyncError = syncSummary?.lastErrorMessage,
            rows = emptyList(),
            chartPoints = chartPoints,
            dailyTotals = dailyTotals,
            dailyNumericSummaries = dailyNumericSummaries,
            dailyTotalsSource = dailyTotalsSource,
            weeklyTotals = weeklyTotalsFromDaily(dailyTotals, weekStart),
            readableRows = displayRows
                .take(displayRowLimit)
                .map {
                    HealthDisplayFormatter.toReadable(
                        row = it,
                        descriptor = descriptor,
                        zoneId = zoneId,
                        unitSystem = unitSystem
                    )
                },
            recordListTotalCount = recordListTotalCount,
            tableRows = emptyList(),
            tableRowsLimited = recordListTotalCount > displayRowLimit,
            chartPointsLimited = numericRows.limited,
            restingHeartRateEstimate = restingHeartRateEstimate
        )
    }

    private fun inspectorDisplayRowLimit(key: String): Int =
        if (key == HealthDataTypeKeys.SLEEP_SESSION) INSPECTOR_SLEEP_ROW_LIMIT else INSPECTOR_TABLE_ROW_LIMIT

    private fun heartRateChartWindow(
        requestedStart: Instant,
        requestedEnd: Instant,
        selectedStart: Instant?,
        selectedEnd: Instant?,
        dailySummaries: List<HealthDailyNumericSummaryRow>,
        zoneId: ZoneId
    ): Pair<Instant, Instant>? {
        if (selectedStart != null && selectedEnd != null && selectedStart.isBefore(selectedEnd)) {
            val start = maxInstant(requestedStart, selectedStart)
            val end = minInstant(requestedEnd, selectedEnd)
            return if (start.isBefore(end)) start to end else null
        }
        val latestDate = dailySummaries
            .mapNotNull { row -> runCatching { LocalDate.parse(row.localDate) }.getOrNull() }
            .maxOrNull()
            ?: return null
        val start = maxInstant(requestedStart, latestDate.atStartOfDay(zoneId).toInstant())
        val end = minInstant(requestedEnd, latestDate.plusDays(1).atStartOfDay(zoneId).toInstant())
        return if (start.isBefore(end)) start to end else null
    }

    private suspend fun recordedRestingHeartRate(start: Instant, end: Instant): Double? =
        healthDao.inspectorNumericRowsForTypeRange(
            recordType = HealthDataTypeKeys.RESTING_HEART_RATE,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli(),
            limit = 1
        ).firstOrNull()?.numericValue

    private suspend fun loadGenericNumericRowsForChart(
        key: String,
        start: Instant,
        end: Instant
    ): NumericChartRows {
        val limit = inspectorChartRowLimit(key)
        val rows = healthDao.inspectorNumericRowsForTypeRange(
            recordType = key,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli(),
            limit = limit + 1
        ).distinctBy { "${it.localRecordId}:${it.valueKey}" }
        val chartRows = rows.take(limit)
        return NumericChartRows(
            chartRows = chartRows,
            limited = rows.size > limit
        )
    }

    private suspend fun loadHeartRateNumericRowsForChart(
        start: Instant,
        end: Instant
    ): NumericChartRows {
        val startEpochMillis = start.toEpochMilli()
        val endEpochMillis = end.toEpochMilli()
        val exactRows = ArrayList<HealthCsvRow>(INSPECTOR_HEART_RATE_CHART_ROW_LIMIT + 1)
        val downsampler = HealthCsvRowMinMaxDownsampler(
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis,
            targetCount = INSPECTOR_HEART_RATE_CHART_ROW_LIMIT
        )
        var limited = false
        loadHeartRateNumericRowsAsc(
            startEpochMillis = startEpochMillis,
            endEpochMillis = endEpochMillis,
            onRow = { row ->
                if (!limited) {
                    exactRows.add(row)
                    if (exactRows.size > INSPECTOR_HEART_RATE_CHART_ROW_LIMIT) {
                        limited = true
                    }
                }
                downsampler.offer(row)
            }
        )
        if (exactRows.isEmpty()) return NumericChartRows.Empty
        return NumericChartRows(
            chartRows = if (limited) downsampler.rows() else exactRows,
            limited = limited
        )
    }

    private suspend fun loadHeartRateNumericRowsAsc(
        startEpochMillis: Long,
        endEpochMillis: Long,
        onRow: (HealthCsvRow) -> Unit
    ) {
        var afterEpochMillis: Long? = null
        var afterLocalValueId = 0L
        while (true) {
            val page = healthDao.inspectorNumericRowsForMetricEpochRangeAscAfter(
                recordType = HealthDataTypeKeys.HEART_RATE,
                metric = HEART_RATE_METRIC,
                startEpochMillis = startEpochMillis,
                endEpochMillis = endEpochMillis,
                limit = INSPECTOR_HEART_RATE_CHART_PAGE_SIZE,
                afterEpochMillis = afterEpochMillis,
                afterLocalValueId = afterLocalValueId
            )
            if (page.isEmpty()) break
            page.forEach { row ->
                onRow(row)
            }
            val last = page.last()
            afterEpochMillis = last.valueStartEpochMillis ?: last.recordStartEpochMillis
            afterLocalValueId = last.localValueId
            if (page.size < INSPECTOR_HEART_RATE_CHART_PAGE_SIZE) break
        }
    }

    private suspend fun heartRateDailySummariesForZone(
        start: Instant,
        end: Instant,
        zoneId: ZoneId
    ): List<HealthDailyNumericSummaryRow> {
        if (!start.isBefore(end)) return emptyList()
        val firstDate = start.atZone(zoneId).toLocalDate()
        val lastDate = end.minusMillis(1).atZone(zoneId).toLocalDate()
        val summaries = mutableListOf<HealthDailyNumericSummaryRow>()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            val dayStart = maxInstant(start, date.atStartOfDay(zoneId).toInstant())
            val dayEnd = minInstant(end, date.plusDays(1).atStartOfDay(zoneId).toInstant())
            if (dayStart.isBefore(dayEnd)) {
                val summary = healthDao.numericSummaryForMetricEpochRange(
                    metric = HEART_RATE_METRIC,
                    localDate = date.toString(),
                    startEpochMillis = dayStart.toEpochMilli(),
                    endEpochMillis = dayEnd.toEpochMilli()
                )
                if (summary.sampleCount > 0) {
                    summaries += summary
                }
            }
            date = date.plusDays(1)
        }
        return summaries
    }

    private suspend fun dailyTotalsForInspector(
        descriptor: HealthDataTypeDescriptor,
        startDate: LocalDate,
        endDate: LocalDate
    ): Pair<List<HealthDailyAggregateRow>, String?> {
        if (descriptor.preferredChartSource == PreferredChartSource.AGGREGATE) {
            val aggregateRows = aggregateDao.dailyTotalsForType(
                recordType = descriptor.key,
                startDate = startDate.toString(),
                endDate = endDate.toString()
            )
            if (aggregateRows.isNotEmpty()) {
                return aggregateRows to "Health Connect aggregate"
            }
        }

        val localRows = healthDao.dailyTotalsForType(
            recordType = descriptor.key,
            startDate = startDate.toString(),
            endDate = endDate.toString()
        )
        return localRows to if (localRows.isNotEmpty()) "Local raw records" else null
    }

    private fun weeklyTotalsFromDaily(
        dailyTotals: List<HealthDailyAggregateRow>,
        weekStartDay: DayOfWeek
    ): List<InspectorPeriodTotal> =
        dailyTotals
            .mapNotNull { row ->
                val date = runCatching { LocalDate.parse(row.localDate) }.getOrNull()
                    ?: return@mapNotNull null
                val weekStart = date.with(TemporalAdjusters.previousOrSame(weekStartDay))
                weekStart to row
            }
            .groupBy { it.first }
            .toSortedMap()
            .map { (weekStart, rows) ->
                InspectorPeriodTotal(
                    label = "Week of $weekStart",
                    total = rows.sumOf { it.second.total },
                    unit = rows.mapNotNull { it.second.unit }.distinct().singleOrNull()
                        ?: rows.lastOrNull()?.second?.unit
                )
            }

    private data class NumericChartRows(
        val chartRows: List<HealthCsvRow>,
        val limited: Boolean
    ) {
        companion object {
            val Empty = NumericChartRows(emptyList(), limited = false)
        }
    }

    private fun List<HealthCsvRow>.asChartPoints(
        descriptor: HealthDataTypeDescriptor,
        zoneId: ZoneId,
        unitSystem: UnitSystemPreference
    ): List<InspectorChartPoint> {
        val preferredMetric = when (descriptor.key) {
            HealthDataTypeKeys.BLOOD_PRESSURE -> "blood_pressure"
            HealthDataTypeKeys.SLEEP_SESSION -> "duration"
            else -> null
        }
        return asSequence()
            .filter { row -> preferredMetric == null || row.metric == preferredMetric }
            .mapNotNull { row ->
                val value = row.numericValue ?: return@mapNotNull null
                val time = row.valueStartEpochMillis ?: row.recordStartEpochMillis
                val readable = HealthDisplayFormatter.toReadable(
                    row = row,
                    descriptor = descriptor,
                    zoneId = zoneId,
                    unitSystem = unitSystem
                )
                InspectorChartPoint(
                    epochMillis = time,
                    value = value,
                    value2 = row.secondaryNumericValue,
                    label = row.categoryOrStage ?: row.label,
                    unit = readable.unit ?: row.unit,
                    sourceText = readable.sourceText,
                    primaryText = readable.primaryText,
                    secondaryText = readable.secondaryText,
                    rawDetailsText = readable.rawDetailsText
                )
            }
            .toList()
    }

}

internal fun inspectorChartRowLimit(key: String): Int =
    if (key == HealthDataTypeKeys.HEART_RATE) {
        INSPECTOR_HEART_RATE_CHART_ROW_LIMIT
    } else {
        INSPECTOR_CHART_ROW_LIMIT
    }

private fun maxInstant(a: Instant, b: Instant): Instant = if (a.isAfter(b)) a else b

private fun minInstant(a: Instant, b: Instant): Instant = if (a.isBefore(b)) a else b
