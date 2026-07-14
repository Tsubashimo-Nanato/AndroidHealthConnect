package com.example.healthconnectandroid.ui.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthconnectandroid.data.HealthDailyAggregateRow
import com.example.healthconnectandroid.ui.EmptyStateText
import com.example.healthconnectandroid.ui.i18n.uiText
import kotlin.math.roundToInt

@Composable
fun DailyBarChart(
    title: String,
    xAxisLabel: String,
    yAxisLabel: String,
    rows: List<HealthDailyAggregateRow>,
    sourceText: String?,
    barColorsByDate: Map<String, Color> = emptyMap()
) {
    if (rows.isEmpty()) {
        EmptyStateText("No data points available for this chart.")
        return
    }

    val barColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outline
    val gridColor = axisColor.copy(alpha = 0.28f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val localizedTitle = uiText(title)
    val localizedXAxisLabel = uiText(xAxisLabel)
    val localizedYAxisLabel = uiText(yAxisLabel)
    val sorted = remember(rows) { rows.sortedBy { it.localDate } }
    val defaultViewportFraction =
        (dailyVisibleBucketCount(sorted.size).toFloat() / sorted.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    val minViewportFraction = (2f / sorted.size.coerceAtLeast(1)).coerceIn(0.12f, 1f)
    var viewportFraction by remember(rows) { mutableFloatStateOf(defaultViewportFraction) }
    var viewportStartFraction by remember(rows) {
        mutableFloatStateOf(if (defaultViewportFraction < 1f) 1f else 0f)
    }
    var selectedRow by remember(rows, viewportStartFraction) { mutableStateOf<HealthDailyAggregateRow?>(null) }
    var chartWidthPx by remember(rows) { mutableFloatStateOf(0f) }
    var chartHeightPx by remember(rows) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val visibleCount = (sorted.size * viewportFraction).roundToInt().coerceIn(1, sorted.size)
    val maxStartIndex = (sorted.size - visibleCount).coerceAtLeast(0)
    val visibleStartIndex = if (maxStartIndex == 0) {
        0
    } else {
        (maxStartIndex * viewportStartFraction.coerceIn(0f, 1f)).roundToInt().coerceIn(0, maxStartIndex)
    }
    val visibleEndIndex = (visibleStartIndex + visibleCount).coerceAtMost(sorted.size)
    val visibleRows = remember(sorted, visibleStartIndex, visibleEndIndex) {
        sorted.subList(visibleStartIndex, visibleEndIndex)
    }
    val yBounds = expandedYBounds(visibleRows.map { it.total }, forceZeroMinimum = true)
    val xTicks = dailyTicks(visibleRows)
    val yTicks = numericTicks(yBounds)
    val plotLeftPx = with(density) { 56.dp.toPx() }
    val plotTopPx = with(density) { 18.dp.toPx() }
    val plotRightInsetPx = with(density) { 16.dp.toPx() }
    val plotBottomInsetPx = with(density) { 52.dp.toPx() }
    val axisPanTopInsetPx = with(density) { 10.dp.toPx() }

    val tickTextPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    tickTextPaint.color = onSurfaceColor.toArgb()
    tickTextPaint.textSize = with(density) { 10.sp.toPx() }
    tickTextPaint.textAlign = Paint.Align.CENTER

    val yTickPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    yTickPaint.color = onSurfaceColor.toArgb()
    yTickPaint.textSize = with(density) { 10.sp.toPx() }
    yTickPaint.textAlign = Paint.Align.RIGHT

    val axisLabelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    axisLabelPaint.color = onSurfaceColor.toArgb()
    axisLabelPaint.textSize = with(density) { 11.sp.toPx() }
    axisLabelPaint.isFakeBoldText = true

    Text(
        uiText("Range: ${compactLocalDateRange(visibleRows.firstOrNull()?.localDate, visibleRows.lastOrNull()?.localDate)}"),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        uiText("Y: ${formatNumber(yBounds.min)}-${formatNumber(yBounds.max)} ${visibleRows.firstOrNull()?.unit.orEmpty()}".trim()),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .onSizeChanged {
                chartWidthPx = it.width.toFloat()
                chartHeightPx = it.height.toFloat()
            }
            .chartAxisPanOrLongPressSelection(
                gestureKey = sorted.size,
                chartWidthPx = chartWidthPx,
                chartHeightPx = chartHeightPx,
                axisPanTopPx = (chartHeightPx - plotBottomInsetPx - axisPanTopInsetPx).coerceAtLeast(0f),
                plotLeftPx = plotLeftPx,
                plotRightPx = (chartWidthPx - plotRightInsetPx).coerceAtLeast(plotLeftPx + 1f),
                minViewportFraction = minViewportFraction,
                viewportFraction = viewportFraction,
                viewportStartFraction = viewportStartFraction,
                onViewportChange = { startFraction, newViewportFraction ->
                    selectedRow = null
                    viewportFraction = newViewportFraction
                    viewportStartFraction = startFraction
                }
            ) { touchX ->
                selectedRow = selectNearestDailyRow(
                    touchX = touchX,
                    plotLeftPx = plotLeftPx,
                    plotRightPx = (chartWidthPx - plotRightInsetPx).coerceAtLeast(plotLeftPx + 1f),
                    rows = visibleRows
                )
            }
    ) {
        val plot = PlotArea(
            left = plotLeftPx,
            top = plotTopPx,
            right = size.width - plotRightInsetPx,
            bottom = size.height - plotBottomInsetPx
        )
        drawChartFrame(
            plot = plot,
            xTicks = xTicks,
            yTicks = yTicks,
            xAxisLabel = localizedXAxisLabel,
            yAxisLabel = localizedYAxisLabel,
            axisColor = axisColor,
            gridColor = gridColor,
            tickTextPaint = tickTextPaint,
            yTickPaint = yTickPaint,
            axisLabelPaint = axisLabelPaint
        )

        val slot = plot.width / visibleRows.size.coerceAtLeast(1)
        visibleRows.forEachIndexed { index, row ->
            val barHeight = (((row.total - yBounds.min) / yBounds.range) * plot.height).toFloat()
            val x0 = plot.left + index * slot + slot * 0.18f
            val x1 = plot.left + (index + 1) * slot - slot * 0.18f
            drawRect(
                color = if (row == selectedRow) {
                    selectedColor
                } else {
                    barColorsByDate[row.localDate] ?: barColor
                },
                topLeft = Offset(x0, plot.bottom - barHeight),
                size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(2f), barHeight.coerceAtLeast(1f))
            )
            if (row == selectedRow) {
                val centerX = (x0 + x1) / 2f
                drawLine(
                    color = selectedColor,
                    start = Offset(centerX, plot.top),
                    end = Offset(centerX, plot.bottom),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }

    selectedRow?.let { row ->
        SelectedPointCard(
            title = localizedTitle,
            recordTypeLabel = localizedTitle,
            primaryText = "${formatNumber(row.total)} ${row.unit.orEmpty()}".trim(),
            timeText = row.localDate,
            sourceText = sourceText,
            secondaryText = "Bucket value for ${row.localDate}",
            rawDetailsText = null
        )
    }
}



private fun dailyVisibleBucketCount(totalBuckets: Int): Int = when {
    totalBuckets <= 7 -> totalBuckets
    totalBuckets <= 14 -> 7
    totalBuckets <= 31 -> 10
    else -> 14
}

private fun dailyTicks(rows: List<HealthDailyAggregateRow>): List<AxisTick> {
    if (rows.isEmpty()) return emptyList()
    if (rows.size == 1) {
        return listOf(AxisTick(0.5f, formatDateTick(rows.first().localDate)))
    }
    return spreadIndices(rows.size, 4).map { index ->
        AxisTick(
            fraction = (index + 0.5f) / rows.size.toFloat(),
            label = formatDateTick(rows[index].localDate)
        )
    }
}

private fun selectNearestDailyRow(
    touchX: Float,
    plotLeftPx: Float,
    plotRightPx: Float,
    rows: List<HealthDailyAggregateRow>
): HealthDailyAggregateRow? {
    if (rows.isEmpty()) return null
    if (rows.size == 1) return rows.first()
    val slot = (plotRightPx - plotLeftPx) / rows.size.coerceAtLeast(1)
    val index = ((touchX - plotLeftPx) / slot).toInt().coerceIn(0, rows.lastIndex)
    return rows.getOrNull(index)
}
