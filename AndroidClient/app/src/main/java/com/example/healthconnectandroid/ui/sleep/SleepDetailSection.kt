package com.example.healthconnectandroid.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.data.HealthDailyAggregateRow
import com.example.healthconnectandroid.hc.InspectorDetailData
import com.example.healthconnectandroid.hc.InspectorTimeRange
import com.example.healthconnectandroid.hc.SleepDaySummary
import com.example.healthconnectandroid.hc.SleepQualityMatrixModel
import com.example.healthconnectandroid.hc.SleepSessionAnalyzer
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyStateText
import com.example.healthconnectandroid.ui.MetricCard
import com.example.healthconnectandroid.ui.SegmentedSwitch
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.charts.DailyBarChart
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import com.example.healthconnectandroid.ui.gesture.horizontalWindowSwipe
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SleepInsightSummary(
    detail: InspectorDetailData,
    sessionModels: List<SleepSessionUiModel>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val inputs = remember(sessionModels) { sessionModels.map { it.toSleepInput() } }
    val metrics = remember(inputs, startDate, endDate, zoneId) {
        SleepSessionAnalyzer.summarizeRange(
            sessions = inputs,
            startDate = startDate,
            endDate = endDate,
            zoneId = zoneId
        )
    }
    val daySummaries = remember(inputs, startDate, endDate, zoneId) {
        SleepSessionAnalyzer.dailySummaries(inputs, startDate, endDate, zoneId)
    }
    val chartRows = remember(daySummaries) { sleepDurationChartRows(daySummaries) }
    val barColorsByDate = remember(daySummaries) {
        daySummaries.associate { summary ->
            summary.localDate.toString() to sleepDurationChartBarColor(summary.totalDuration.toMinutes())
        }
    }
    AppSection(
        modifier = Modifier.rowFadeIn(1),
        title = "Summary"
    ) {
        if (metrics.averageSessionDuration == null) {
            EmptyStateText("No sleep sessions in this range")
            return@AppSection
        }
        AppActionRow {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Average per sleep",
                value = MetricDisplayFormatter.formatDurationCompact(metrics.averageSessionDuration),
                supporting = detail.range.label,
                tone = StatusTone.Neutral
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Last sleep",
                value = metrics.lastSessionDuration?.let(MetricDisplayFormatter::formatDurationCompact) ?: "N/A",
                supporting = "Most recent",
                tone = StatusTone.Neutral
            )
        }
        Text(
            uiText("Sleep duration"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        DailyBarChart(
            title = "Sleep duration",
            xAxisLabel = "Date",
            yAxisLabel = "Hours",
            rows = chartRows,
            sourceText = "Local sleep sessions",
            barColorsByDate = barColorsByDate
        )
    }
}

@Composable
fun SleepVisualizationSection(
    selectedRange: InspectorTimeRange,
    onRangeSelected: (InspectorTimeRange) -> Unit,
    windowLabel: String,
    model: SleepQualityMatrixModel,
    sessionModels: List<SleepSessionUiModel>,
    selectedBoxIds: Set<String>,
    onSelectedBoxIdsChange: (Set<String>) -> Unit,
    onPanCells: (Int) -> Unit,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onGestureDiagnostic: (action: String, deltaSnap: Int?, selectedCount: Int) -> Unit = { _, _, _ -> }
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SleepRangeSwitch(selected = selectedRange, onSelected = onRangeSelected)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalWindowSwipe(
                    onSettled = { delta ->
                        val action = if (delta == 0) "snap-back" else "pan"
                        onGestureDiagnostic(action, delta, selectedBoxIds.size)
                    },
                    onSwipe = onPanCells
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { onPanCells(-1) }) {
                Text(uiText("Prev"), maxLines = 1)
            }
            Text(
                windowLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            TextButton(onClick = { onPanCells(1) }) {
                Text(uiText("Next"), maxLines = 1)
            }
        }
        SleepTimeline(
            model = model,
            sessions = sessionModels,
            selectedBoxIds = selectedBoxIds,
            onSelectedBoxIdsChange = onSelectedBoxIdsChange,
            zoneId = zoneId
        )
        selectedSleepLabel(model, selectedBoxIds)?.let { label ->
            Text(
                uiText(label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

internal fun sleepDurationChartRows(summaries: List<SleepDaySummary>): List<HealthDailyAggregateRow> =
    summaries.mapNotNull { summary ->
        val minutes = summary.totalDuration.toMinutes()
        if (summary.sessionCount == 0 || minutes <= 0) return@mapNotNull null
        HealthDailyAggregateRow(
            localDate = summary.localDate.toString(),
            total = minutes / 60.0,
            unit = "h"
        )
    }

@Composable
private fun SleepRangeSwitch(
    selected: InspectorTimeRange,
    onSelected: (InspectorTimeRange) -> Unit
) {
    SegmentedSwitch(
        options = listOf(InspectorTimeRange.WEEKLY, InspectorTimeRange.MONTHLY),
        selected = selected,
        label = { it.label },
        onSelected = onSelected
    )
}
