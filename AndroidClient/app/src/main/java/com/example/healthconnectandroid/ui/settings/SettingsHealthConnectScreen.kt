package com.example.healthconnectandroid.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.hc.upload.UploadPendingCounts
import com.example.healthconnectandroid.hc.upload.UploadProgress
import com.example.healthconnectandroid.hc.upload.UploadSettings
import com.example.healthconnectandroid.hc.upload.UploadStatus
import com.example.healthconnectandroid.hc.upload.UploadTimeRange
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant

@Composable
fun SettingsHealthConnectScreen(
    profileOwnsHealthConnect: Boolean,
    healthConnectOwnerName: String?,
    declaredReadHr: Boolean,
    platformGranted: Boolean,
    hcGranted: Boolean,
    backgroundReadStatus: String,
    dataPermissionSummary: String,
    backgroundReadAvailable: Boolean,
    periodicEnabled: Boolean,
    backgroundReadGranted: Boolean,
    lastPeriodicSync: Instant?,
    lastPeriodicStatus: String?,
    lastPeriodicSummary: String?,
    syncStatus: String,
    uploadActionStatus: String,
    syncBusy: Boolean,
    syncProgress: SyncProgress?,
    uploadSettings: UploadSettings,
    uploadStatus: UploadStatus,
    uploadPendingCounts: UploadPendingCounts,
    debugEnabled: Boolean,
    uploadBusy: Boolean,
    uploadProgress: UploadProgress?,
    onRequestPlatform: () -> Unit,
    onRequestDataPermissions: () -> Unit,
    onRequestBackgroundRead: () -> Unit,
    openAppSettings: (Intent) -> Unit,
    packageName: String,
    onTogglePeriodic: () -> Unit,
    onFullResync: () -> Unit,
    onCancelFullResync: () -> Unit,
    onRunBackgroundNow: () -> Unit,
    onSaveUploadSettings: (UploadSettings) -> Unit,
    onTestUploadConnection: (UploadSettings) -> Unit,
    onUploadNow: (UploadSettings, UploadTimeRange) -> Unit,
    onScanPairingQr: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!profileOwnsHealthConnect) {
            AppSection(
                title = "Local profile",
                subtitle = healthConnectOwnerName?.let { "Health Connect: $it" }
                    ?: "Health Connect is not linked"
            ) {
                Text(
                    uiText(
                        "Health Connect belongs to another profile. This profile can still pair for medicine and local-record upload."
                    )
                )
            }
        } else {
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
            SyncSettingsSection(
                periodicEnabled = periodicEnabled,
                backgroundReadAvailable = backgroundReadAvailable,
                backgroundReadGranted = backgroundReadGranted,
                lastPeriodicSync = lastPeriodicSync,
                lastPeriodicStatus = lastPeriodicStatus,
                lastPeriodicSummary = lastPeriodicSummary,
                status = syncStatus,
                busy = syncBusy,
                syncProgress = syncProgress,
                onTogglePeriodic = onTogglePeriodic,
                onFullResync = onFullResync,
                onCancelFullResync = onCancelFullResync,
                onRunBackgroundNow = onRunBackgroundNow,
                modifier = Modifier.rowFadeIn(1)
            )
        }
        UploadSettingsSections(
            settings = uploadSettings,
            uploadStatus = uploadStatus,
            pendingCounts = uploadPendingCounts,
            debugEnabled = debugEnabled,
            status = uploadActionStatus,
            busy = uploadBusy,
            progress = uploadProgress,
            onSaveSettings = onSaveUploadSettings,
            onTestConnection = onTestUploadConnection,
            onUploadNow = onUploadNow,
            onScanPairingQr = onScanPairingQr,
            modifier = Modifier.rowFadeIn(if (profileOwnsHealthConnect) 2 else 1),
            statusModifier = Modifier.rowFadeIn(if (profileOwnsHealthConnect) 3 else 2)
        )
    }
}
