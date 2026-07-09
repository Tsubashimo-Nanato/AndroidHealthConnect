package com.example.healthconnectandroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.debug.DetailQueryDiagnostic
import com.example.healthconnectandroid.debug.DeviceSmokeDiagnostics
import com.example.healthconnectandroid.debug.MatrixGestureDiagnostic
import com.example.healthconnectandroid.debug.SyncDiagnostic
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.QueryCard
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant

@Composable
fun DebugScreen(
    modifier: Modifier,
    platformGranted: Boolean,
    hrHcGranted: Boolean,
    status: String,
    diagnostics: DeviceSmokeDiagnostics,
    onSyncHours: (Long) -> Unit,
    onQuery: (Instant) -> Unit,
    onRequestClear: () -> Unit
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DebugToolsSection(
            platformGranted = platformGranted,
            hrHcGranted = hrHcGranted,
            status = status,
            diagnostics = diagnostics,
            onSyncHours = onSyncHours,
            onQuery = onQuery,
            onRequestClear = onRequestClear
        )
    }
}

@Composable
fun DebugToolsSection(
    platformGranted: Boolean,
    hrHcGranted: Boolean,
    status: String,
    diagnostics: DeviceSmokeDiagnostics,
    onSyncHours: (Long) -> Unit,
    onQuery: (Instant) -> Unit,
    onRequestClear: () -> Unit,
    firstRowIndex: Int = 0
) {
    var showSmokeDiagnostics by rememberSaveable { mutableStateOf(false) }

    AppSection(
        title = "Smoke-Test Diagnostics",
        subtitle = "Counts and ranges only",
        modifier = Modifier.rowFadeIn(firstRowIndex)
    ) {
        SecondaryActionButton(
            label = if (showSmokeDiagnostics) "Hide Diagnostics" else "Show Diagnostics",
            onClick = { showSmokeDiagnostics = !showSmokeDiagnostics }
        )
        if (showSmokeDiagnostics) {
            DiagnosticMatrixBlock(diagnostics.matrix)
            DiagnosticSyncBlock(diagnostics.sync)
            DiagnosticDetailBlock(diagnostics.detail)
        }
    }

    AppSection(
        title = "Heart-Rate Compatibility Tools",
        subtitle = "Kept for older heart-rate debug paths",
        modifier = Modifier.rowFadeIn(firstRowIndex + 1)
    ) {
        Text(
            uiText("Requires platform and Health Connect heart-rate access."),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AppActionRow {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = platformGranted && hrHcGranted,
                onClick = { onSyncHours(6) }
            ) { Text(uiText("Sync 6h")) }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = platformGranted && hrHcGranted,
                onClick = { onSyncHours(24) }
            ) { Text(uiText("Sync 24h")) }
        }
    }

    QueryCard(onQuery = onQuery)

    AppSection(
        title = "Sleep Scoring",
        subtitle = "Placeholder visual guide",
        modifier = Modifier.rowFadeIn(firstRowIndex + 3)
    ) {
        Text(
            uiText(
                "Sleep tags and quality colors currently use simple duration, nap, and extreme stage-churn rules only. " +
                    "They are not medical advice or a validated sleep score."
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    AppSection(
        title = "Cache Management",
        subtitle = "Developer-only local database action",
        modifier = Modifier.rowFadeIn(firstRowIndex + 4)
    ) {
        Text(
            uiText("Removing local data clears app rows and sync history. Health Connect data is not deleted."),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onRequestClear, modifier = Modifier.fillMaxWidth()) {
            Text(uiText("Remove Local Data"))
        }
    }

    StatusMessageCard(status)
}

@Composable
private fun DiagnosticMatrixBlock(value: MatrixGestureDiagnostic) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(uiText("Matrix Gesture"), style = MaterialTheme.typography.titleSmall)
        Text(uiText(value.updatedAt?.toString() ?: "No event"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DiagnosticRow("Type", value.matrixType)
        DiagnosticRow("Action", value.action)
        DiagnosticRow("Delta / snap", value.deltaSnap)
        DiagnosticRow("Selected", value.selectedCount.toString())
        DiagnosticRow("Scroll preserved", if (value.parentScrollPreserved) "Yes" else "No")
    }
}

@Composable
private fun DiagnosticSyncBlock(value: SyncDiagnostic) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(uiText("Sync"), style = MaterialTheme.typography.titleSmall)
        Text(uiText(value.updatedAt?.toString() ?: "No event"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DiagnosticRow("Mode", value.mode)
        DiagnosticRow("Type", value.currentType ?: "None")
        DiagnosticRow("Range", value.requestedRange)
        DiagnosticRow("Rows", "ins ${value.inserted}, upd ${value.updated}, dup ${value.duplicates}, err ${value.errors}")
        DiagnosticRow("State", value.state)
    }
}

@Composable
private fun DiagnosticDetailBlock(value: DetailQueryDiagnostic) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(uiText("Detail Query"), style = MaterialTheme.typography.titleSmall)
        Text(uiText(value.updatedAt?.toString() ?: "No event"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DiagnosticRow("Type", value.dataType)
        DiagnosticRow("Range", value.queryRange)
        DiagnosticRow("Chart points", value.chartPointCount.toString())
        DiagnosticRow("Record page", value.recordPageSize.toString())
        DiagnosticRow("Expanded id", value.expandedRecordId?.toString() ?: "None")
        value.sleepSessionCount?.let { DiagnosticRow("Sleep sessions", it.toString()) }
        value.matrixCellCount?.let { DiagnosticRow("Matrix cells", it.toString()) }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            uiText(label),
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            uiText(value),
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
