package com.example.healthconnectandroid.ui.sleep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.ReadableHealthRecord
import com.example.healthconnectandroid.hc.SleepQualityBand
import com.example.healthconnectandroid.hc.SleepSessionAnalysis
import com.example.healthconnectandroid.hc.SleepTagTone
import com.example.healthconnectandroid.hc.sleepSessionDate
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.ZoneId

@Composable
fun SleepSessionCard(
    model: SleepSessionUiModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val session = model.session
    val stages = model.stages
    val analysis = model.analysis
    val supportingText = sleepSessionSupportingText(model, zoneId)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() },
        colors = CardDefaults.cardColors(
            containerColor = sleepQualityContainerColor(analysis.qualityBand)
        )
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(uiText(session.primaryText), style = MaterialTheme.typography.titleSmall)
            if (supportingText.isNotBlank()) {
                Text(
                    uiText(supportingText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SleepTagRow(analysis, stages.size)
            if (expanded) {
                SleepStagePlot(session = session, stages = stages, zoneId = zoneId)
                stages.forEach { stage ->
                    SleepStageCard(stage)
                }
            }
        }
    }
}

internal fun sleepSessionSupportingText(model: SleepSessionUiModel, zoneId: ZoneId): String {
    val start = model.analysisStart ?: return model.session.secondaryText
    val end = model.analysisEnd ?: return model.session.secondaryText
    val duration = model.analysis.duration ?: return model.session.secondaryText
    if (!end.isAfter(start)) return model.session.secondaryText

    return listOfNotNull(
        "${MetricDisplayFormatter.formatShortInstant(start, zoneId)} to " +
            "${MetricDisplayFormatter.formatShortInstant(end, zoneId)} " +
            "(${MetricDisplayFormatter.formatDurationCompact(duration)})",
        sleepSessionDate(start, end, zoneId)?.let { "Local date $it" },
        model.session.sourceText
    ).joinToString(" | ")
}

@Composable
private fun SleepStageCard(stage: ReadableHealthRecord) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(uiText(stage.primaryText), style = MaterialTheme.typography.bodyMedium)
        if (stage.secondaryText.isNotBlank()) {
            Text(
                uiText(stage.secondaryText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SleepTagRow(analysis: SleepSessionAnalysis, stageCount: Int) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        analysis.tags.forEach { tag ->
            SleepTagBadge(uiText(tag.label), tag.tone)
        }
        if (stageCount > 0) {
            SleepTagBadge(
                text = uiText(if (stageCount == 1) "1 stage" else "${MetricDisplayFormatter.formatCount(stageCount)} stages"),
                tone = SleepTagTone.NEUTRAL
            )
        }
    }
}

@Composable
fun sleepQualityContainerColor(band: SleepQualityBand): Color =
    sleepQualityColors(band).cardContainer

@Composable
private fun SleepTagBadge(
    text: String,
    tone: SleepTagTone,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val colors = when (tone) {
        SleepTagTone.DATE -> Pair(
            Color(0xFF1E3A5F).copy(alpha = 0.82f),
            Color(0xFFD7E7FF)
        )
        SleepTagTone.NAP -> Pair(
            Color(0xFF30375F).copy(alpha = 0.82f),
            Color(0xFFE2E5FA)
        )
        SleepTagTone.SUCCESS -> Pair(
            Color(0xFF183327),
            Color(0xFFC5E7D2)
        )
        SleepTagTone.WARNING -> Pair(
            Color(0xFF3A2D14),
            Color(0xFFF3D79A)
        )
        SleepTagTone.ERROR -> Pair(
            Color(0xFF3E1F1C),
            Color(0xFFF2C0B9)
        )
        SleepTagTone.INFO -> Pair(
            Color(0xFF173040),
            Color(0xFFBFE0EE)
        )
        SleepTagTone.NEUTRAL -> Pair(
            scheme.surfaceVariant.copy(alpha = 0.42f),
            scheme.onSurfaceVariant
        )
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.first
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.second
        )
    }
}
