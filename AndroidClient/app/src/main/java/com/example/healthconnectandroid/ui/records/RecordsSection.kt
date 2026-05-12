package com.example.healthconnectandroid.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.RecordFullDetails
import com.example.healthconnectandroid.hc.RecordListItem
import com.example.healthconnectandroid.hc.RecordListPage
import com.example.healthconnectandroid.hc.RecordPagingPolicy
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyStateText
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import kotlinx.coroutines.launch

@Composable
fun RecordsSection(
    listKey: String,
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    loadRecordPage: suspend (offset: Int, limit: Int) -> RecordListPage,
    loadRecordDetails: suspend (Long) -> RecordFullDetails,
    onRecordPageLoaded: (Int) -> Unit = {},
    onExpandedRecordChanged: (Long?) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var rows by remember(listKey) { mutableStateOf<List<RecordListItem>>(emptyList()) }
    var nextOffset by remember(listKey) { mutableStateOf<Int?>(0) }
    var loadingPage by remember(listKey) { mutableStateOf(false) }
    var pageError by remember(listKey) { mutableStateOf<String?>(null) }
    var expandedRecordIds by remember(listKey) { mutableStateOf<Set<Long>>(emptySet()) }
    var loadingRecordIds by remember(listKey) { mutableStateOf<Set<Long>>(emptySet()) }
    var recordDetails by remember(listKey) { mutableStateOf<Map<Long, RecordFullDetails>>(emptyMap()) }
    var recordErrors by remember(listKey) { mutableStateOf<Map<Long, String>>(emptyMap()) }

    fun loadNextPage() {
        val offset = nextOffset ?: return
        if (loadingPage) return
        loadingPage = true
        pageError = null
        scope.launch {
            runCatching { loadRecordPage(offset, RecordPagingPolicy.DEFAULT_PAGE_SIZE) }
                .onSuccess { page ->
                    rows = (rows + page.items).distinctBy { it.rowKey }
                    nextOffset = page.nextOffset
                    onRecordPageLoaded(page.items.size)
                }
                .onFailure { error ->
                    pageError = error.message ?: "Unable to load records"
                }
            loadingPage = false
        }
    }

    LaunchedEffect(expanded, listKey) {
        if (expanded && rows.isEmpty() && nextOffset != null && !loadingPage) {
            loadingPage = true
            pageError = null
            runCatching { loadRecordPage(0, RecordPagingPolicy.DEFAULT_PAGE_SIZE) }
                .onSuccess { page ->
                    rows = page.items
                    nextOffset = page.nextOffset
                    onRecordPageLoaded(page.items.size)
                }
                .onFailure { error ->
                    pageError = error.message ?: "Unable to load records"
                }
            loadingPage = false
        }
    }

    fun toggleRecord(localRecordId: Long) {
        if (localRecordId in expandedRecordIds) {
            expandedRecordIds = expandedRecordIds - localRecordId
            recordDetails = recordDetails - localRecordId
            recordErrors = recordErrors - localRecordId
            onExpandedRecordChanged(expandedRecordIds.firstOrNull())
            return
        }
        expandedRecordIds = expandedRecordIds + localRecordId
        onExpandedRecordChanged(localRecordId)
        if (localRecordId in recordDetails || localRecordId in loadingRecordIds) return
        loadingRecordIds = loadingRecordIds + localRecordId
        scope.launch {
            val loaded = runCatching { loadRecordDetails(localRecordId) }
            loaded.onSuccess { detailRows ->
                recordDetails = recordDetails + (localRecordId to detailRows)
                recordErrors = recordErrors - localRecordId
            }.onFailure { error ->
                recordErrors = recordErrors + (localRecordId to (error.message ?: "Unable to load record details"))
            }
            loadingRecordIds = loadingRecordIds - localRecordId
        }
    }

    AppSection(title = "Records") {
        Text(
            "${MetricDisplayFormatter.formatCount(totalCount)} rows in this range",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SecondaryActionButton(
            label = if (expanded) "Hide Records" else "Show Records",
            onClick = onToggle
        )
        if (!expanded) return@AppSection
        if (rows.isEmpty() && loadingPage) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@AppSection
        }
        pageError?.let {
            StatusMessageCard("Record list failed: $it", tone = StatusTone.Error)
        }
        if (rows.isEmpty() && !loadingPage) {
            EmptyStateText("No local rows in selected range")
            return@AppSection
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(rows, key = { _, row -> row.rowKey }) { index, row ->
                val recordExpanded = row.localRecordId in expandedRecordIds
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .rowFadeIn(index, enabled = rows.size <= 40)
                        .clickable { toggleRecord(row.localRecordId) }
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(row.primaryText, style = MaterialTheme.typography.titleSmall)
                        if (row.secondaryText.isNotBlank()) {
                            Text(
                                row.secondaryText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (recordExpanded) {
                            when {
                                row.localRecordId in loadingRecordIds -> {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                                recordErrors[row.localRecordId] != null -> {
                                    StatusMessageCard(
                                        "Record load failed: ${recordErrors[row.localRecordId]}",
                                        tone = StatusTone.Error
                                    )
                                }
                                else -> {
                                    recordDetails[row.localRecordId]?.let { details ->
                                        ExpandedRecordDetails(details)
                                    }
                                        ?: EmptyStateText("No additional fields for this record")
                                }
                            }
                        }
                    }
                }
            }
            if (nextOffset != null || loadingPage) {
                item(key = "load-more-records") {
                    if (loadingPage) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        SecondaryActionButton(
                            label = "Load More",
                            onClick = ::loadNextPage
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedRecordDetails(details: RecordFullDetails) {
    if (details.valueRows.isEmpty() && details.readableFields.isEmpty()) {
        EmptyStateText("No additional fields for this record")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        details.readableFields.takeIf { it.isNotEmpty() }?.let { fields ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Fields", style = MaterialTheme.typography.titleSmall)
                    fields.forEach { field ->
                        Text("${field.label}: ${field.value}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (details.valueRows.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Values", style = MaterialTheme.typography.titleSmall)
                    details.valueRows.forEach { row ->
                        Text(row.primaryText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        details.metadata?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Metadata", style = MaterialTheme.typography.titleSmall)
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        details.rawDetails?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Raw fields", style = MaterialTheme.typography.titleSmall)
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
