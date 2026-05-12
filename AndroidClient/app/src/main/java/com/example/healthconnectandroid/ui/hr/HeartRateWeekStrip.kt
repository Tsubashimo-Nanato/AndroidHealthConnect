package com.example.healthconnectandroid.ui.hr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

@Composable
internal fun HeartRateWeekStrip(
    cells: List<HrDateCell>,
    selectedDates: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val selectedScrollDate = selectedDates.maxOrNull()

    LaunchedEffect(cells.firstOrNull()?.date, cells.lastOrNull()?.date, selectedScrollDate) {
        val selectedIndex = selectedScrollDate
            ?.let { date -> cells.indexOfFirst { it.date == date } }
            ?.takeIf { it >= 0 }
            ?: cells.lastIndex
        if (selectedIndex < 0) return@LaunchedEffect

        val visibleIndexes = listState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visibleIndexes.isEmpty() || selectedIndex !in visibleIndexes) {
            listState.scrollToItem((selectedIndex - 3).coerceAtLeast(0))
        }
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        state = listState,
        flingBehavior = flingBehavior
    ) {
        items(cells, key = { it.date }) { cell ->
            HeartRateDateCell(
                cell = cell,
                selected = cell.date in selectedDates,
                active = true,
                modifier = Modifier
                    .width(52.dp)
                    .height(52.dp)
                    .clickable { onDateSelected(cell.date) }
            )
        }
    }
}
