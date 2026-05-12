package com.example.healthconnectandroid.ui.hr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.HrDateSelectionMode
import com.example.healthconnectandroid.ui.SegmentedSwitch
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun HeartRateDateSelector(
    mode: HrDateSelectionMode,
    onModeChange: (HrDateSelectionMode) -> Unit,
    windowLabel: String,
    anchorDate: LocalDate,
    today: LocalDate,
    weekStart: DayOfWeek,
    weekCells: List<HrDateCell>,
    cellByDate: Map<LocalDate, HrDateCell>,
    selectedDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onAnchorDateChange: (LocalDate) -> Unit,
    onGestureDiagnostic: (action: String, deltaSnap: Int?, selectedCount: Int) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SegmentedSwitch(
                options = HrDateSelectionMode.entries,
                selected = mode,
                label = { it.label },
                onSelected = onModeChange
            )
            if (mode == HrDateSelectionMode.WEEK) {
                Text(
                    windowLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                HeartRateWeekStrip(
                    cells = weekCells,
                    selectedDates = selectedDates,
                    onDateSelected = { date ->
                        onDateSelected(date)
                        onGestureDiagnostic("tap", null, 1)
                    }
                )
            } else {
                HeartRateMonthGrid(
                    anchorDate = anchorDate,
                    today = today,
                    weekStart = weekStart,
                    cellByDate = cellByDate,
                    selectedDates = selectedDates,
                    onDateSelected = { date ->
                        onDateSelected(date)
                        onGestureDiagnostic("tap", null, 1)
                    },
                    onAnchorDateChange = { date ->
                        val delta = if (date > anchorDate) 1 else if (date < anchorDate) -1 else 0
                        onAnchorDateChange(date)
                        onGestureDiagnostic("pan", delta, selectedDates.size)
                    }
                )
            }
        }
    }
}
