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
    val colors = heartRateDateCellColors(cell.quality)
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
private fun heartRateDateCellColors(quality: HrDateQuality): HrCellColors {
    val scheme = MaterialTheme.colorScheme
    return when (quality) {
        HrDateQuality.NORMAL -> HrCellColors(Color(0xFF204E35), Color(0xFF6EAA81), Color(0xFFD7F0DE))
        HrDateQuality.ELEVATED -> HrCellColors(Color(0xFF4B3B16), Color(0xFFB18B3E), Color(0xFFFFE5A8))
        HrDateQuality.HIGH -> HrCellColors(Color(0xFF53241F), Color(0xFFA45C52), Color(0xFFFFD1C9))
        HrDateQuality.NO_DATA -> HrCellColors(
            scheme.surfaceVariant.copy(alpha = 0.28f),
            scheme.outline.copy(alpha = 0.16f),
            scheme.onSurfaceVariant.copy(alpha = 0.62f)
        )
    }
}
