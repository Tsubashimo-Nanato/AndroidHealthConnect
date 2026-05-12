package com.example.healthconnectandroid.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun <T> SegmentedSwitch(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val selectedIndex = options.indexOf(selected).takeIf { it >= 0 } ?: 0

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(4.dp)
        ) {
            val segmentWidth = maxWidth / options.size
            val selectedOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex,
                animationSpec = tween(durationMillis = 180),
                label = "segmented-switch-offset"
            )
            Surface(
                modifier = Modifier
                    .offset(x = selectedOffset)
                    .width(segmentWidth)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {}
            Row(Modifier.fillMaxWidth()) {
                options.forEach { option ->
                    val selectedSegment = option == selected
                    val textColor by animateColorAsState(
                        targetValue = if (selectedSegment) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 140),
                        label = "segmented-switch-text"
                    )
                    Box(
                        modifier = Modifier
                            .width(segmentWidth)
                            .fillMaxHeight()
                            .clickable { onSelected(option) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
