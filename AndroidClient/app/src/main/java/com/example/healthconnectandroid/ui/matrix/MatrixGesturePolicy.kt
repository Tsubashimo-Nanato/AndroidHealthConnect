package com.example.healthconnectandroid.ui.matrix

import kotlin.math.abs
import kotlin.math.roundToInt

enum class MatrixDragIntent {
    HorizontalPan,
    VerticalScroll
}

object MatrixGesturePolicy {
    private const val SNAP_THRESHOLD_FRACTION = 0.18f

    fun resolveDragIntent(
        deltaX: Float,
        deltaY: Float,
        touchSlop: Float
    ): MatrixDragIntent? {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        if (absX < touchSlop && absY < touchSlop) return null
        return if (absX >= absY) {
            MatrixDragIntent.HorizontalPan
        } else {
            MatrixDragIntent.VerticalScroll
        }
    }

    fun panCells(totalDragX: Float, cellWidthPx: Float): Int {
        if (cellWidthPx <= 0f) return 0
        return (-totalDragX / cellWidthPx).roundToInt()
    }

    fun snappedWindowDelta(totalDragX: Float, viewportWidthPx: Float): Int {
        if (viewportWidthPx <= 0f) return 0
        val fraction = totalDragX / viewportWidthPx
        if (abs(fraction) < SNAP_THRESHOLD_FRACTION) return 0
        return if (totalDragX < 0f) 1 else -1
    }

    fun nearestWindowDelta(dragOffsetPx: Float, viewportWidthPx: Float): Int {
        if (viewportWidthPx <= 0f) return 0
        val fraction = dragOffsetPx / viewportWidthPx
        if (abs(fraction) < 0.5f) return 0
        return if (dragOffsetPx < 0f) 1 else -1
    }
}
