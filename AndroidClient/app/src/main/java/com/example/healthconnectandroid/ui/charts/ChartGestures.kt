package com.example.healthconnectandroid.ui.charts

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.sqrt

fun axisStartFractionFromDrag(
    dragDeltaX: Float,
    plotLeftPx: Float,
    plotRightPx: Float,
    viewportFraction: Float,
    startFraction: Float
): Float {
    val widthPx = (plotRightPx - plotLeftPx).coerceAtLeast(1f)
    if (viewportFraction >= 1f) return 0f
    val scrollableFraction = (1f - viewportFraction).coerceAtLeast(0.001f)
    val normalizedDelta = (dragDeltaX / widthPx) * (viewportFraction / scrollableFraction)
    return (startFraction - normalizedDelta).coerceIn(0f, 1f)
}

fun viewportStartFractionForZoom(
    startFraction: Float,
    startViewportFraction: Float,
    newViewportFraction: Float,
    anchorFraction: Float
): Float {
    if (newViewportFraction >= 1f) return 0f
    val startInFullRange = startFraction.coerceIn(0f, 1f) * (1f - startViewportFraction)
    val anchorInFullRange = startInFullRange + anchorFraction.coerceIn(0f, 1f) * startViewportFraction
    val newStartInFullRange = anchorInFullRange - anchorFraction.coerceIn(0f, 1f) * newViewportFraction
    return (newStartInFullRange / (1f - newViewportFraction)).coerceIn(0f, 1f)
}

private fun pointerDistance(a: Offset, b: Offset): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

private fun pointerCenter(a: Offset, b: Offset): Offset =
    Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)

@Composable
fun Modifier.chartAxisPanOrLongPressSelection(
    gestureKey: Any,
    chartWidthPx: Float,
    chartHeightPx: Float,
    axisPanTopPx: Float,
    plotLeftPx: Float,
    plotRightPx: Float,
    minViewportFraction: Float,
    viewportFraction: Float,
    viewportStartFraction: Float,
    onViewportChange: (startFraction: Float, viewportFraction: Float) -> Unit,
    onSelectX: (Float) -> Unit
): Modifier {
    val currentViewportStartFraction by rememberUpdatedState(viewportStartFraction)
    val currentViewportFraction by rememberUpdatedState(viewportFraction)
    val currentOnViewportChange by rememberUpdatedState(onViewportChange)
    val currentOnSelectX by rememberUpdatedState(onSelectX)
    return pointerInput(
        gestureKey,
        chartWidthPx,
        chartHeightPx,
        axisPanTopPx,
        plotLeftPx,
        plotRightPx,
        minViewportFraction
    ) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var longPressTriggered = false
        val axisPanCandidate = down.position.y >= axisPanTopPx
        val axisStartX = down.position.x
        val axisStartFraction = currentViewportStartFraction
        val axisStartViewportFraction = currentViewportFraction
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val first = pressed[0]
                val second = pressed[1]
                val initialDistance = pointerDistance(first.position, second.position).coerceAtLeast(1f)
                val initialCenter = pointerCenter(first.position, second.position)
                val anchorFraction =
                    ((initialCenter.x - plotLeftPx) / (plotRightPx - plotLeftPx).coerceAtLeast(1f))
                        .coerceIn(0f, 1f)
                val zoomStartFraction = currentViewportStartFraction
                val zoomStartViewportFraction = currentViewportFraction
                while (true) {
                    val zoomEvent = awaitPointerEvent()
                    val zoomPressed = zoomEvent.changes.filter { it.pressed }
                    if (zoomPressed.size < 2) break
                    val zoomDistance = pointerDistance(zoomPressed[0].position, zoomPressed[1].position)
                        .coerceAtLeast(1f)
                    val scale = (zoomDistance / initialDistance).coerceIn(0.25f, 4f)
                    val newViewportFraction =
                        (zoomStartViewportFraction / scale).coerceIn(minViewportFraction, 1f)
                    currentOnViewportChange(
                        viewportStartFractionForZoom(
                            startFraction = zoomStartFraction,
                            startViewportFraction = zoomStartViewportFraction,
                            newViewportFraction = newViewportFraction,
                            anchorFraction = anchorFraction
                        ),
                        newViewportFraction
                    )
                    zoomPressed.forEach { it.consume() }
                }
                return@awaitEachGesture
            }

            val change = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
            if (axisPanCandidate) {
                currentOnViewportChange(
                    axisStartFractionFromDrag(
                        dragDeltaX = change.position.x - axisStartX,
                        plotLeftPx = plotLeftPx,
                        plotRightPx = plotRightPx,
                        viewportFraction = axisStartViewportFraction,
                        startFraction = axisStartFraction
                    ),
                    axisStartViewportFraction
                )
                change.consume()
                continue
            }
            if (!longPressTriggered &&
                change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis
            ) {
                longPressTriggered = true
            }
            if (longPressTriggered) {
                currentOnSelectX(change.position.x)
                change.consume()
            }
        }
    }
    }
}
