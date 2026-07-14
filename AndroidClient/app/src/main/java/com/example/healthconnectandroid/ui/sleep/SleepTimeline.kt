package com.example.healthconnectandroid.ui.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.example.healthconnectandroid.hc.SleepQualityBand
import com.example.healthconnectandroid.hc.SleepQualityMatrixModel
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
    val qualityBand: SleepQualityBand?,
    val totalSleepMinutes: Long?,
    val periods: List<SleepClockPeriod>
)

@Composable
// Weekly and monthly views share one clock rail so stage colors and interaction cannot drift apart.
fun SleepTimeline(
    model: SleepQualityMatrixModel,
    sessions: List<SleepSessionUiModel>,
    selectedBoxIds: Set<String>,
    onSelectedBoxIdsChange: (Set<String>) -> Unit,
    zoneId: ZoneId = ZoneId.systemDefault(),
    modifier: Modifier = Modifier
) {
    val days = remember(model, sessions, zoneId) {
        sleepTimelineDays(model, sessions, zoneId)
    }
    val timelineState = rememberScrollState()
    val selectedIndex = days.indexOfLast { it.date.toString() in selectedBoxIds }
    LaunchedEffect(days, selectedIndex, timelineState.maxValue) {
        if (days.isEmpty() || timelineState.maxValue == 0) return@LaunchedEffect
        val target = if (selectedIndex <= 0 || days.size <= 1) {
            0
        } else {
            (timelineState.maxValue * (selectedIndex.toFloat() / days.lastIndex)).toInt()
        }
        timelineState.scrollTo(target.coerceIn(0, timelineState.maxValue))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SleepStageLegend()
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dayWidth = sleepTimelineColumnWidth(maxWidth)
            Row(Modifier.fillMaxWidth()) {
                SleepTimeAxis()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(timelineState),
                    horizontalArrangement = Arrangement.spacedBy(TimelineColumnSpacing)
                ) {
                    days.forEach { day ->
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
        Text(
            uiText(if (days.size == 7) "Scroll to explore 7 days" else "Scroll through dates"),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SleepStageLegend() {
    Row(
        modifier = Modifier
            .height(20.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Awake", "REM", "Light sleep", "Deep sleep", "Sleeping").forEach { label ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Surface(
                    modifier = Modifier.width(6.dp).height(6.dp),
                    shape = RoundedCornerShape(99.dp),
                    color = sleepStageColor(label)
                ) {}
                Text(uiText(label), style = MaterialTheme.typography.labelSmall)
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
        Spacer(Modifier.height(44.dp))
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
private fun SleepTimelineDayColumn(
    day: SleepTimelineDay,
    width: Dp,
    selected: Boolean,
    onClick: () -> Unit
) {
    val durationColors = sleepDurationColors(day.totalSleepMinutes)
    val accessibilityLabel = buildString {
        append(day.date)
        append(", ")
        append(uiText(day.qualityBand?.label ?: "No data"))
    }
    val containerColor = durationColors.container.copy(alpha = if (selected) 1f else 0.76f)
    Box(
        modifier = Modifier
            .width(width)
            .drawBehind {
                val radius = 12.dp.toPx()
                drawRoundRect(containerColor, cornerRadius = CornerRadius(radius))
            }
            .semantics {
                contentDescription = accessibilityLabel
                this.selected = selected
            }
            .clickable(onClick = onClick)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = sleepTimelineDateLabel(day.date),
                modifier = Modifier.height(44.dp).padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = durationColors.content,
                textAlign = TextAlign.Center
            )
            SleepDayRail(day.periods, width)
        }
    }
}

@Composable
private fun SleepDayRail(periods: List<SleepClockPeriod>, width: Dp) {
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f)
    Canvas(
        modifier = Modifier
            .width(width)
            .height(TimelineRailHeight)
            .padding(horizontal = 7.dp)
    ) {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        val trackWidth = 26.dp.toPx()
        val trackLeft = (size.width - trackWidth) / 2f
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(trackLeft, 0f),
            size = Size(trackWidth, size.height),
            cornerRadius = CornerRadius(trackWidth / 2f)
        )
        periods.forEach { period ->
            val top = size.height * (period.startMinute / MinutesPerDay)
            val bottom = size.height * (period.endMinute / MinutesPerDay)
            drawRoundRect(
                color = sleepStageColor(period.stageLabel),
                topLeft = Offset(trackLeft + 3.dp.toPx(), top),
                size = Size(trackWidth - 6.dp.toPx(), (bottom - top).coerceAtLeast(3.dp.toPx())),
                cornerRadius = CornerRadius(5.dp.toPx())
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
        session.analysisStart?.atZone(zoneId)?.toLocalDate()
    }
    return model.boxes.map { box ->
        val daySessions = sessionsByDate[box.startDate].orEmpty()
        val validDurations = daySessions.mapNotNull { session ->
            val start = session.analysisStart ?: return@mapNotNull null
            val end = session.analysisEnd ?: return@mapNotNull null
            if (end.isAfter(start)) Duration.between(start, end) else null
        }
        val periods = daySessions.flatMap { session ->
            val stagePeriods = session.stages.flatMap stageLoop@{ stage ->
                val start = stage.startTime ?: return@stageLoop emptyList()
                val end = stage.endTime ?: return@stageLoop emptyList()
                sleepClockPeriods(start, end, sleepStageLabel(stage), zoneId)
            }
            if (stagePeriods.isNotEmpty()) {
                stagePeriods
            } else {
                val start = session.analysisStart
                val end = session.analysisEnd
                if (start == null || end == null) emptyList() else sleepClockPeriods(start, end, "Sleeping", zoneId)
            }
        }
        SleepTimelineDay(
            date = box.startDate,
            qualityBand = box.qualityBand,
            totalSleepMinutes = validDurations
                .takeIf { it.isNotEmpty() }
                ?.sumOf { it.toMinutes() },
            periods = periods
        )
    }
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
private val TimelineDateFormatter = DateTimeFormatter.ofPattern("M/d\nEEE", Locale.US)
private val TimelineRailHeight = 288.dp
private val TimelineAxisWidth = 44.dp
private val TimelineColumnSpacing = 4.dp
private const val VisibleTimelineColumns = 5
private const val MinutesPerDay = 1440f
