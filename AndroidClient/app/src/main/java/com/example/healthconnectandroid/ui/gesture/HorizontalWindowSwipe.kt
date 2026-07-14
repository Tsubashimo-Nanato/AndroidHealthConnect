package com.example.healthconnectandroid.ui.gesture

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import com.example.healthconnectandroid.ui.matrix.MatrixGesturePolicy
import kotlin.math.roundToInt

fun Modifier.horizontalWindowSwipe(
    enabled: Boolean = true,
    onSettled: (Int) -> Unit = {},
    onSwipe: (Int) -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val visualOffsetPx by animateFloatAsState(
        targetValue = dragTotalPx,
        animationSpec = if (dragging) {
            snap()
        } else {
            spring(stiffness = Spring.StiffnessMediumLow)
        },
        label = "window-drag-offset"
    )

    onSizeChanged { viewportWidthPx = it.width.toFloat() }
        .clipToBounds()
        .offset { IntOffset(visualOffsetPx.roundToInt(), 0) }
        .pointerInput(viewportWidthPx, onSettled, onSwipe) {
        detectHorizontalDragGestures(
            onDragStart = {
                dragTotalPx = 0f
                dragging = true
            },
            onHorizontalDrag = { change, dragAmount ->
                val nextOffset = dragTotalPx + dragAmount
                val maxOffset = viewportWidthPx * 0.45f
                dragTotalPx = if (maxOffset > 0f) {
                    nextOffset.coerceIn(-maxOffset, maxOffset)
                } else {
                    nextOffset
                }
                change.consume()
            },
            onDragEnd = {
                val delta = MatrixGesturePolicy.snappedWindowDelta(dragTotalPx, viewportWidthPx)
                dragging = false
                dragTotalPx = 0f
                onSettled(delta)
                if (delta != 0) onSwipe(delta)
            },
            onDragCancel = {
                dragging = false
                dragTotalPx = 0f
                onSettled(0)
            }
        )
    }
}
