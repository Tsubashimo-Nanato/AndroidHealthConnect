package com.example.healthconnectandroid.ui.hr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.HrDateQuality
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter

@Composable
internal fun HeartRateDateCell(
    cell: HrDateCell,
    selected: Boolean,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = heartRateDateCellColors(cell.quality, cell.zoneScore)
    Surface(
        modifier = modifier.graphicsLayer { alpha = if (active) 1f else 0.38f },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.background.copy(alpha = 0.96f) else colors.background,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else colors.border
        )
    ) {
        Column(
            Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = cell.label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.content,
                maxLines = 1
            )
            Text(
                text = if (cell.sampleCount == 0) "-" else MetricDisplayFormatter.formatCount(cell.sampleCount),
                style = MaterialTheme.typography.labelSmall,
                color = colors.content.copy(alpha = 0.72f),
                maxLines = 1
            )
        }
    }
}

private data class HrCellColors(
    val background: Color,
    val border: Color,
    val content: Color
)

@Composable
private fun heartRateDateCellColors(quality: HrDateQuality, zoneScore: Float): HrCellColors {
    val scheme = MaterialTheme.colorScheme
    return if (quality == HrDateQuality.NO_DATA) {
        HrCellColors(
            scheme.surfaceVariant.copy(alpha = 0.28f),
            scheme.outline.copy(alpha = 0.16f),
            scheme.onSurfaceVariant.copy(alpha = 0.62f)
        )
    } else {
        val score = zoneScore.coerceIn(0f, 1f)
        val colorT = zoneColorProgress(score)
        val reference = Color(0xFF1F5A3A)
        val elevated = Color(0xFF806315)
        val high = Color(0xFF61251F)
        val background = if (colorT <= 0.5f) {
            lerp(reference, elevated, colorT / 0.5f)
        } else {
            lerp(elevated, high, (colorT - 0.5f) / 0.5f)
        }
        HrCellColors(
            background = background,
            border = lerp(background, Color.White, 0.36f),
            content = lerp(Color(0xFFE5F4E8), Color(0xFFFFD4C9), colorT)
        )
    }
}

private fun zoneColorProgress(score: Float): Float =
    when {
        score <= 0.28f -> (score / 0.28f) * 0.22f
        score <= 0.68f -> 0.22f + ((score - 0.28f) / 0.40f) * 0.43f
        else -> 0.65f + ((score - 0.68f) / 0.32f) * 0.35f
    }.coerceIn(0f, 1f)
