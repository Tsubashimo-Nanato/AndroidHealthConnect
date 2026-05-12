package com.example.healthconnectandroid.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.DemoStatus
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.MetricCard
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.SyncProgressCard
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.format.DisplayPreferences
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter

@Composable
fun DashboardScreen(
    modifier: Modifier,
    demoStatus: DemoStatus?,
    grantedPermissions: Set<String>,
    backgroundReadAvailable: Boolean,
    backgroundReadGranted: Boolean,
    periodicEnabled: Boolean,
    status: String,
    statusTone: StatusTone,
    syncing: Boolean,
    syncProgress: SyncProgress?,
    displayPreferences: DisplayPreferences,
    onSyncAll: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.rowFadeIn(0),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Health Connect Data Sync", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Local health data viewer, CSV exporter, and sync demo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AppActionRow(modifier = Modifier.rowFadeIn(1)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Records",
                value = demoStatus?.localRecordCount?.let(MetricDisplayFormatter::formatCount) ?: "...",
                supporting = "Local SQLite"
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Types",
                value = demoStatus?.localDataTypeCount?.let(MetricDisplayFormatter::formatCount) ?: "...",
                supporting = "With local data"
            )
        }

        AppSection(
            modifier = Modifier.rowFadeIn(2),
            title = "Current Status",
            subtitle = "Last sync and access health"
        ) {
            AppActionRow {
                StatusBadge(permissionSummaryBadge(grantedPermissions), permissionSummaryTone(grantedPermissions))
                StatusBadge(
                    if (periodicEnabled) "Auto sync on" else "Auto sync off",
                    if (periodicEnabled) StatusTone.Success else StatusTone.Neutral
                )
            }
            Text(
                if (backgroundReadGranted) {
                    "Background read permission is granted."
                } else if (backgroundReadAvailable) {
                    "Background read can be enabled in Settings."
                } else {
                    "Manual sync is available; background read is unavailable on this device."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(demoStatusText(demoStatus, displayPreferences), color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusMessageCard(status, tone = statusTone)
        }

        AppSection(
            modifier = Modifier.rowFadeIn(3),
            title = "Actions",
            subtitle = "Smart sync reads a recent per-type window with a safety overlap"
        ) {
            PrimaryActionButton(
                if (syncing) "Syncing..." else "Smart Sync",
                enabled = !syncing,
                onClick = onSyncAll
            )
            if (syncing || syncProgress != null) {
                SyncProgressCard(syncProgress)
            }
            Text(
                "Use the bottom tabs for Data and Settings. Full resync and exports live in Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun demoStatusText(demoStatus: DemoStatus?, displayPreferences: DisplayPreferences): String {
    if (demoStatus == null) return "Local records are loading."
    val lastSync = MetricDisplayFormatter.formatShortInstant(demoStatus.lastSync, displayPreferences.zoneId)
    val syncStatus = demoStatus.lastSyncStatus ?: "no sync yet"
    return "Last sync: $lastSync ($syncStatus)"
}

private fun permissionSummaryBadge(grantedPermissions: Set<String>): String {
    val total = HealthDataTypeRegistry.implementedReadPermissions.size
    val granted = HealthDataTypeRegistry.implementedReadPermissions.count { it in grantedPermissions }
    return if (granted == total) "Data access ready" else "$granted/$total access"
}

private fun permissionSummaryTone(grantedPermissions: Set<String>): StatusTone {
    val total = HealthDataTypeRegistry.implementedReadPermissions.size
    val granted = HealthDataTypeRegistry.implementedReadPermissions.count { it in grantedPermissions }
    return if (granted == total) StatusTone.Success else StatusTone.Warning
}
