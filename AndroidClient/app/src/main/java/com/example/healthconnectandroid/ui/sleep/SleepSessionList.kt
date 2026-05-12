package com.example.healthconnectandroid.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.ReadableHealthRecord
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyStateText
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import java.time.ZoneId

@Composable
fun SessionTimeline(rows: List<ReadableHealthRecord>) {
    SleepSessionsSection(remember(rows) { sleepSessionModels(rows) })
}

@Composable
fun SleepSessionsSection(
    sessionModels: List<SleepSessionUiModel>,
    zoneId: ZoneId = ZoneId.systemDefault()
) {
    AppSection(title = "Sleep Sessions", modifier = Modifier.rowFadeIn(3)) {
        if (sessionModels.isEmpty()) {
            EmptyStateText("No sessions in this range")
            return@AppSection
        }
        var expandedSessionId by remember { mutableStateOf<Long?>(null) }
        LaunchedEffect(sessionModels) {
            val visibleSessionIds = sessionModels.mapTo(mutableSetOf()) { it.session.localRecordId }
            if (expandedSessionId !in visibleSessionIds) {
                expandedSessionId = null
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(sessionModels, key = { _, model -> model.session.localRecordId }) { index, model ->
                val expanded = expandedSessionId == model.session.localRecordId
                SleepSessionCard(
                    model = model,
                    expanded = expanded,
                    onToggleExpanded = {
                        expandedSessionId = if (expanded) null else model.session.localRecordId
                    },
                    modifier = Modifier.rowFadeIn(index, enabled = sessionModels.size <= 24),
                    zoneId = zoneId
                )
            }
        }
    }
}
