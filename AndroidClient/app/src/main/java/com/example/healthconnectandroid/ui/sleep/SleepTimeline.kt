package com.example.healthconnectandroid.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.healthconnectandroid.hc.SleepQualityMatrixModel
import com.example.healthconnectandroid.hc.sleepSessionDate
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class SleepClockPeriod(
    val startMinute: Float,
    val endMinute: Float,
    val stageLabel: String
)

internal data class SleepTimelineDay(
    val date: LocalDate,
    val totalSleepMinutes: Long?,
    val periods: List<SleepClockPeriod>
)

@Composable
fun SleepTimeline(
    model: SleepQualityMatrixModel,
    sessions: List<SleepSessionUiModel>,
    selectedBoxIds: Set<String>,
    onSelectedBoxIdsChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    val days = remember(model, sessions, zoneId) {
        sleepTimelineDays(model, sessions, zoneId)
    }
    val timelineState = rememberLazyListState()
    val selectedIndex = days.indexOfLast { it.date.toString() in selectedBoxIds }
    var positionedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(days, selectedIndex) {
        if (days.isEmpty()) return@LaunchedEffect
        val targetIndex = selectedIndex.takeIf { it >= 0 } ?: days.lastIndex
        val firstVisibleIndex = (targetIndex - VisibleTimelineColumns + 1).coerceAtLeast(0)
        if (positionedOnce) {
            timelineState.animateScrollToItem(firstVisibleIndex)
        } else {
            timelineState.scrollToItem(firstVisibleIndex)
            positionedOnce = true
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SleepStageLegend()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dayWidth = sleepTimelineColumnWidth(maxWidth)
            Row(Modifier.fillMaxWidth()) {
                SleepTimeAxis()
                Box(modifier = Modifier.weight(1f)) {
                    SleepTimelineGrid(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(TimelineRailHeight)
                            .align(Alignment.BottomStart)
                    )
                    LazyRow(
                        state = timelineState,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(TimelineColumnSpacing)
                    ) {
                        items(days, key = { it.date }) { day ->
                            SleepTimelineDayColumn(
                                day = day,
                                width = dayWidth,
                                selected = day.date.toString() in selectedBoxIds,
                                onClick = { onSelectedBoxIdsChange(setOf(day.date.toString())) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepStageLegend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .padding(start = TimelineAxisWidth),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("REM", "Light sleep", "Deep sleep").forEach { label ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Canvas(Modifier.width(14.dp).height(3.dp)) {
                    drawRoundRect(
                        color = sleepStageColor(label),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
                Text(
                    uiText(label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SleepTimeAxis() {
    Column(
        modifier = Modifier.width(TimelineAxisWidth),
        horizontalAlignment = Alignment.End
    ) {
        Box(Modifier.height(TimelineHeaderHeight).fillMaxWidth()) {
            Text(
                text = uiText("Time"),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(Modifier.height(TimelineRailHeight).fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxHeight().padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                timelineTicks.forEach { tick ->
                    Text(
                        tick,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepTimelineGrid(modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    Canvas(modifier) {
        TimelineTickFractions.forEach { fraction ->
            val y = size.height * fraction
            drawLine(
                color = gridColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
private fun SleepTimelineDayColumn(
    day: SleepTimelineDay,
    width: Dp,
    selected: Boolean,
    onClick: () -> Unit
) {
    val durationColor = day.totalSleepMinutes
        ?.let(::sleepDurationColor)
        ?: MaterialTheme.colorScheme.outlineVariant
    val accessibilityLabel = buildString {
        append(day.date)
        append(", ")
        val minutes = day.totalSleepMinutes
        if (minutes == null) {
            append(uiText("No data"))
        } else {
            append("${minutes / 60}h ${minutes % 60}m")
        }
    }
    Column(
        modifier = Modifier
            .width(width)
            .semantics {
                contentDescription = accessibilityLabel
                this.selected = selected
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.width(width).height(TimelineHeaderHeight)) {
            Text(
                text = sleepTimelineDateLabel(day.date),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )
            Canvas(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(if (selected) 28.dp else 20.dp)
                    .height(if (selected) 3.dp else 2.dp)
            ) {
                drawRoundRect(
                    color = durationColor.copy(alpha = if (selected) 1f else 0.78f),
                    cornerRadius = CornerRadius(size.height / 2f)
                )
            }
        }
        SleepDayRail(day.periods, width)
    }
}

@Composable
private fun SleepDayRail(periods: List<SleepClockPeriod>, width: Dp) {
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
    Canvas(
        modifier = Modifier
            .width(width)
            .height(TimelineRailHeight)
            .padding(horizontal = 8.dp)
    ) {
        val trackCenter = size.width / 2f
        drawLine(
            color = trackColor,
            start = Offset(trackCenter, 0f),
            end = Offset(trackCenter, size.height),
            strokeWidth = 1.dp.toPx()
        )
        val stageWidth = 18.dp.toPx()
        periods.forEach { period ->
            val startMinute = period.startMinute.coerceIn(0f, MinutesPerDay)
            val endMinute = period.endMinute.coerceIn(startMinute, MinutesPerDay)
            val top = size.height * (startMinute / MinutesPerDay)
            val bottom = size.height * (endMinute / MinutesPerDay)
            drawRoundRect(
                color = sleepStageColor(period.stageLabel),
                topLeft = Offset(trackCenter - stageWidth / 2f, top),
                size = Size(stageWidth, (bottom - top).coerceAtLeast(2.dp.toPx())),
                cornerRadius = CornerRadius(2.dp.toPx())
            )
        }
    }
}

internal fun sleepTimelineDays(
    model: SleepQualityMatrixModel,
    sessions: List<SleepSessionUiModel>,
    zoneId: ZoneId
): List<SleepTimelineDay> {
    val sessionsByDate = sessions.groupBy { session ->
        sleepSessionDate(session.analysisStart, session.analysisEnd, zoneId)
    }
    return model.boxes.map { box ->
        val daySessions = sessionsByDate[box.startDate].orEmpty()
        val validDurations = daySessions.mapNotNull { session ->
            val start = session.analysisStart ?: return@mapNotNull null
            val end = session.analysisEnd ?: return@mapNotNull null
            if (end.isAfter(start)) Duration.between(start, end) else null
        }
        val periods = daySessions.flatMap { session -> sleepTimelinePeriods(session, zoneId) }
        SleepTimelineDay(
            date = box.startDate,
            totalSleepMinutes = validDurations
                .takeIf { it.isNotEmpty() }
                ?.sumOf { it.toMinutes() },
            periods = periods
        )
    }
}

private fun sleepTimelinePeriods(session: SleepSessionUiModel, zoneId: ZoneId): List<SleepClockPeriod> {
    val stagePeriods = session.stages.flatMap stageLoop@{ stage ->
        val label = sleepStageLabel(stage)
        // Awake remains negative space, making interruptions legible on a narrow 24-hour rail.
        if (!isSleepTimelineStageVisible(label)) return@stageLoop emptyList()
        val start = stage.startTime ?: return@stageLoop emptyList()
        val end = stage.endTime ?: return@stageLoop emptyList()
        sleepClockPeriods(start, end, label, zoneId)
    }
    if (stagePeriods.isNotEmpty()) return stagePeriods

    val start = session.analysisStart ?: return emptyList()
    val end = session.analysisEnd ?: return emptyList()
    return sleepClockPeriods(start, end, "Sleeping", zoneId)
}

internal fun isSleepTimelineStageVisible(label: String): Boolean {
    val normalized = label.lowercase(Locale.ROOT)
    return "awake" !in normalized && "out of bed" !in normalized
}

internal fun sleepTimelineDateLabel(date: LocalDate): String =
    date.format(TimelineDateFormatter)

internal fun sleepTimelineColumnWidth(viewportWidth: Dp): Dp {
    val timelineWidth = viewportWidth - TimelineAxisWidth
    val totalSpacing = TimelineColumnSpacing * (VisibleTimelineColumns - 1)
    return ((timelineWidth - totalSpacing) / VisibleTimelineColumns.toFloat()).coerceAtLeast(44.dp)
}

internal fun sleepClockPeriods(
    start: Instant,
    end: Instant,
    stageLabel: String,
    zoneId: ZoneId
): List<SleepClockPeriod> {
    if (!end.isAfter(start)) return emptyList()
    if (Duration.between(start, end) >= Duration.ofDays(1)) {
        return listOf(SleepClockPeriod(0f, MinutesPerDay, stageLabel))
    }
    val localStart = start.atZone(zoneId)
    val localEnd = end.atZone(zoneId)
    val startMinute = localStart.toLocalTime().toSecondOfDay() / 60f
    val endMinute = localEnd.toLocalTime().toSecondOfDay() / 60f
    if (localStart.toLocalDate() == localEnd.toLocalDate()) {
        return listOf(SleepClockPeriod(startMinute, endMinute, stageLabel))
    }
    return buildList {
        if (startMinute < MinutesPerDay) add(SleepClockPeriod(startMinute, MinutesPerDay, stageLabel))
        if (endMinute > 0f) add(SleepClockPeriod(0f, endMinute, stageLabel))
    }
}

private val timelineTicks = listOf("00:00", "06:00", "12:00", "18:00", "24:00")
private val TimelineTickFractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
private val TimelineDateFormatter = DateTimeFormatter.ofPattern("M/d\nEEE", Locale.US)
private val TimelineRailHeight = 288.dp
private val TimelineHeaderHeight = 44.dp
private val TimelineAxisWidth = 44.dp
private val TimelineColumnSpacing = 4.dp
private const val VisibleTimelineColumns = 5
private const val MinutesPerDay = 1440f
