package com.example.healthconnectandroid.ui.sleep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
            if (session.secondaryText.isNotBlank()) {
                Text(
                    uiText(session.secondaryText),
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

@Composable
private fun SleepStageCard(stage: ReadableHealthRecord) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(10.dp),
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
        SleepTagTone.DATE -> Triple(
            Color(0xFF1E3A5F).copy(alpha = 0.82f),
            Color(0xFFD7E7FF),
            Color(0xFF6F99C8)
        )
        SleepTagTone.NAP -> Triple(
            Color(0xFF30375F).copy(alpha = 0.82f),
            Color(0xFFE2E5FA),
            Color(0xFF7884BD)
        )
        SleepTagTone.SUCCESS -> Triple(
            Color(0xFF183327),
            Color(0xFFC5E7D2),
            Color(0xFF3F7759)
        )
        SleepTagTone.WARNING -> Triple(
            Color(0xFF3A2D14),
            Color(0xFFF3D79A),
            Color(0xFF8A6A2E)
        )
        SleepTagTone.ERROR -> Triple(
            Color(0xFF3E1F1C),
            Color(0xFFF2C0B9),
            Color(0xFF91554D)
        )
        SleepTagTone.INFO -> Triple(
            Color(0xFF173040),
            Color(0xFFBFE0EE),
            Color(0xFF49778C)
        )
        SleepTagTone.NEUTRAL -> Triple(
            scheme.surfaceVariant.copy(alpha = 0.42f),
            scheme.onSurfaceVariant,
            scheme.outline.copy(alpha = 0.24f)
        )
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.first,
        border = BorderStroke(1.dp, colors.third)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.second
        )
    }
}
