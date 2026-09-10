package com.example.healthconnectandroid.ui.gesture

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.example.healthconnectandroid.ui.matrix.MatrixGesturePolicy

fun Modifier.horizontalWindowSwipe(
    enabled: Boolean = true,
    onSettled: (Int) -> Unit = {},
    onSwipe: (Int) -> Unit
): Modifier = composed {
    if (!enabled) return@composed this

    var viewportWidthPx by remember { mutableFloatStateOf(0f) }
    var dragTotalPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val currentOnSettled by rememberUpdatedState(onSettled)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    val visualOffsetPx by animateFloatAsState(
        targetValue = dragTotalPx,
        animationSpec = if (dragging) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        },
        label = "window-drag-offset"
    )

    onSizeChanged { viewportWidthPx = it.width.toFloat() }
        .clipToBounds()
        .graphicsLayer { translationX = visualOffsetPx }
        .pointerInput(viewportWidthPx) {
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
                currentOnSettled(delta)
                if (delta != 0) currentOnSwipe(delta)
            },
            onDragCancel = {
                dragging = false
                dragTotalPx = 0f
                currentOnSettled(0)
            }
        )
    }
}
