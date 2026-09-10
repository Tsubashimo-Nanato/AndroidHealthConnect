package com.example.healthconnectandroid.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.sync.SyncMode
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.SyncProgressCard
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant

@Composable
fun SettingsScreen(
    periodicEnabled: Boolean,
    debugEnabled: Boolean,
    onOpenGeneral: () -> Unit,
    onOpenMedicine: () -> Unit,
    onOpenHealthConnect: () -> Unit,
    onOpenStorageAndTools: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.rowFadeIn(0),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                SettingsNavRow(
                    icon = Icons.Default.Tune,
                    title = "General",
                    subtitle = "Language, display, profile",
                    onClick = onOpenGeneral
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Sync,
                    title = "Health Connect",
                    subtitle = if (periodicEnabled) "Permissions, sync, upload" else "Permissions and upload",
                    onClick = onOpenHealthConnect
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Notifications,
                    title = "Medicine",
                    subtitle = "Schedule and dose checks",
                    onClick = onOpenMedicine
                )
                SettingsDivider()
                SettingsNavRow(
                    icon = Icons.Default.Storage,
                    title = "Storage & Tools",
                    subtitle = if (debugEnabled) {
                        "Exports, local storage, diagnostics"
                    } else {
                        "Exports and local storage"
                    },
                    onClick = onOpenStorageAndTools
                )
            }
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        headlineContent = { Text(uiText(title), style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(uiText(subtitle)) },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun SettingsPermissionsScreen(
    declaredReadHr: Boolean,
    platformGranted: Boolean,
    hcGranted: Boolean,
    backgroundReadStatus: String,
    backgroundReadGranted: Boolean,
    dataPermissionSummary: String,
    backgroundReadAvailable: Boolean,
    onRequestPlatform: () -> Unit,
    onRequestDataPermissions: () -> Unit,
    onRequestBackgroundRead: () -> Unit,
    openAppSettings: (Intent) -> Unit,
    packageName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PermissionsSettingsSection(
            declaredReadHr = declaredReadHr,
            platformGranted = platformGranted,
            hcGranted = hcGranted,
            backgroundReadStatus = backgroundReadStatus,
            backgroundReadGranted = backgroundReadGranted,
            dataPermissionSummary = dataPermissionSummary,
            backgroundReadAvailable = backgroundReadAvailable,
            onRequestPlatform = onRequestPlatform,
            onRequestDataPermissions = onRequestDataPermissions,
            onRequestBackgroundRead = onRequestBackgroundRead,
            openAppSettings = openAppSettings,
            packageName = packageName,
            modifier = Modifier.rowFadeIn(0)
        )
    }
}

@Composable
fun PermissionsSettingsSection(
    declaredReadHr: Boolean,
    platformGranted: Boolean,
    hcGranted: Boolean,
    backgroundReadStatus: String,
    backgroundReadGranted: Boolean,
    dataPermissionSummary: String,
    backgroundReadAvailable: Boolean,
    onRequestPlatform: () -> Unit,
    onRequestDataPermissions: () -> Unit,
    onRequestBackgroundRead: () -> Unit,
    openAppSettings: (Intent) -> Unit,
    packageName: String,
    modifier: Modifier = Modifier
) {
    AppSection(title = "Permissions", subtitle = "Local read access", modifier = modifier) {
        AppActionRow {
            StatusBadge(
                if (hcGranted) "Ready" else "Needs access",
                if (hcGranted) StatusTone.Success else StatusTone.Warning
            )
            StatusBadge(
                backgroundReadStatus,
                if (backgroundReadGranted) StatusTone.Success else StatusTone.Warning
            )
        }
        Text(
            uiText(dataPermissionSummary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PrimaryActionButton(if (hcGranted) "Review Access" else "Grant Access", onClick = onRequestDataPermissions)
        if (!platformGranted) {
            StatusBadge("Platform HR missing", StatusTone.Warning)
            SecondaryActionButton(
                label = "Request Platform HR",
                enabled = declaredReadHr,
                onClick = onRequestPlatform
            )
        }
        if (!backgroundReadGranted) {
            SecondaryActionButton(
                "Grant Background",
                enabled = backgroundReadAvailable,
                onClick = onRequestBackgroundRead
            )
        }
        SecondaryActionButton(
            label = "App Settings",
            onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                openAppSettings(intent)
            }
        )
    }
}

@Composable
fun SettingsSyncScreen(
    periodicEnabled: Boolean,
    backgroundReadAvailable: Boolean,
    backgroundReadGranted: Boolean,
    lastPeriodicSync: Instant?,
    lastPeriodicStatus: String?,
    lastPeriodicSummary: String?,
    status: String,
    busy: Boolean,
    syncProgress: SyncProgress?,
    onTogglePeriodic: () -> Unit,
    onFullResync: () -> Unit,
    onCancelFullResync: () -> Unit,
    onRunBackgroundNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SyncSettingsSection(
            periodicEnabled = periodicEnabled,
            backgroundReadAvailable = backgroundReadAvailable,
            backgroundReadGranted = backgroundReadGranted,
            lastPeriodicSync = lastPeriodicSync,
            lastPeriodicStatus = lastPeriodicStatus,
            lastPeriodicSummary = lastPeriodicSummary,
            status = status,
            busy = busy,
            syncProgress = syncProgress,
            onTogglePeriodic = onTogglePeriodic,
            onFullResync = onFullResync,
            onCancelFullResync = onCancelFullResync,
            onRunBackgroundNow = onRunBackgroundNow,
            modifier = Modifier.rowFadeIn(0)
        )
    }
}

@Composable
fun SyncSettingsSection(
    periodicEnabled: Boolean,
    backgroundReadAvailable: Boolean,
    backgroundReadGranted: Boolean,
    lastPeriodicSync: Instant?,
    lastPeriodicStatus: String?,
    lastPeriodicSummary: String?,
    status: String,
    busy: Boolean,
    syncProgress: SyncProgress?,
    onTogglePeriodic: () -> Unit,
    onFullResync: () -> Unit,
    onCancelFullResync: () -> Unit,
    onRunBackgroundNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val periodicScheduled = periodicEnabled && backgroundReadGranted
    val periodicLabel = when {
        !periodicEnabled -> "Off"
        periodicScheduled -> "Scheduled"
        backgroundReadAvailable -> "Permission needed"
        else -> "Unavailable"
    }
    AppSection(title = "Sync", subtitle = "Periodic, smart, and full", modifier = modifier) {
        AppActionRow {
            StatusBadge(
                periodicLabel,
                if (periodicScheduled) StatusTone.Success else if (periodicEnabled) StatusTone.Warning else StatusTone.Neutral
            )
            StatusBadge(if (backgroundReadGranted) "Background ready" else "Manual only", if (backgroundReadGranted) StatusTone.Success else StatusTone.Warning)
        }
        StatusMessageCard(
            periodicSyncStatusText(
                enabled = periodicEnabled,
                backgroundReadAvailable = backgroundReadAvailable,
                backgroundReadGranted = backgroundReadGranted,
                lastFinished = lastPeriodicSync,
                lastStatus = lastPeriodicStatus,
                lastSummary = lastPeriodicSummary
            )
        )
        PrimaryActionButton(
            if (busy) "Working..." else if (periodicEnabled) "Disable Periodic" else "Enable Periodic",
            enabled = !busy,
            onClick = onTogglePeriodic
        )
        AppActionRow {
            SecondaryActionButton(
                "Run Now",
                modifier = Modifier.weight(1f),
                enabled = backgroundReadAvailable && !busy,
                onClick = onRunBackgroundNow
            )
            SecondaryActionButton(
                "Full Resync",
                modifier = Modifier.weight(1f),
                enabled = !busy,
                onClick = onFullResync
            )
        }
        if (syncProgress != null) {
            SyncProgressCard(syncProgress)
        }
        if (syncProgress?.mode == SyncMode.FULL_HISTORY && syncProgress.isCancellable) {
            SecondaryActionButton("Cancel Full Resync", onClick = onCancelFullResync)
        }
        Text(
            uiText("Full resync reads from the full historical floor to now and can be slow. Periodic sync checks Health Connect changes about hourly; Android may delay it, so it is not real-time."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (status != "Ready") {
            StatusMessageCard(status)
        }
    }
}

private fun periodicSyncStatusText(
    enabled: Boolean,
    backgroundReadAvailable: Boolean,
    backgroundReadGranted: Boolean,
    lastFinished: Instant?,
    lastStatus: String?,
    lastSummary: String?
): String {
    val enabledText = when {
        !enabled -> "disabled"
        backgroundReadGranted -> "enabled and scheduled"
        backgroundReadAvailable -> "enabled, waiting for background permission"
        else -> "enabled, but background read is unavailable"
    }
    val finishedText = lastFinished?.toString() ?: "never"
    val statusText = lastStatus ?: "no run yet"
    return "Periodic sync is $enabledText. Last run: $finishedText ($statusText). " +
        lastSummary.orEmpty()
}
