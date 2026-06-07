package com.example.healthconnectandroid.ui.charts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthconnectandroid.hc.HeartRateAnalysis
import com.example.healthconnectandroid.hc.HeartRateReferenceZones
import com.example.healthconnectandroid.hc.HeartRateZoneTone
import com.example.healthconnectandroid.hc.InspectorChartPoint
import com.example.healthconnectandroid.ui.EmptyStateText
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

@Composable
fun LineChart(
    title: String,
    xAxisLabel: String,
    yAxisLabel: String,
    points: List<InspectorChartPoint>,
    zoneId: ZoneId,
    domainStartEpochMillis: Long? = null,
    domainEndEpochMillis: Long? = null,
    viewportResetKey: Any? = points,
    initialFullRange: Boolean = false,
    preferredVisibleRange: ChartVisibleRange? = null,
    heartRateZones: HeartRateReferenceZones? = null,
    onVisibleRangeChanged: ((ChartVisibleRange) -> Unit)? = null
) {
    if (points.isEmpty()) {
        EmptyStateText("No data points available for this chart.")
        return
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.tertiary
    val axisColor = MaterialTheme.colorScheme.outline
    val gridColor = axisColor.copy(alpha = 0.28f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val sorted = remember(points) { points.sortedBy { it.epochMillis } }
    val fullXBounds = lineFullXBounds(
        points = sorted,
        domainStartEpochMillis = domainStartEpochMillis,
        domainEndEpochMillis = domainEndEpochMillis,
        xAxisLabel = xAxisLabel,
        padDomain = !initialFullRange
    )
    val defaultViewportFraction = if (initialFullRange) {
        1f
    } else {
        (lineVisiblePointCount(sorted.size).toFloat() / sorted.size.coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    val initialViewport = preferredVisibleRange?.let { preferredViewportFractions(fullXBounds, it) }
    val minViewportFraction = lineMinViewportFraction(
        totalPoints = sorted.size,
        preferredViewportFraction = initialViewport?.second
    )
    var viewportFraction by remember(
        points,
        viewportResetKey,
        initialFullRange,
        domainStartEpochMillis,
        domainEndEpochMillis
    ) {
        mutableFloatStateOf(initialViewport?.second ?: defaultViewportFraction)
    }
    var viewportStartFraction by remember(
        points,
        viewportResetKey,
        initialFullRange,
        domainStartEpochMillis,
        domainEndEpochMillis
    ) {
        mutableFloatStateOf(initialViewport?.first ?: if (defaultViewportFraction < 1f) 1f else 0f)
    }
    var selectedPoint by remember(points, viewportStartFraction) { mutableStateOf<InspectorChartPoint?>(null) }
    var chartWidthPx by remember(points) { mutableFloatStateOf(0f) }
    var chartHeightPx by remember(points) { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val xBounds = visibleXBounds(fullXBounds, viewportStartFraction, viewportFraction)
    LaunchedEffect(xBounds.min, xBounds.max) {
        onVisibleRangeChanged?.invoke(
            ChartVisibleRange(
                startEpochMillis = xBounds.min.roundToLong(),
                endEpochMillis = xBounds.max.roundToLong()
            )
        )
    }
    val visiblePoints = remember(sorted, xBounds) {
        visibleChartPointWindow(sorted, xBounds)
    }
    val drawPoints = remember(visiblePoints) {
        ChartDrawDownsampler.downsampleMinMax(visiblePoints)
    }
    val visibleValues = remember(visiblePoints, sorted) {
        visiblePoints.flatMap { listOfNotNull(it.value, it.value2) }
            .ifEmpty { sorted.flatMap { listOfNotNull(it.value, it.value2) } }
    }
    val yBounds = if (heartRateZones != null) {
        val bounds = HeartRateAnalysis.stableChartBounds(visibleValues, heartRateZones)
        NumericBounds(bounds.first, bounds.second)
    } else {
        expandedYBounds(visibleValues)
    }
    val unit = sorted.firstOrNull()?.unit.orEmpty()
    val xTicks = remember(xBounds, zoneId) { lineTimeTicks(xBounds, zoneId) }
    val yTicks = remember(yBounds, heartRateZones) {
        if (heartRateZones != null) heartRateTicks(yBounds) else numericTicks(yBounds)
    }
    val seriesStyle = adaptiveLineStyle(visiblePoints.size)
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

    val zoneLabelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    zoneLabelPaint.color = onSurfaceColor.copy(alpha = 0.62f).toArgb()
    zoneLabelPaint.textSize = with(density) { 10.sp.toPx() }
    zoneLabelPaint.textAlign = Paint.Align.RIGHT

    Text(
        uiText("Range: ${compactEpochRange(xBounds.min.roundToLong(), xBounds.max.roundToLong(), zoneId)}"),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Text(
        uiText("Y: ${formatNumber(yBounds.min)}-${formatNumber(yBounds.max)} $unit".trim()),
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
                    selectedPoint = null
                    viewportFraction = newViewportFraction
                    viewportStartFraction = startFraction
                }
            ) { touchX ->
                selectedPoint = selectNearestLinePoint(
                    touchX = touchX,
                    plotLeftPx = plotLeftPx,
                    plotRightPx = (chartWidthPx - plotRightInsetPx).coerceAtLeast(plotLeftPx + 1f),
                    xBounds = xBounds,
                    visiblePoints = visiblePoints
                )
            }
    ) {
        val plot = PlotArea(
            left = plotLeftPx,
            top = plotTopPx,
            right = size.width - plotRightInsetPx,
            bottom = size.height - plotBottomInsetPx
        )
        heartRateZones?.let {
            drawHeartRateReferenceBands(
                plot = plot,
                yBounds = yBounds,
                zones = it,
                labelPaint = zoneLabelPaint
            )
        }
        drawChartFrame(
            plot = plot,
            xTicks = xTicks,
            yTicks = yTicks,
            xAxisLabel = xAxisLabel,
            yAxisLabel = yAxisLabel,
            axisColor = axisColor,
            gridColor = gridColor,
            tickTextPaint = tickTextPaint,
            yTickPaint = yTickPaint,
            axisLabelPaint = axisLabelPaint
        )

        fun pointX(epochMillis: Long): Float =
            plot.left + (((epochMillis.toDouble() - xBounds.min) / xBounds.range).toFloat() * plot.width)

        fun pointY(value: Double): Float =
            plot.bottom - (((value - yBounds.min) / yBounds.range).toFloat() * plot.height)

        fun drawSeries(
            valueOf: (InspectorChartPoint) -> Double?,
            color: androidx.compose.ui.graphics.Color
        ) {
            val series = drawPoints.mapNotNull { point ->
                valueOf(point)?.let { Offset(pointX(point.epochMillis), pointY(it)) }
            }
            if (series.isEmpty()) return
            val pointRadius = seriesStyle.pointRadiusDp.dp.toPx()
            if (pointRadius > 0f) {
                for (point in series) {
                    drawCircle(color, radius = pointRadius, center = point)
                }
            }
            if (series.size >= 2) {
                val path = Path().apply {
                    moveTo(series.first().x, series.first().y)
                    for (point in series.drop(1)) lineTo(point.x, point.y)
                }
                drawPath(path, color, style = Stroke(width = seriesStyle.strokeDp.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        drawSeries({ it.value }, primaryColor)
        drawSeries({ it.value2 }, secondaryColor)

        selectedPoint?.let { selected ->
            val selectedX = pointX(selected.epochMillis)
            drawLine(
                color = secondaryColor,
                start = Offset(selectedX, plot.top),
                end = Offset(selectedX, plot.bottom),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(
                color = secondaryColor,
                radius = 3.dp.toPx(),
                center = Offset(selectedX, pointY(selected.value))
            )
        }
    }

    selectedPoint?.let { point ->
        SelectedPointCard(
            title = title,
            recordTypeLabel = title,
            primaryText = point.primaryText ?: "${formatNumber(point.value)} ${point.unit.orEmpty()}".trim(),
            timeText = formatEpoch(point.epochMillis, zoneId),
            sourceText = point.sourceText,
            secondaryText = point.value2?.let { second ->
                "Secondary value: ${formatNumber(second)} ${point.unit.orEmpty()}".trim()
            } ?: point.secondaryText,
            rawDetailsText = point.rawDetailsText
        )
    }
}

private data class LineSeriesStyle(
    val strokeDp: Float,
    val pointRadiusDp: Float
)

private fun adaptiveLineStyle(visiblePointCount: Int): LineSeriesStyle =
    when {
        visiblePointCount > 200 -> LineSeriesStyle(strokeDp = 1.0f, pointRadiusDp = 0f)
        visiblePointCount > 50 -> LineSeriesStyle(strokeDp = 1.25f, pointRadiusDp = 0f)
        else -> LineSeriesStyle(strokeDp = 1.8f, pointRadiusDp = 1.4f)
    }

private fun lineVisiblePointCount(totalPoints: Int): Int = when {
    totalPoints <= 18 -> totalPoints
    totalPoints <= 60 -> 18
    totalPoints <= 180 -> 30
    else -> 48
}

internal fun lineMinViewportFraction(
    totalPoints: Int,
    preferredViewportFraction: Float?
): Float {
    val sampleBasedFloor = (2f / totalPoints.coerceAtLeast(1)).coerceIn(0.001f, 1f)
    val preferredFloor = preferredViewportFraction
        ?.takeIf { it > 0f }
        ?.let { (it / 8f).coerceAtLeast(0.001f) }
    return minOf(sampleBasedFloor, preferredFloor ?: sampleBasedFloor).coerceIn(0.001f, 1f)
}

internal fun visibleChartPointWindow(
    points: List<InspectorChartPoint>,
    xBounds: NumericBounds
): List<InspectorChartPoint> {
    if (points.isEmpty()) return emptyList()
    val startIndex = lowerEpochBound(points, xBounds.min)
    val endIndex = upperEpochBound(points, xBounds.max)
    if (startIndex >= endIndex) return emptyList()
    return points.subList(startIndex, endIndex)
}

private fun lowerEpochBound(points: List<InspectorChartPoint>, epochMillis: Double): Int {
    var low = 0
    var high = points.size
    while (low < high) {
        val mid = (low + high) / 2
        if (points[mid].epochMillis.toDouble() < epochMillis) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    return low
}

private fun upperEpochBound(points: List<InspectorChartPoint>, epochMillis: Double): Int {
    var low = 0
    var high = points.size
    while (low < high) {
        val mid = (low + high) / 2
        if (points[mid].epochMillis.toDouble() <= epochMillis) {
            low = mid + 1
        } else {
            high = mid
        }
    }
    return low
}

private fun lineFullXBounds(
    points: List<InspectorChartPoint>,
    domainStartEpochMillis: Long?,
    domainEndEpochMillis: Long?,
    xAxisLabel: String,
    padDomain: Boolean
): NumericBounds {
    val explicitStart = domainStartEpochMillis?.toDouble()
    val explicitEnd = domainEndEpochMillis?.toDouble()
    if (explicitStart != null && explicitEnd != null && explicitEnd > explicitStart) {
        return NumericBounds(explicitStart, explicitEnd)
    }
    if (points.isEmpty()) return NumericBounds(0.0, 1.0)
    if (points.size == 1) {
        val center = points.first().epochMillis.toDouble()
        val padding = if (xAxisLabel.contains("Date", ignoreCase = true)) {
            12 * 60 * 60 * 1000.0
        } else {
            60 * 60 * 1000.0
        }
        return NumericBounds(center - padding, center + padding)
    }
    val minValue = points.first().epochMillis.toDouble()
    val maxValue = points.last().epochMillis.toDouble()
    val padding = if (padDomain) max((maxValue - minValue) * 0.02, 1.0) else 0.0
    return NumericBounds(minValue - padding, maxValue + padding)
}

private fun visibleXBounds(
    fullBounds: NumericBounds,
    viewportStartFraction: Float,
    viewportFraction: Float
): NumericBounds {
    val visibleRange = fullBounds.range * viewportFraction.coerceIn(0f, 1f)
    if (visibleRange >= fullBounds.range) return fullBounds
    val maxStart = fullBounds.range - visibleRange
    val start = fullBounds.min + maxStart * viewportStartFraction.coerceIn(0f, 1f)
    return NumericBounds(start, start + visibleRange)
}

private fun preferredViewportFractions(
    fullBounds: NumericBounds,
    preferred: ChartVisibleRange
): Pair<Float, Float>? {
    if (fullBounds.range <= 0.0) return null
    val rawStart = preferred.startEpochMillis.toDouble()
    val rawEnd = preferred.endEpochMillis.toDouble()
    if (rawEnd <= rawStart) return null
    val start = rawStart.coerceIn(fullBounds.min, fullBounds.max)
    val end = rawEnd.coerceIn(fullBounds.min, fullBounds.max)
    val visibleRange = (end - start).takeIf { it > 0.0 } ?: return null
    val fraction = (visibleRange / fullBounds.range).toFloat().coerceIn(0.001f, 1f)
    val scrollableRange = (fullBounds.range - visibleRange).coerceAtLeast(0.0)
    val startFraction = if (scrollableRange <= 0.0) {
        0f
    } else {
        ((start - fullBounds.min) / scrollableRange).toFloat().coerceIn(0f, 1f)
    }
    return startFraction to fraction
}

private fun lineTimeTicks(bounds: NumericBounds, zoneId: ZoneId): List<AxisTick> {
    val visibleDurationMillis = bounds.range.roundToLong().coerceAtLeast(1L)
    return listOf(0f, 0.33f, 0.66f, 1f).map { fraction ->
        val epochMillis = (bounds.min + (bounds.range * fraction)).roundToLong()
        AxisTick(fraction, formatTimeTick(epochMillis, visibleDurationMillis, zoneId))
    }.distinctBy { it.label }
}

private fun heartRateTicks(bounds: NumericBounds): List<AxisTick> {
    val start = ((bounds.min / 20.0).toInt() * 20).coerceAtLeast(20)
    val end = (((bounds.max + 19.0) / 20.0).toInt() * 20).coerceAtLeast(start + 20)
    return (start..end step 20)
        .map { bpm ->
            AxisTick(
                fraction = ((bpm.toDouble() - bounds.min) / bounds.range).toFloat().coerceIn(0f, 1f),
                label = bpm.toString()
            )
        }
        .distinctBy { it.label }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeartRateReferenceBands(
    plot: PlotArea,
    yBounds: NumericBounds,
    zones: HeartRateReferenceZones,
    labelPaint: Paint
) {
    zones.bands.forEach { band ->
        val lower = band.lowerBpm.coerceAtLeast(yBounds.min)
        val upper = band.upperBpm.coerceAtMost(yBounds.max)
        if (upper <= lower) return@forEach
        val yTop = plot.bottom - (((upper - yBounds.min) / yBounds.range).toFloat() * plot.height)
        val yBottom = plot.bottom - (((lower - yBounds.min) / yBounds.range).toFloat() * plot.height)
        val color = when (band.tone) {
            HeartRateZoneTone.REFERENCE -> androidx.compose.ui.graphics.Color(0xFF4D8B6F)
            HeartRateZoneTone.ELEVATED -> androidx.compose.ui.graphics.Color(0xFFB77718)
            HeartRateZoneTone.HIGH -> androidx.compose.ui.graphics.Color(0xFFB94A3D)
        }
        drawRect(
            color = color.copy(alpha = 0.10f),
            topLeft = Offset(plot.left, yTop),
            size = androidx.compose.ui.geometry.Size(plot.width, (yBottom - yTop).coerceAtLeast(1f))
        )
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(
                band.label,
                plot.right - 6.dp.toPx(),
                yTop + ((yBottom - yTop) / 2f) + (labelPaint.textSize / 3f),
                labelPaint
            )
        }
    }
}

private fun selectNearestLinePoint(
    touchX: Float,
    plotLeftPx: Float,
    plotRightPx: Float,
    xBounds: NumericBounds,
    visiblePoints: List<InspectorChartPoint>
): InspectorChartPoint? {
    if (visiblePoints.isEmpty()) return null
    if (visiblePoints.size == 1) return visiblePoints.first()
    val normalized = ((touchX - plotLeftPx) / (plotRightPx - plotLeftPx)).coerceIn(0f, 1f)
    val targetEpoch = xBounds.min + (xBounds.range * normalized)
    return nearestLinePointForEpoch(targetEpoch, visiblePoints)
}

private fun nearestLinePointForEpoch(
    targetEpoch: Double,
    points: List<InspectorChartPoint>
): InspectorChartPoint? {
    if (points.isEmpty()) return null
    var low = 0
    var high = points.lastIndex
    while (low <= high) {
        val mid = (low + high) / 2
        val midValue = points[mid].epochMillis.toDouble()
        when {
            midValue < targetEpoch -> low = mid + 1
            midValue > targetEpoch -> high = mid - 1
            else -> return points[mid]
        }
    }
    val before = points.getOrNull((low - 1).coerceAtLeast(0))
    val after = points.getOrNull(low.coerceAtMost(points.lastIndex))
    return listOfNotNull(before, after).minByOrNull { abs(it.epochMillis - targetEpoch) }
}
