package com.example.healthconnectandroid.ui.matrix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MatrixGesturePolicyTest {
    @Test
    fun resolvesHorizontalIntentInBothDirections() {
        assertEquals(
            MatrixDragIntent.HorizontalPan,
            MatrixGesturePolicy.resolveDragIntent(deltaX = 16f, deltaY = 4f, touchSlop = 8f)
        )
        assertEquals(
            MatrixDragIntent.HorizontalPan,
            MatrixGesturePolicy.resolveDragIntent(deltaX = -16f, deltaY = 4f, touchSlop = 8f)
        )
    }

    @Test
    fun resolvesVerticalIntentWithoutConsumingAsHorizontal() {
        assertEquals(
            MatrixDragIntent.VerticalScroll,
            MatrixGesturePolicy.resolveDragIntent(deltaX = 5f, deltaY = 18f, touchSlop = 8f)
        )
    }

    @Test
    fun waitsUntilTouchSlopIsExceeded() {
        assertNull(MatrixGesturePolicy.resolveDragIntent(deltaX = 3f, deltaY = 4f, touchSlop = 8f))
    }

    @Test
    fun panCellsUsesSymmetricSign() {
        assertEquals(1, MatrixGesturePolicy.panCells(totalDragX = -51f, cellWidthPx = 50f))
        assertEquals(-1, MatrixGesturePolicy.panCells(totalDragX = 51f, cellWidthPx = 50f))
    }

    @Test
    fun snappedWindowDeltaClampsToOneWindowPerGesture() {
        assertEquals(1, MatrixGesturePolicy.snappedWindowDelta(totalDragX = -900f, viewportWidthPx = 300f))
        assertEquals(-1, MatrixGesturePolicy.snappedWindowDelta(totalDragX = 900f, viewportWidthPx = 300f))
        assertEquals(0, MatrixGesturePolicy.snappedWindowDelta(totalDragX = 20f, viewportWidthPx = 300f))
    }

    @Test
    fun nearestWindowDeltaSettlesAtHalfViewport() {
        assertEquals(0, MatrixGesturePolicy.nearestWindowDelta(dragOffsetPx = -149f, viewportWidthPx = 300f))
        assertEquals(1, MatrixGesturePolicy.nearestWindowDelta(dragOffsetPx = -151f, viewportWidthPx = 300f))
        assertEquals(0, MatrixGesturePolicy.nearestWindowDelta(dragOffsetPx = 149f, viewportWidthPx = 300f))
        assertEquals(-1, MatrixGesturePolicy.nearestWindowDelta(dragOffsetPx = 151f, viewportWidthPx = 300f))
    }
}
