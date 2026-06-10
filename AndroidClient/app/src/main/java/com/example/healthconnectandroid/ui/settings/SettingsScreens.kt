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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.UserProfile
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
    userProfile: UserProfile,
    periodicEnabled: Boolean,
    debugEnabled: Boolean,
    status: String,
    onOpenProfile: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onToggleDebug: () -> Unit,
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(uiText("Settings"), modifier = Modifier.rowFadeIn(0), style = MaterialTheme.typography.headlineSmall)
        SettingsNavCard("Profile", profileBrief(userProfile), onOpenProfile, Modifier.rowFadeIn(1))
        SettingsNavCard("Preferences", "Units, week, timezone", onOpenPreferences, Modifier.rowFadeIn(2))
        SettingsNavCard("Permissions", "Health Connect access", onOpenPermissions, Modifier.rowFadeIn(3))
        SettingsNavCard("Sync", if (periodicEnabled) "Periodic on" else "Periodic off", onOpenSync, Modifier.rowFadeIn(4))
        SettingsNavCard("Upload", "Server upload", onOpenUpload, Modifier.rowFadeIn(5))
        SettingsNavCard("Data Settings", "Exports and local data", onOpenDataSettings, Modifier.rowFadeIn(6))
        SettingsNavCard("Appearance", "Mode and palette", onOpenAppearance, Modifier.rowFadeIn(7))
        AppSection(
            title = "Debug Mode",
            subtitle = if (debugEnabled) "Debug tools and local upload are visible" else "Debug tools hidden",
            modifier = Modifier.rowFadeIn(8)
        ) {
            AppActionRow {
                StatusBadge(if (debugEnabled) "On" else "Off", if (debugEnabled) StatusTone.Warning else StatusTone.Neutral)
                SecondaryActionButton(
                    if (debugEnabled) "Turn Off" else "Turn On",
                    onClick = onToggleDebug
                )
            }
            Text(
                uiText("Turn on only when testing local server upload or legacy diagnostics."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (debugEnabled) {
            SettingsNavCard("Debug", "Legacy tools", onOpenDebug, Modifier.rowFadeIn(9))
        }
        StatusMessageCard(status, modifier = Modifier.rowFadeIn(if (debugEnabled) 10 else 9))
    }
}

@Composable
private fun SettingsNavCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppSection(
        title = title,
        subtitle = subtitle,
        modifier = modifier.clickable(onClick = onClick)
    ) {}
}

@Composable
fun SettingsPermissionsScreen(
    declaredReadHr: Boolean,
    platformGranted: Boolean,
    hcGranted: Boolean,
    backgroundReadStatus: String,
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
        AppSection(title = "Permissions", subtitle = "Local read access", modifier = Modifier.rowFadeIn(0)) {
            StatusBadge(if (hcGranted) "Ready" else "Needs access", if (hcGranted) StatusTone.Success else StatusTone.Warning)
            Text(uiText(dataPermissionSummary), color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryActionButton(if (hcGranted) "Review Access" else "Grant Access", onClick = onRequestDataPermissions)
            StatusBadge(
                if (platformGranted) "Platform HR ready" else "Platform HR missing",
                if (platformGranted) StatusTone.Success else StatusTone.Warning
            )
            SecondaryActionButton(
                label = if (platformGranted) "Platform HR Ready" else "Request Platform HR",
                enabled = declaredReadHr && !platformGranted,
                onClick = onRequestPlatform
            )
            StatusBadge(backgroundReadStatus, if ("granted" in backgroundReadStatus) StatusTone.Success else StatusTone.Warning)
            SecondaryActionButton("Grant Background", enabled = backgroundReadAvailable, onClick = onRequestBackgroundRead)
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
        AppSection(title = "Sync", subtitle = "Periodic, smart, and full", modifier = Modifier.rowFadeIn(0)) {
            AppActionRow {
                StatusBadge(if (periodicEnabled) "Scheduled" else "Off", if (periodicEnabled) StatusTone.Success else StatusTone.Neutral)
                StatusBadge(if (backgroundReadGranted) "Background ready" else "Manual only", if (backgroundReadGranted) StatusTone.Success else StatusTone.Warning)
            }
            Text(
                uiText(periodicSyncStatusText(periodicEnabled, lastPeriodicSync, lastPeriodicStatus, lastPeriodicSummary)),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryActionButton(
                if (busy) "Working..." else if (periodicEnabled) "Disable Periodic" else "Enable Periodic",
                enabled = !busy,
                onClick = onTogglePeriodic
            )
            SecondaryActionButton("Run Now", enabled = backgroundReadAvailable && !busy, onClick = onRunBackgroundNow)
            SecondaryActionButton("Full Resync", enabled = !busy, onClick = onFullResync)
            if (syncProgress != null) {
                SyncProgressCard(syncProgress)
            }
            if (syncProgress?.mode == SyncMode.FULL_HISTORY && syncProgress.isCancellable) {
                SecondaryActionButton("Cancel Full Resync", onClick = onCancelFullResync)
            }
            Text(
                uiText("Full resync reads from the full historical floor to now and can be slow. Periodic sync uses WorkManager smart sync; Android may delay it, so it is not real-time."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusMessageCard(status)
        }
    }
}

private fun profileBrief(profile: UserProfile): String =
    profile.age?.let { "Age $it from DOB" } ?: "DOB not set"

private fun periodicSyncStatusText(
    enabled: Boolean,
    lastFinished: Instant?,
    lastStatus: String?,
    lastSummary: String?
): String {
    val enabledText = if (enabled) "enabled" else "disabled"
    val finishedText = lastFinished?.toString() ?: "never"
    val statusText = lastStatus ?: "no run yet"
    return "Periodic sync is $enabledText. Last run: $finishedText ($statusText). " +
        lastSummary.orEmpty()
}
