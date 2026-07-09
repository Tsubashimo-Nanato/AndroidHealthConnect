package com.example.healthconnectandroid.ui.medicine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.medicine.MedicineDayLogSummary
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusTone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun MedicineCalendarSection(
    daySummaries: List<MedicineDayLogSummary>,
    today: LocalDate,
    weekStart: DayOfWeek,
    modifier: Modifier = Modifier
) {
    var anchorDate by remember(today) { mutableStateOf(today) }
    val month = remember(anchorDate) { YearMonth.from(anchorDate.coerceAtMost(today)) }
    val todayMonth = remember(today) { YearMonth.from(today) }
    val summaryByDate = remember(daySummaries) { daySummaries.associateBy { it.date } }
    val firstLogMonth = remember(daySummaries) { daySummaries.minOfOrNull { YearMonth.from(it.date) } }
    val canGoPrevious = firstLogMonth?.let { month > it } ?: false
    val canGoNext = month < todayMonth
    val dates = remember(month, weekStart) { monthGridDates(month, weekStart) }

    AppSection(
        title = medicineUiText("Medicine Calendar"),
        subtitle = medicineUiText("Green days include at least one taken log"),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = canGoPrevious,
                onClick = { anchorDate = anchorDate.minusMonths(1) }
            ) {
                Text(medicineUiText("Prev"), maxLines = 1)
            }
            Text(
                text = DateTimeFormatter.ofPattern("MMM yyyy").format(month),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                enabled = canGoNext,
                onClick = { anchorDate = anchorDate.plusMonths(1).coerceAtMost(today) }
            ) {
                Text(medicineUiText("Next"), maxLines = 1)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            dates.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { date ->
                        MedicineCalendarDay(
                            date = date,
                            displayedMonth = month,
                            today = today,
                            summary = summaryByDate[date],
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(medicineUiText("Taken"), StatusTone.Success)
            StatusBadge(medicineUiText("Missed"), StatusTone.Warning)
            StatusBadge(medicineUiText("Skipped"), StatusTone.Neutral)
        }
    }
}

@Composable
private fun MedicineCalendarDay(
    date: LocalDate,
    displayedMonth: YearMonth,
    today: LocalDate,
    summary: MedicineDayLogSummary?,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val inMonth = YearMonth.from(date) == displayedMonth && date <= today
    val container = when {
        !inMonth -> scheme.surfaceVariant.copy(alpha = 0.10f)
        summary?.takenCount ?: 0 > 0 -> scheme.secondaryContainer.copy(alpha = 0.95f)
        summary?.missedCount ?: 0 > 0 -> scheme.tertiaryContainer.copy(alpha = 0.85f)
        summary?.skippedCount ?: 0 > 0 -> scheme.surfaceVariant.copy(alpha = 0.78f)
        else -> scheme.surfaceVariant.copy(alpha = 0.28f)
    }
    val content = when {
        !inMonth -> scheme.onSurfaceVariant.copy(alpha = 0.30f)
        summary?.takenCount ?: 0 > 0 -> scheme.onSecondaryContainer
        summary?.missedCount ?: 0 > 0 -> scheme.onTertiaryContainer
        else -> scheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = container,
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = if (summary?.hasAnyLog == true) 0.35f else 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                color = content,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private fun monthGridDates(month: YearMonth, weekStart: DayOfWeek): List<LocalDate> {
    val first = month.atDay(1)
    val offset = Math.floorMod(first.dayOfWeek.value - weekStart.value, 7)
    val start = first.minusDays(offset.toLong())
    return List(42) { start.plusDays(it.toLong()) }
}
