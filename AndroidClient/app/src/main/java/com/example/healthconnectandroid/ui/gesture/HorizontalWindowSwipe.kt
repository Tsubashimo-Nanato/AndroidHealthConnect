package com.example.healthconnectandroid.ui.gesture

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

fun Modifier.horizontalWindowSwipe(
    enabled: Boolean = true,
    threshold: Dp = 48.dp,
    onSwipe: (Int) -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }

    pointerInput(thresholdPx, onSwipe) {
        detectHorizontalDragGestures(
            onDragStart = { dragTotalPx = 0f },
            onHorizontalDrag = { change, dragAmount ->
                dragTotalPx += dragAmount
                change.consume()
            },
            onDragEnd = {
                val delta = when {
                    abs(dragTotalPx) < thresholdPx -> 0
                    dragTotalPx < 0f -> 1
                    else -> -1
                }
                if (delta != 0) onSwipe(delta)
                dragTotalPx = 0f
            },
            onDragCancel = { dragTotalPx = 0f }
        )
    }
}
