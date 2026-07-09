package com.example.healthconnectandroid.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.time.Instant

@Composable
fun SettingsDataFlowScreen(
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
    settingsStatus: String,
    syncBusy: Boolean,
    syncProgress: SyncProgress?,
    uploadSettings: UploadSettings,
    uploadStatus: UploadStatus,
    uploadPendingCounts: UploadPendingCounts,
    debugEnabled: Boolean,
    uploadBusy: Boolean,
    uploadProgress: UploadProgress?,
    exportBusy: Boolean,
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
    onExportHrCsv: () -> Unit,
    onExportAllCsv: () -> Unit,
    onExportZip: () -> Unit,
    onRequestClear: () -> Unit,
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
            status = settingsStatus,
            busy = syncBusy,
            syncProgress = syncProgress,
            onTogglePeriodic = onTogglePeriodic,
            onFullResync = onFullResync,
            onCancelFullResync = onCancelFullResync,
            onRunBackgroundNow = onRunBackgroundNow,
            modifier = Modifier.rowFadeIn(1)
        )
        UploadSettingsSections(
            settings = uploadSettings,
            uploadStatus = uploadStatus,
            pendingCounts = uploadPendingCounts,
            debugEnabled = debugEnabled,
            status = settingsStatus,
            busy = uploadBusy,
            progress = uploadProgress,
            onSaveSettings = onSaveUploadSettings,
            onTestConnection = onTestUploadConnection,
            onUploadNow = onUploadNow,
            onScanPairingQr = onScanPairingQr,
            destinationModifier = Modifier.rowFadeIn(2),
            statusModifier = Modifier.rowFadeIn(3)
        )
        DataManagementSection(
            status = settingsStatus,
            busy = exportBusy,
            onExportHrCsv = onExportHrCsv,
            onExportAllCsv = onExportAllCsv,
            onExportZip = onExportZip,
            onRequestClear = onRequestClear,
            modifier = Modifier.rowFadeIn(4)
        )
    }
}
