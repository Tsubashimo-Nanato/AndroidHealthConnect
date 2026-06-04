package com.example.healthconnectandroid.ui.hr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.HeartRateDateAnalysis
import com.example.healthconnectandroid.hc.HrDateQuality
import com.example.healthconnectandroid.ui.gesture.horizontalWindowSwipe
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
internal fun HeartRateMonthGrid(
    anchorDate: LocalDate,
    today: LocalDate,
    weekStart: DayOfWeek,
    cellByDate: Map<LocalDate, HrDateCell>,
    selectedDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onAnchorDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val month = remember(anchorDate) { YearMonth.from(anchorDate.coerceAtMost(today)) }
    val todayMonth = remember(today) { YearMonth.from(today) }
    val firstLoadedMonth = remember(cellByDate) { cellByDate.keys.minOrNull()?.let { YearMonth.from(it) } }
    val canGoPrevious = firstLoadedMonth?.let { month > it } ?: false
    val canGoNext = month < todayMonth
    val dates = remember(month, weekStart) { HeartRateDateAnalysis.monthMatrixDates(month, weekStart) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                enabled = canGoPrevious,
                onClick = { onAnchorDateChange(anchorDate.minusMonths(1)) }
            ) {
                Text(uiText("Prev"), maxLines = 1)
            }
            Text(
                text = DateTimeFormatter.ofPattern("MMM yyyy").format(month),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                enabled = canGoNext,
                onClick = { onAnchorDateChange(anchorDate.plusMonths(1).coerceAtMost(today)) }
            ) {
                Text(uiText("Next"), maxLines = 1)
            }
        }

        Column(
            modifier = Modifier.horizontalWindowSwipe { delta ->
                val target = when {
                    delta < 0 && canGoPrevious -> anchorDate.minusMonths(1)
                    delta > 0 && canGoNext -> anchorDate.plusMonths(1).coerceAtMost(today)
                    else -> null
                }
                target?.let(onAnchorDateChange)
            },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dates.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    week.forEach { date ->
                        val inDisplayedMonth = YearMonth.from(date) == month
                        val selectable = inDisplayedMonth && date <= today
                        val cell = cellByDate[date] ?: HrDateCell(
                            date = date,
                            label = date.dayOfMonth.toString(),
                            quality = HrDateQuality.NO_DATA,
                            sampleCount = 0
                        )
                        HeartRateDateCell(
                            cell = cell.copy(label = date.dayOfMonth.toString()),
                            selected = date in selectedDates,
                            active = selectable,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .then(
                                    if (selectable) {
                                        Modifier.clickable { onDateSelected(date) }
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}
