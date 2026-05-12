package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthCsvRow
import com.example.healthconnectandroid.data.HealthDailyAggregateRow
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

private const val INSPECTOR_TABLE_ROW_LIMIT = 200
private const val INSPECTOR_SLEEP_ROW_LIMIT = 12000
private const val INSPECTOR_CHART_ROW_LIMIT = 1200
private const val INSPECTOR_HEART_RATE_CHART_ROW_LIMIT = 150000

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
        weekStart: DayOfWeek = DayOfWeek.MONDAY
    ): InspectorDetailData {
        return inspectorDetailForWindow(
            key = key,
            range = range,
            start = range.startBefore(end, zoneId),
            end = end,
            zoneId = zoneId,
            unitSystem = unitSystem,
            weekStart = weekStart
        )
    }

    suspend fun inspectorDetailForWindow(
        key: String,
        range: InspectorTimeRange,
        start: Instant,
        end: Instant,
        zoneId: ZoneId = ZoneId.systemDefault(),
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC,
        weekStart: DayOfWeek = DayOfWeek.MONDAY
    ): InspectorDetailData {
        val descriptor = HealthDataTypeRegistry.require(key)
        val syncSummary = syncDao.latestSummaries().firstOrNull { it.recordType == key }
        val totalLocalRecordsForType = healthDao.countRecordsForType(key)
        val recordListTotalCount = healthDao.countInspectorRowsForTypeRange(
            recordType = key,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli()
        )
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
        val numericRows = if (needsNumericRows) {
            val chartRowLimit = inspectorChartRowLimit(key)
            healthDao.inspectorNumericRowsForTypeRange(
                recordType = key,
                startEpochMillis = start.toEpochMilli(),
                endEpochMillis = end.toEpochMilli(),
                limit = chartRowLimit + 1
            ).distinctBy { "${it.localRecordId}:${it.valueKey}" }
        } else {
            emptyList()
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
            val chartRowLimit = inspectorChartRowLimit(key)
            numericRows
                .take(chartRowLimit)
                .asChartPoints(descriptor)
        }
        val restingHeartRateEstimate = if (key == HealthDataTypeKeys.HEART_RATE) {
            HeartRateAnalysis.estimateRestingHeartRate(
                bpmSamples = numericRows.mapNotNull { it.numericValue },
                rangeLabel = range.label,
                recordedBpm = recordedRestingHeartRate(start, end)
            )
        } else {
            null
        }
        return InspectorDetailData(
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
            chartPointsLimited = numericRows.size > inspectorChartRowLimit(key),
            restingHeartRateEstimate = restingHeartRateEstimate
        )
    }

    private fun inspectorDisplayRowLimit(key: String): Int =
        if (key == HealthDataTypeKeys.SLEEP_SESSION) INSPECTOR_SLEEP_ROW_LIMIT else INSPECTOR_TABLE_ROW_LIMIT

    private suspend fun recordedRestingHeartRate(start: Instant, end: Instant): Double? =
        healthDao.inspectorNumericRowsForTypeRange(
            recordType = HealthDataTypeKeys.RESTING_HEART_RATE,
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli(),
            limit = 1
        ).firstOrNull()?.numericValue

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

    private fun List<HealthCsvRow>.asChartPoints(
        descriptor: HealthDataTypeDescriptor
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
                val readable = HealthDisplayFormatter.toReadable(row, descriptor)
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
