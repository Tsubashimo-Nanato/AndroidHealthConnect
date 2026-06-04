package com.example.healthconnectandroid.ui.sleep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.InspectorDetailData
import com.example.healthconnectandroid.hc.InspectorTimeRange
import com.example.healthconnectandroid.hc.SleepQualityMatrixModel
import com.example.healthconnectandroid.hc.SleepSessionAnalyzer
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyStateText
import com.example.healthconnectandroid.ui.MetricCard
import com.example.healthconnectandroid.ui.SegmentedSwitch
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
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
    AppSection(
        modifier = Modifier.rowFadeIn(1),
        title = "Summary"
    ) {
        if (metrics.sessionCount == 0) {
            EmptyStateText("No sleep sessions in this range")
            return@AppSection
        }
        AppActionRow {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Average sleep",
                value = metrics.averageDuration?.let(MetricDisplayFormatter::formatDurationCompact) ?: "N/A",
                supporting = detail.range.label,
                tone = StatusTone.Neutral
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Sessions",
                value = MetricDisplayFormatter.formatCount(metrics.sessionCount),
                supporting = "${MetricDisplayFormatter.formatCount(metrics.napCount)} naps",
                tone = StatusTone.Neutral
            )
        }
        AppActionRow {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Typical quality",
                value = metrics.typicalQuality.label,
                supporting = detail.range.label,
                tone = sleepQualityStatusTone(metrics.typicalQuality)
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Last session",
                value = metrics.lastSessionDuration?.let(MetricDisplayFormatter::formatDurationCompact) ?: "N/A",
                supporting = "Most recent",
                tone = StatusTone.Neutral
            )
        }
    }
}

@Composable
fun SleepVisualizationSection(
    selectedRange: InspectorTimeRange,
    onRangeSelected: (InspectorTimeRange) -> Unit,
    windowLabel: String,
    model: SleepQualityMatrixModel,
    selectedBoxIds: Set<String>,
    onSelectedBoxIdsChange: (Set<String>) -> Unit,
    onPanCells: (Int) -> Unit,
    onGestureDiagnostic: (action: String, deltaSnap: Int?, selectedCount: Int) -> Unit = { _, _, _ -> }
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .rowFadeIn(2),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SleepRangeSwitch(selected = selectedRange, onSelected = onRangeSelected)
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            SleepMatrixPicker(
                model = model,
                selectedBoxIds = selectedBoxIds,
                onSelectedBoxIdsChange = onSelectedBoxIdsChange,
                onGestureDiagnostic = onGestureDiagnostic,
                modifier = Modifier.horizontalWindowSwipe { delta ->
                    onPanCells(delta)
                    onGestureDiagnostic("pan", delta, selectedBoxIds.size)
                }
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
