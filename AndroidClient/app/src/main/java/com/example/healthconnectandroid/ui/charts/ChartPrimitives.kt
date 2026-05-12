package com.example.healthconnectandroid.ui.charts

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

data class PlotArea(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

data class AxisTick(
    val fraction: Float,
    val label: String
)

data class NumericBounds(
    val min: Double,
    val max: Double
) {
    val range: Double get() = (max - min).coerceAtLeast(1e-9)
}

fun expandedYBounds(
    values: List<Double>,
    forceZeroMinimum: Boolean = false
): NumericBounds {
    if (values.isEmpty()) return NumericBounds(0.0, 1.0)
    val rawMin = values.minOrNull() ?: 0.0
    val rawMax = values.maxOrNull() ?: 1.0
    val rawRange = rawMax - rawMin
    val padding = if (rawRange <= 1e-9) {
        max(abs(rawMax) * 0.05, 1.0)
    } else {
        rawRange * 0.1
    }
    val minValue = if (forceZeroMinimum) 0.0 else rawMin - padding
    val maxValue = rawMax + padding
    return NumericBounds(minValue, maxValue)
}

fun numericTicks(bounds: NumericBounds): List<AxisTick> =
    listOf(0f, 0.33f, 0.66f, 1f).map { fraction ->
        AxisTick(
            fraction = fraction,
            label = formatNumber(bounds.min + (bounds.range * fraction))
        )
    }

fun spreadIndices(totalSize: Int, desiredCount: Int): List<Int> {
    if (totalSize <= 1) return listOf(0)
    val count = desiredCount.coerceAtLeast(2)
    return (0 until count)
        .map { step ->
            ((step.toDouble() / (count - 1)) * (totalSize - 1)).roundToInt()
        }
        .distinct()
}

fun DrawScope.drawChartFrame(
    plot: PlotArea,
    xTicks: List<AxisTick>,
    yTicks: List<AxisTick>,
    xAxisLabel: String,
    yAxisLabel: String,
    axisColor: androidx.compose.ui.graphics.Color,
    gridColor: androidx.compose.ui.graphics.Color,
    tickTextPaint: Paint,
    yTickPaint: Paint,
    axisLabelPaint: Paint
) {
    drawLine(axisColor, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), strokeWidth = 1.dp.toPx())
    drawLine(axisColor, Offset(plot.left, plot.top), Offset(plot.left, plot.bottom), strokeWidth = 1.dp.toPx())

    yTicks.forEach { tick ->
        val y = plot.bottom - (tick.fraction * plot.height)
        drawLine(gridColor, Offset(plot.left, y), Offset(plot.right, y), strokeWidth = 1.dp.toPx())
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                tick.label,
                plot.left - 8.dp.toPx(),
                y + (yTickPaint.textSize / 3f),
                yTickPaint
            )
        }
    }

    xTicks.forEach { tick ->
        val x = plot.left + (tick.fraction * plot.width)
        drawLine(axisColor, Offset(x, plot.bottom), Offset(x, plot.bottom + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                tick.label,
                x,
                plot.bottom + 16.dp.toPx(),
                tickTextPaint
            )
        }
    }

    axisLabelPaint.textAlign = Paint.Align.LEFT
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(
            yAxisLabel,
            plot.left,
            plot.top - 4.dp.toPx(),
            axisLabelPaint
        )
    }
    axisLabelPaint.textAlign = Paint.Align.CENTER
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(
            xAxisLabel,
            plot.left + (plot.width / 2f),
            plot.bottom + 34.dp.toPx(),
            axisLabelPaint
        )
    }
}
