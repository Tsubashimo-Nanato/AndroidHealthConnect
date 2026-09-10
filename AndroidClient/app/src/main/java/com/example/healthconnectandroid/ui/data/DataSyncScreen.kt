package com.example.healthconnectandroid.ui.data

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
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.LocalHealthStatus
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.AppLanguagePreference
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
import com.example.healthconnectandroid.ui.i18n.uiText
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage

@Composable
fun DataSyncScreen(
    modifier: Modifier,
    localHealthStatus: LocalHealthStatus?,
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
    val backgroundSyncActive = periodicEnabled && backgroundReadGranted
    val backgroundSyncLabel = when {
        !periodicEnabled -> "Auto sync off"
        backgroundSyncActive -> "Auto sync on"
        backgroundReadAvailable -> "Auto sync waiting"
        else -> "Auto sync unavailable"
    }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppActionRow(modifier = Modifier.rowFadeIn(0)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Records",
                value = localHealthStatus?.localRecordCount?.let(MetricDisplayFormatter::formatCount) ?: "...",
                supporting = "Local SQLite"
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                label = "Types",
                value = localHealthStatus?.localDataTypeCount?.let(MetricDisplayFormatter::formatCount) ?: "...",
                supporting = "With local data"
            )
        }

        AppSection(
            modifier = Modifier.rowFadeIn(1),
            title = "Current Status",
            subtitle = "Last sync and access health"
        ) {
            AppActionRow {
                StatusBadge(permissionSummaryBadge(grantedPermissions), permissionSummaryTone(grantedPermissions))
                StatusBadge(
                    backgroundSyncLabel,
                    if (backgroundSyncActive) StatusTone.Success else if (periodicEnabled) StatusTone.Warning else StatusTone.Neutral
                )
            }
            Text(
                if (backgroundReadGranted) {
                    uiText("Background read permission is granted.")
                } else if (backgroundReadAvailable) {
                    uiText("Background read can be enabled in Settings.")
                } else {
                    uiText("Manual sync is available; background read is unavailable on this device.")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                uiText(localHealthStatusText(localHealthStatus, displayPreferences)),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (status != "Ready") {
                StatusMessageCard(status, tone = statusTone)
            }
        }

        AppSection(
            modifier = Modifier.rowFadeIn(2),
            title = "Actions",
            subtitle = "Incremental changes from Health Connect"
        ) {
            PrimaryActionButton(
                if (syncing) {
                    syncButtonText(syncProgress?.progressPercent ?: 0, LocalAppLanguage.current)
                } else {
                    "Sync New Data"
                },
                enabled = !syncing,
                onClick = onSyncAll
            )
            if (syncing || syncProgress != null) {
                SyncProgressCard(syncProgress)
            }
        }
    }
}

private fun syncButtonText(percent: Int, language: AppLanguagePreference): String =
    if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
        "同步中 $percent%"
    } else {
        "Syncing $percent%"
    }

private fun localHealthStatusText(
    localHealthStatus: LocalHealthStatus?,
    displayPreferences: DisplayPreferences
): String {
    if (localHealthStatus == null) return "Local records are loading."
    val lastSync = MetricDisplayFormatter.formatShortInstant(localHealthStatus.lastSync, displayPreferences.zoneId)
    val syncStatus = localHealthStatus.lastSyncStatus ?: "no sync yet"
    return "Last sync: $lastSync ($syncStatus)"
}

private fun permissionSummaryBadge(grantedPermissions: Set<String>): String {
    val permissions = HealthDataTypeRegistry.implementedReadPermissions
    val granted = permissions.count(grantedPermissions::contains)
    return if (granted == permissions.size) "Data access ready" else "$granted/${permissions.size} access"
}

private fun permissionSummaryTone(grantedPermissions: Set<String>): StatusTone {
    val permissions = HealthDataTypeRegistry.implementedReadPermissions
    return if (permissions.all(grantedPermissions::contains)) StatusTone.Success else StatusTone.Warning
}
