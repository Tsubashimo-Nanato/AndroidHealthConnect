package com.example.healthconnectandroid.ui.sleep

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.healthconnectandroid.hc.HealthDisplayFormatter
import com.example.healthconnectandroid.hc.ReadableHealthRecord
import com.example.healthconnectandroid.ui.EmptyStateText
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun SleepStagePlot(
    session: ReadableHealthRecord,
    stages: List<ReadableHealthRecord>,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val blocks = remember(session, stages) { sleepStageBlocks(session, stages) }
    if (blocks.isEmpty()) {
        EmptyStateText("No stage blocks available for this sleep session")
        return
    }

    val axisColor = MaterialTheme.colorScheme.outline
    val gridColor = axisColor.copy(alpha = 0.24f)
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    val labels = remember(blocks) { sleepStageLabels(blocks) }
    val sessionStart = blocks.minOf { it.startEpochMillis }
    val sessionEnd = blocks.maxOf { it.endEpochMillis }.coerceAtLeast(sessionStart + 1L)
    val xBounds = SleepNumericBounds(sessionStart.toDouble(), sessionEnd.toDouble())
    var chartWidthPx by remember(blocks) { mutableFloatStateOf(0f) }
    var chartHeightPx by remember(blocks) { mutableFloatStateOf(0f) }
    var selectedBlock by remember(blocks) { mutableStateOf<SleepStageBlock?>(null) }

    val tickTextPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    tickTextPaint.color = onSurfaceColor.toArgb()
    tickTextPaint.textSize = with(density) { 10.sp.toPx() }
    tickTextPaint.textAlign = Paint.Align.CENTER

    val yLabelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    yLabelPaint.color = onSurfaceColor.toArgb()
    yLabelPaint.textSize = with(density) { 9.sp.toPx() }
    yLabelPaint.textAlign = Paint.Align.RIGHT

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .onSizeChanged {
                chartWidthPx = it.width.toFloat()
                chartHeightPx = it.height.toFloat()
            }
            .pointerInput(blocks, chartWidthPx, chartHeightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    selectedBlock = selectSleepStageBlock(
                        touchX = down.position.x,
                        plotLeftPx = 64.dp.toPx(),
                        plotRightPx = (chartWidthPx - 12.dp.toPx()).coerceAtLeast(65.dp.toPx()),
                        xBounds = xBounds,
                        blocks = blocks
                    )
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        selectedBlock = selectSleepStageBlock(
                            touchX = change.position.x,
                            plotLeftPx = 64.dp.toPx(),
                            plotRightPx = (chartWidthPx - 12.dp.toPx()).coerceAtLeast(65.dp.toPx()),
                            xBounds = xBounds,
                            blocks = blocks
                        )
                        change.consume()
                    }
                }
            }
    ) {
        val plot = SleepPlotArea(
            left = 64.dp.toPx(),
            top = 14.dp.toPx(),
            right = size.width - 12.dp.toPx(),
            bottom = size.height - 42.dp.toPx()
        )
        val laneHeight = plot.height / labels.size.coerceAtLeast(1)

        drawLine(axisColor, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), strokeWidth = 1.dp.toPx())
        drawLine(axisColor, Offset(plot.left, plot.top), Offset(plot.left, plot.bottom), strokeWidth = 1.dp.toPx())

        labels.forEachIndexed { index, label ->
            val centerY = plot.top + laneHeight * (index + 0.5f)
            drawLine(gridColor, Offset(plot.left, centerY), Offset(plot.right, centerY), strokeWidth = 1.dp.toPx())
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(label, plot.left - 8.dp.toPx(), centerY + yLabelPaint.textSize / 3f, yLabelPaint)
            }
        }

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val x = plot.left + fraction * plot.width
            val epochMillis = (xBounds.min + xBounds.range * fraction).roundToLong()
            drawLine(axisColor, Offset(x, plot.bottom), Offset(x, plot.bottom + 4.dp.toPx()), strokeWidth = 1.dp.toPx())
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(
                    shortTimeTickFormatter.withZone(zoneId).format(Instant.ofEpochMilli(epochMillis)),
                    x,
                    plot.bottom + 16.dp.toPx(),
                    tickTextPaint
                )
            }
        }

        blocks.forEach { block ->
            val x0 = plot.left + (((block.startEpochMillis - xBounds.min) / xBounds.range).toFloat() * plot.width)
            val x1 = plot.left + (((block.endEpochMillis - xBounds.min) / xBounds.range).toFloat() * plot.width)
            val lane = labels.indexOf(block.label).coerceAtLeast(0)
            val centerY = plot.top + laneHeight * (lane + 0.5f)
            val blockHeight = (laneHeight * 0.58f).coerceAtLeast(8.dp.toPx())
            val blockColor = sleepStageColor(block.label)
            drawRect(
                color = blockColor.copy(alpha = if (block == selectedBlock) 0.92f else 0.64f),
                topLeft = Offset(x0, centerY - blockHeight / 2f),
                size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(2.dp.toPx()), blockHeight)
            )
            if (block == selectedBlock) {
                drawLine(
                    color = selectedColor,
                    start = Offset(x0, plot.top),
                    end = Offset(x0, plot.bottom),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }

    selectedBlock?.let { block ->
        Text(
            "${block.label}: ${formatEpoch(block.startEpochMillis, zoneId)} to ${formatEpoch(block.endEpochMillis, zoneId)} (${block.durationText})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



private data class SleepStageBlock(
    val label: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val durationText: String,
    val rawText: String?
)

private fun sleepStageBlocks(
    session: ReadableHealthRecord,
    stages: List<ReadableHealthRecord>
): List<SleepStageBlock> {
    val rawBlocks = stages.mapNotNull { stage ->
        val start = stage.startTime ?: return@mapNotNull null
        val end = stage.endTime ?: return@mapNotNull null
        if (!end.isAfter(start)) return@mapNotNull null
        SleepStageBlock(
            label = sleepStageLabel(stage),
            startEpochMillis = start.toEpochMilli(),
            endEpochMillis = end.toEpochMilli(),
            durationText = HealthDisplayFormatter.formatDurationForUi(Duration.between(start, end)),
            rawText = stage.rawDetailsText
        )
    }.sortedBy { it.startEpochMillis }
    if (rawBlocks.isEmpty()) return emptyList()

    val stageStart = Instant.ofEpochMilli(rawBlocks.minOf { it.startEpochMillis })
    val stageEnd = Instant.ofEpochMilli(rawBlocks.maxOf { it.endEpochMillis })
    val sessionStart = session.startTime?.takeIf { !it.isAfter(stageEnd) } ?: stageStart
    val sessionEnd = session.endTime?.takeIf { it.isAfter(sessionStart) && !it.isBefore(stageStart) } ?: stageEnd
    if (!sessionEnd.isAfter(sessionStart)) return rawBlocks

    val clipped = rawBlocks.mapNotNull { block ->
        val start = Instant.ofEpochMilli(block.startEpochMillis)
        val end = Instant.ofEpochMilli(block.endEpochMillis)
        val clippedStart = if (start.isBefore(sessionStart)) sessionStart else start
        val clippedEnd = if (end.isAfter(sessionEnd)) sessionEnd else end
        if (!clippedEnd.isAfter(clippedStart)) return@mapNotNull null
        block.copy(
            startEpochMillis = clippedStart.toEpochMilli(),
            endEpochMillis = clippedEnd.toEpochMilli(),
            durationText = HealthDisplayFormatter.formatDurationForUi(Duration.between(clippedStart, clippedEnd))
        )
    }
    return clipped.ifEmpty { rawBlocks }
}

private fun sleepStageLabels(blocks: List<SleepStageBlock>): List<String> {
    val present = blocks.map { it.label }.toSet()
    val ordered = sleepStageDisplayOrder.filter { it in present }
    val unknown = present.filterNot { it in sleepStageDisplayOrder }.sorted()
    return (ordered + unknown).ifEmpty { listOf("Stage") }
}

private val sleepStageDisplayOrder = listOf(
    "Out of bed",
    "Awake",
    "REM",
    "Light sleep",
    "Sleeping",
    "Deep sleep",
    "Unknown / Unmapped",
    "Unknown"
)

private fun sleepStageLabel(stage: ReadableHealthRecord): String =
    stage.detailFields.firstOrNull { it.label == "Sleep stage" }?.value
        ?: stage.primaryText.substringBefore(" - ").ifBlank { "Unknown" }

private fun sleepStageColor(label: String): Color {
    val normalized = label.lowercase()
    return when {
        "out of bed" in normalized -> Color(0xFFB86A3A)
        "awake" in normalized -> Color(0xFFD39A2E)
        "rem" in normalized -> Color(0xFF557FBF)
        "light" in normalized -> Color(0xFF67A980)
        "deep" in normalized -> Color(0xFF4D5F9F)
        "sleep" in normalized -> Color(0xFF4D8B7A)
        else -> Color(0xFF8E8E8E)
    }
}

private fun selectSleepStageBlock(
    touchX: Float,
    plotLeftPx: Float,
    plotRightPx: Float,
    xBounds: SleepNumericBounds,
    blocks: List<SleepStageBlock>
): SleepStageBlock? {
    if (blocks.isEmpty()) return null
    val normalized = ((touchX - plotLeftPx) / (plotRightPx - plotLeftPx).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val targetEpoch = (xBounds.min + xBounds.range * normalized).roundToLong()
    return blocks.firstOrNull { targetEpoch in it.startEpochMillis..it.endEpochMillis }
        ?: blocks.minByOrNull {
            minOf(
                abs(it.startEpochMillis - targetEpoch),
                abs(it.endEpochMillis - targetEpoch)
            )
        }
}



private data class SleepPlotArea(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
}

private data class SleepNumericBounds(
    val min: Double,
    val max: Double
) {
    val range: Double get() = (max - min).coerceAtLeast(1e-9)
}

private fun formatEpoch(value: Long?, zoneId: ZoneId): String =
    value?.let { MetricDisplayFormatter.formatShortInstant(Instant.ofEpochMilli(it), zoneId) }.orEmpty()

private val shortTimeTickFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")
