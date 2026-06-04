package com.example.healthconnectandroid.ui.charts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.HeartRateAnalysis
import com.example.healthconnectandroid.hc.InspectorDetailData
import com.example.healthconnectandroid.hc.InspectorPeriodTotal
import com.example.healthconnectandroid.hc.VisualizationType
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyStateText
import com.example.healthconnectandroid.ui.MetricCard
import com.example.healthconnectandroid.ui.format.DisplayPreferences
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import com.example.healthconnectandroid.ui.i18n.uiText
import com.example.healthconnectandroid.ui.sleep.SessionTimeline

enum class ChartBucket(val label: String) {
    RAW("Raw"),
    HOURLY("Hourly"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

data class ChartVisibleRange(
    val startEpochMillis: Long,
    val endEpochMillis: Long
)

@Composable
fun InspectorVisualization(
    modifier: Modifier = Modifier,
    detail: InspectorDetailData,
    userAge: Int?,
    displayPreferences: DisplayPreferences,
    chartBucket: ChartBucket,
    onChartBucketChange: (ChartBucket) -> Unit,
    viewportResetKey: Any? = detail.descriptor.key,
    preferredVisibleRange: ChartVisibleRange? = null,
    heartRateVisibleRange: ChartVisibleRange?,
    onHeartRateVisibleRangeChanged: (ChartVisibleRange) -> Unit
) {
    val descriptor = detail.descriptor
    val unitSystem = displayPreferences.unitSystem
    val zoneId = displayPreferences.zoneId
    val labels = chartLabelsFor(descriptor, detail.chartPoints.firstOrNull()?.unit)
    val availableBuckets = ChartBucketOptions.availableFor(descriptor)
    val effectiveBucket = chartBucket.takeIf { it in availableBuckets } ?: availableBuckets.first()
    AppSection(
        modifier = modifier,
        title = labels.title
    ) {
        if (availableBuckets.size > 1) {
            ChartBucketSelector(
                selected = effectiveBucket,
                options = availableBuckets,
                onSelected = onChartBucketChange
            )
        }
        when (descriptor.visualizationType) {
            VisualizationType.DAILY_AGGREGATE -> {
                val bucketedRows = remember(detail.dailyTotals, effectiveBucket, displayPreferences.weekStart) {
                    bucketDailyTotals(detail.dailyTotals, effectiveBucket, displayPreferences.weekStart)
                }
                val displayRows = remember(bucketedRows, descriptor.key, unitSystem) {
                    bucketedRows.map { it.toDisplayUnits(descriptor.key, unitSystem) }
                }
                val displayLabels = chartLabelsFor(descriptor, displayRows.firstOrNull()?.unit)
                if (displayRows.isNotEmpty()) {
                    DailyBarChart(
                        title = displayLabels.title,
                        xAxisLabel = if (effectiveBucket == ChartBucket.DAILY) "Date" else effectiveBucket.label,
                        yAxisLabel = displayLabels.yAxisLabel,
                        rows = displayRows,
                        sourceText = detail.dailyTotalsSource
                    )
                    if (effectiveBucket == ChartBucket.DAILY) {
                        WeeklyTotals(detail.weeklyTotals)
                    }
                } else {
                    EmptyStateText("No ${effectiveBucket.label.lowercase()} totals in this range")
                }
            }
            VisualizationType.SESSION_TIMELINE -> SessionTimeline(detail.readableRows)
            VisualizationType.TIME_SERIES,
            VisualizationType.TREND,
            VisualizationType.MEASUREMENT_LIST -> {
                val bucketedPoints = remember(detail.chartPoints, effectiveBucket, zoneId, displayPreferences.weekStart) {
                    bucketChartPoints(
                        points = detail.chartPoints,
                        bucket = effectiveBucket,
                        zoneId = zoneId,
                        weekStart = displayPreferences.weekStart
                    )
                }
                val displayPoints = remember(bucketedPoints, descriptor.key, unitSystem) {
                    bucketedPoints.map { it.toDisplayUnits(descriptor.key, unitSystem) }
                }
                val displayLabels = chartLabelsFor(descriptor, displayPoints.firstOrNull()?.unit)
                if (descriptor.key == HealthDataTypeKeys.HEART_RATE) {
                    val visibleRange = heartRateVisibleRange
                        ?: bucketedPoints.firstOrNull()?.let { first ->
                            bucketedPoints.lastOrNull()?.let { last ->
                                ChartVisibleRange(first.epochMillis, last.epochMillis)
                            }
                        }
                    val visibleEstimate = remember(displayPoints, visibleRange) {
                        HeartRateAnalysis.estimateRestingHeartRate(
                            bpmSamples = displayPoints
                                .filter { point ->
                                    visibleRange == null ||
                                        point.epochMillis in visibleRange.startEpochMillis..visibleRange.endEpochMillis
                                }
                                .map { it.value },
                            rangeLabel = "visible range",
                            recordedBpm = null
                        )
                    }
                    RestingHeartRateVisualizationSummary(
                        estimate = visibleEstimate,
                        recordedEstimate = detail.restingHeartRateEstimate
                    )
                }
                if (bucketedPoints.isNotEmpty()) {
                    LineChart(
                        title = if (effectiveBucket == ChartBucket.RAW) displayLabels.title else "${displayLabels.title} (${effectiveBucket.label.lowercase()})",
                        xAxisLabel = chartXAxisLabel(displayLabels.xAxisLabel, effectiveBucket),
                        yAxisLabel = displayLabels.yAxisLabel,
                        points = displayPoints,
                        zoneId = zoneId,
                        domainStartEpochMillis = if (descriptor.key == HealthDataTypeKeys.HEART_RATE) {
                            detail.start.toEpochMilli()
                        } else {
                            null
                        },
                        domainEndEpochMillis = if (descriptor.key == HealthDataTypeKeys.HEART_RATE) {
                            detail.end.toEpochMilli()
                        } else {
                            null
                        },
                        viewportResetKey = viewportResetKey,
                        initialFullRange = descriptor.key == HealthDataTypeKeys.HEART_RATE,
                        preferredVisibleRange = if (descriptor.key == HealthDataTypeKeys.HEART_RATE) {
                            preferredVisibleRange
                        } else {
                            null
                        },
                        heartRateZones = if (descriptor.key == HealthDataTypeKeys.HEART_RATE) {
                            HeartRateAnalysis.referenceZones(userAge)
                        } else {
                            null
                        },
                        onVisibleRangeChanged = if (descriptor.key == HealthDataTypeKeys.HEART_RATE) {
                            onHeartRateVisibleRangeChanged
                        } else {
                            null
                        }
                    )
                } else {
                    EmptyStateText("No numeric measurements available for this chart.")
                }
            }
            VisualizationType.RAW_TABLE -> EmptyStateText("No chart for this type.")
        }
    }
}

@Composable
private fun ChartBucketSelector(
    selected: ChartBucket,
    options: List<ChartBucket>,
    onSelected: (ChartBucket) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            if (option == selected) {
                Button(onClick = { onSelected(option) }) { Text(uiText(option.label)) }
            } else {
                OutlinedButton(onClick = { onSelected(option) }) { Text(uiText(option.label)) }
            }
        }
    }
}

@Composable
private fun RestingHeartRateVisualizationSummary(
    estimate: com.example.healthconnectandroid.hc.RestingHeartRateEstimate,
    recordedEstimate: com.example.healthconnectandroid.hc.RestingHeartRateEstimate?
) {
    MetricCard(
        label = "Estimated Resting Heart Rate (RHR)",
        value = estimate.displayText,
        supporting = "Median of the lowest 10% of valid data"
    )
    recordedEstimate?.recordedBpm?.let { recorded ->
        Text(
            uiText("Recorded resting HR: ${MetricDisplayFormatter.formatBpm(recorded)}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Text(
        uiText("${MetricDisplayFormatter.formatCount(estimate.sampleCount)} visible samples"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun WeeklyTotals(rows: List<InspectorPeriodTotal>) {
    if (rows.isEmpty()) return
    Spacer(Modifier.height(8.dp))
    Text(uiText("Weekly summaries"), style = MaterialTheme.typography.titleSmall)
    rows.takeLast(8).forEach { row ->
        Text("${row.label}: ${formatNumber(row.total)} ${row.unit.orEmpty()}".trim())
    }
}
