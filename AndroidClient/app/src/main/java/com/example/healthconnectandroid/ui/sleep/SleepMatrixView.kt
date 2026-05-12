package com.example.healthconnectandroid.ui.sleep

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.SleepQualityBand
import com.example.healthconnectandroid.hc.SleepQualityMatrixModel
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter

fun selectedSleepLabel(model: SleepQualityMatrixModel, selectedBoxIds: Set<String>): String? {
    val selected = model.boxes.filter { it.id in selectedBoxIds }
    if (selected.isEmpty()) return null
    val sessions = selected.sumOf { it.sessionCount }
    val label = when {
        selected.size == 1 -> selected.first().label
        selected.size <= 4 -> selected.joinToString(", ") { it.label }
        else -> "${selected.size} groups"
    }
    return "Selected: $label - ${MetricDisplayFormatter.formatCount(sessions)} sessions"
}

@Composable
fun SleepMatrixPicker(
    model: SleepQualityMatrixModel,
    selectedBoxIds: Set<String>,
    onSelectedBoxIdsChange: (Set<String>) -> Unit,
    onGestureDiagnostic: (action: String, deltaSnap: Int?, selectedCount: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        model.boxes.chunked(model.columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { box ->
                    SleepMatrixBox(
                        label = box.label,
                        qualityBand = box.qualityBand,
                        sessionCount = box.sessionCount,
                        selected = box.id in selectedBoxIds,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSelectedBoxIdsChange(setOf(box.id))
                                onGestureDiagnostic("tap", null, 1)
                            }
                    )
                }
                repeat(model.columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SleepMatrixBox(
    label: String,
    qualityBand: SleepQualityBand?,
    sessionCount: Int,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = sleepMatrixColor(qualityBand)
    Surface(
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.first.copy(alpha = 0.96f) else color.first,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else color.second
        )
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color.third)
            Text(
                if (sessionCount == 0) "-" else MetricDisplayFormatter.formatCount(sessionCount),
                style = MaterialTheme.typography.labelSmall,
                color = color.third.copy(alpha = 0.78f)
            )
        }
    }
}

@Composable
private fun sleepMatrixColor(band: SleepQualityBand?): Triple<Color, Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (band) {
        SleepQualityBand.GOOD -> Triple(Color(0xFF204E35), Color(0xFF6EAA81), Color(0xFFD7F0DE))
        SleepQualityBand.FAIR -> Triple(Color(0xFF355D2B), Color(0xFF88AD65), Color(0xFFE4F0D4))
        SleepQualityBand.NAP -> Triple(Color(0xFF173B38), Color(0xFF69A8A0), Color(0xFFCBECE7))
        SleepQualityBand.FRAGMENTED -> Triple(Color(0xFF4B3B16), Color(0xFFB18B3E), Color(0xFFFFE5A8))
        SleepQualityBand.SHORT -> Triple(Color(0xFF53241F), Color(0xFFA45C52), Color(0xFFFFD1C9))
        SleepQualityBand.UNKNOWN -> Triple(
            scheme.surfaceVariant.copy(alpha = 0.52f),
            scheme.outline.copy(alpha = 0.24f),
            scheme.onSurfaceVariant
        )
        null -> Triple(
            scheme.surfaceVariant.copy(alpha = 0.28f),
            scheme.outline.copy(alpha = 0.16f),
            scheme.onSurfaceVariant.copy(alpha = 0.58f)
        )
    }
}
