package com.example.healthconnectandroid.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.AgeCalculator
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.AppThemeMode
import com.example.healthconnectandroid.AppThemePalette
import com.example.healthconnectandroid.ProfileSex
import com.example.healthconnectandroid.TimeZonePreferenceMode
import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.UserProfile
import com.example.healthconnectandroid.UserPreferences
import com.example.healthconnectandroid.WeekStartPreference
import com.example.healthconnectandroid.debug.DeviceSmokeDiagnostics
import com.example.healthconnectandroid.debug.DetailQueryDiagnostic
import com.example.healthconnectandroid.debug.MatrixGestureDiagnostic
import com.example.healthconnectandroid.debug.SyncDiagnostic
import com.example.healthconnectandroid.hc.sync.SyncMode
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.hc.upload.UploadEndpointPolicy
import com.example.healthconnectandroid.hc.upload.UploadEndpointValidation
import com.example.healthconnectandroid.hc.upload.UploadPendingCounts
import com.example.healthconnectandroid.hc.upload.UploadProgress
import com.example.healthconnectandroid.hc.upload.UploadResultSeverity
import com.example.healthconnectandroid.hc.upload.UploadServerMode
import com.example.healthconnectandroid.hc.upload.UploadSettings
import com.example.healthconnectandroid.hc.upload.UploadStatus
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.QueryCard
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.SegmentedSwitch
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.SyncProgressCard
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId

@Composable
fun SettingsScreen(
    userProfile: UserProfile,
    periodicEnabled: Boolean,
    status: String,
    onOpenProfile: () -> Unit,
    onOpenPreferences: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenUpload: () -> Unit,
    onOpenDataSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
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
        SettingsNavCard("Debug", "Legacy tools", onOpenDebug, Modifier.rowFadeIn(8))
        StatusMessageCard(status, modifier = Modifier.rowFadeIn(9))
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
fun SettingsPreferencesScreen(
    userPreferences: UserPreferences,
    onUserPreferencesSave: (UserPreferences) -> Unit,
    modifier: Modifier = Modifier
) {
    var weekStart by rememberSaveable(userPreferences) { mutableStateOf(userPreferences.weekStart) }
    var unitSystem by rememberSaveable(userPreferences) { mutableStateOf(userPreferences.unitSystem) }
    var timeZoneMode by rememberSaveable(userPreferences) { mutableStateOf(userPreferences.timeZoneMode) }
    var language by rememberSaveable(userPreferences) { mutableStateOf(userPreferences.language) }
    var customTimeZoneText by rememberSaveable(userPreferences) {
        mutableStateOf(userPreferences.customTimeZoneId.orEmpty())
    }
    val customTimeZoneValid = customTimeZoneText.isBlank() || runCatching {
        ZoneId.of(customTimeZoneText.trim())
    }.isSuccess
    val canSave = timeZoneMode == TimeZonePreferenceMode.SYSTEM || customTimeZoneValid

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppSection(title = "Preferences", subtitle = "Display only", modifier = Modifier.rowFadeIn(0)) {
            Text(uiText("Language"), style = MaterialTheme.typography.titleSmall)
            AppActionRow {
                AppLanguagePreference.values().forEach { option ->
                    val label = when (option) {
                        AppLanguagePreference.ENGLISH -> "English"
                        AppLanguagePreference.CHINESE_SIMPLIFIED -> "Simplified Chinese"
                    }
                    if (option == language) {
                        PrimaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = label,
                            onClick = { language = option }
                        )
                    } else {
                        SecondaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = label,
                            onClick = { language = option }
                        )
                    }
                }
            }

            Text(uiText("Week starts on"), style = MaterialTheme.typography.titleSmall)
            AppActionRow {
                WeekStartPreference.values().forEach { option ->
                    if (option == weekStart) {
                        PrimaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = option.label,
                            onClick = { weekStart = option }
                        )
                    } else {
                        SecondaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = option.label,
                            onClick = { weekStart = option }
                        )
                    }
                }
            }

            Text(uiText("Units"), style = MaterialTheme.typography.titleSmall)
            AppActionRow {
                UnitSystemPreference.values().forEach { option ->
                    if (option == unitSystem) {
                        PrimaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = option.label,
                            onClick = { unitSystem = option }
                        )
                    } else {
                        SecondaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = option.label,
                            onClick = { unitSystem = option }
                        )
                    }
                }
            }

            Text(uiText("Timezone"), style = MaterialTheme.typography.titleSmall)
            AppActionRow {
                TimeZonePreferenceMode.values().forEach { option ->
                    if (option == timeZoneMode) {
                        PrimaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = if (option == TimeZonePreferenceMode.SYSTEM) "System" else "Custom",
                            onClick = { timeZoneMode = option }
                        )
                    } else {
                        SecondaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = if (option == TimeZonePreferenceMode.SYSTEM) "System" else "Custom",
                            onClick = { timeZoneMode = option }
                        )
                    }
                }
            }
            if (timeZoneMode == TimeZonePreferenceMode.CUSTOM) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = customTimeZoneText,
                    onValueChange = { customTimeZoneText = it.trim().take(64) },
                    label = { Text(uiText("Timezone ID")) },
                    placeholder = { Text("Asia/Tokyo") },
                    supportingText = {
                        Text(if (customTimeZoneValid) uiText("Current: ${userPreferences.zoneId}") else uiText("Use a valid IANA timezone."))
                    },
                    isError = !customTimeZoneValid,
                    singleLine = true
                )
            }
            PrimaryActionButton(
                label = "Save",
                enabled = canSave,
                onClick = {
                    onUserPreferencesSave(
                        UserPreferences(
                            weekStart = weekStart,
                            unitSystem = unitSystem,
                            timeZoneMode = timeZoneMode,
                            language = language,
                            customTimeZoneId = customTimeZoneText
                                .trim()
                                .takeIf { timeZoneMode == TimeZonePreferenceMode.CUSTOM && it.isNotBlank() }
                        )
                    )
                }
            )
            Text(
                uiText("Preferences change display grouping and units only. Stored data and CSV export remain canonical."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsProfileScreen(
    userProfile: UserProfile,
    onUserProfileSave: (UserProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var sex by rememberSaveable(userProfile) { mutableStateOf(userProfile.sex) }
    var dobText by rememberSaveable(userProfile) { mutableStateOf(userProfile.dateOfBirthIso.orEmpty()) }
    var weightText by rememberSaveable(userProfile) {
        mutableStateOf(userProfile.weightKg?.let(::formatProfileWeight).orEmpty())
    }
    val parsedDob = remember(dobText) {
        dobText.trim()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    val derivedAge = remember(parsedDob) { AgeCalculator.ageOn(parsedDob) }
    val dobInvalid = dobText.isNotBlank() && derivedAge == null
    val weightInvalid = weightText.isNotBlank() && weightText.toDoubleOrNull()?.let { it in 20.0..350.0 } != true
    val canSave = !dobInvalid && !weightInvalid

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppSection(title = "Profile", subtitle = "Saved locally", modifier = Modifier.rowFadeIn(0)) {
            if (editing) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(uiText("Sex"), style = MaterialTheme.typography.titleSmall)
                    ProfileSex.values().toList().chunked(2).forEach { rowOptions ->
                        AppActionRow {
                            rowOptions.forEach { option ->
                                if (option == sex) {
                                    PrimaryActionButton(
                                        modifier = Modifier.weight(1f),
                                        label = profileSexShortLabel(option),
                                        onClick = { sex = option }
                                    )
                                } else {
                                    SecondaryActionButton(
                                        modifier = Modifier.weight(1f),
                                        label = profileSexShortLabel(option),
                                        onClick = { sex = option }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = dobText,
                        onValueChange = { dobText = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) },
                        label = { Text(uiText("Date of birth")) },
                        placeholder = { Text("YYYY-MM-DD") },
                        supportingText = {
                            Text(
                                uiText(when {
                                    dobInvalid -> "Use YYYY-MM-DD, not a future date."
                                    derivedAge != null -> "Age $derivedAge"
                                    else -> "Optional"
                                })
                            )
                        },
                        isError = dobInvalid,
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = weightText,
                        onValueChange = { raw -> weightText = raw.filter { it.isDigit() || it == '.' }.take(6) },
                        label = { Text(uiText("Weight (kg)")) },
                        supportingText = { Text(uiText(if (weightInvalid) "Enter 20-350 kg." else "Optional")) },
                        isError = weightInvalid,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    AppActionRow {
                        PrimaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = "Save",
                            enabled = canSave,
                            onClick = {
                                onUserProfileSave(
                                    UserProfile(
                                        sex = sex,
                                        dateOfBirthIso = parsedDob?.toString(),
                                        weightKg = weightText.toDoubleOrNull()?.takeIf { it in 20.0..350.0 }
                                    )
                                )
                                editing = false
                            }
                        )
                        SecondaryActionButton(
                            modifier = Modifier.weight(1f),
                            label = "Cancel",
                            onClick = {
                                sex = userProfile.sex
                                dobText = userProfile.dateOfBirthIso.orEmpty()
                                weightText = userProfile.weightKg?.let(::formatProfileWeight).orEmpty()
                                editing = false
                            }
                        )
                    }
                }
            } else {
                ProfileValueRow("Sex", userProfile.sex.label)
                ProfileValueRow("Date of birth", userProfile.dateOfBirthIso ?: "Not set")
                ProfileValueRow("Age", userProfile.age?.let { "$it" } ?: "Not set")
                ProfileValueRow("Weight", userProfile.weightKg?.let { "${formatProfileWeight(it)} kg" } ?: "Not set")
                PrimaryActionButton("Edit", onClick = { editing = true })
            }
            Text(
                uiText("DOB-derived age affects HR reference bands. Other values are saved for later."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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

@Composable
fun SettingsUploadScreen(
    settings: UploadSettings,
    uploadStatus: UploadStatus,
    pendingCounts: UploadPendingCounts,
    busy: Boolean,
    progress: UploadProgress?,
    onSaveSettings: (UploadSettings) -> Unit,
    onTestConnection: (UploadSettings) -> Unit,
    onUploadNow: (UploadSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var serverMode by rememberSaveable(settings) { mutableStateOf(settings.serverMode) }
    var localUrl by rememberSaveable(settings) { mutableStateOf(settings.localBaseUrl) }
    var apiKey by rememberSaveable(settings) { mutableStateOf(settings.apiKey) }
    val editedSettings = remember(serverMode, localUrl, apiKey, settings.deviceId) {
        UploadSettings(
            serverMode = serverMode,
            localBaseUrl = localUrl,
            apiKey = apiKey,
            deviceId = settings.deviceId
        )
    }
    val validation = remember(editedSettings) { UploadEndpointPolicy.validate(editedSettings) }
    val validationMessage = when (validation) {
        is UploadEndpointValidation.Valid -> "Endpoint ready"
        is UploadEndpointValidation.Invalid -> validation.reason
    }
    val canRun = validation is UploadEndpointValidation.Valid && !busy

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppSection(title = "Upload", subtitle = "Server destination", modifier = Modifier.rowFadeIn(0)) {
            SegmentedSwitch(
                options = UploadServerMode.values().toList(),
                selected = serverMode,
                label = { it.label },
                onSelected = { serverMode = it }
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = UploadEndpointPolicy.PRODUCTION_BASE_URL,
                onValueChange = {},
                label = { Text(uiText("Production URL")) },
                enabled = false,
                singleLine = true
            )
            if (serverMode == UploadServerMode.LOCAL_DEBUG) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = localUrl,
                    onValueChange = { localUrl = it.trim().take(160) },
                    label = { Text(uiText("Local URL")) },
                    placeholder = { Text(UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL) },
                    singleLine = true
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = apiKey,
                onValueChange = { apiKey = it.take(256) },
                label = { Text(uiText("API key")) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            AppActionRow {
                StatusBadge(serverMode.label, StatusTone.Info)
                StatusBadge(validationMessage, if (validation is UploadEndpointValidation.Valid) StatusTone.Success else StatusTone.Warning)
            }
            Text(
                uiText("Device ${settings.deviceId.take(8)}"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryActionButton("Save", enabled = !busy, onClick = { onSaveSettings(editedSettings) })
        }

        AppSection(title = "Upload Status", subtitle = "Pending local rows", modifier = Modifier.rowFadeIn(1)) {
            AppActionRow {
                StatusBadge("${pendingCounts.records} records", StatusTone.Neutral)
                StatusBadge("${pendingCounts.values} values", StatusTone.Neutral)
                StatusBadge("${pendingCounts.aggregates} summaries", StatusTone.Neutral)
            }
            Text(
                uiText(uploadLastTimeText(uploadStatus)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusMessageCard(
                message = uploadStatus.lastResult,
                tone = uploadStatus.severity.toStatusTone()
            )
            uploadStatus.connectionResult?.let {
                StatusMessageCard(
                    message = it,
                    tone = uploadStatus.severity.toStatusTone()
                )
            }
            progress?.let {
                Text(
                    uiText("${it.phase} ${it.currentType ?: ""}".trim()),
                    style = MaterialTheme.typography.labelMedium
                )
                if (it.determinate) {
                    LinearProgressIndicator(
                        progress = { it.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    uiText("${it.uploadedItems}/${it.totalPendingItems} rows"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AppActionRow {
                SecondaryActionButton(
                    modifier = Modifier.weight(1f),
                    label = if (busy) "Testing..." else "Test",
                    enabled = canRun,
                    onClick = { onTestConnection(editedSettings) }
                )
                PrimaryActionButton(
                    modifier = Modifier.weight(1f),
                    label = if (busy) "Uploading..." else "Upload",
                    enabled = canRun,
                    onClick = { onUploadNow(editedSettings) }
                )
            }
        }
    }
}

@Composable
fun SettingsDataScreen(
    status: String,
    busy: Boolean,
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
        AppSection(title = "Data Settings", subtitle = "Exports and local data", modifier = Modifier.rowFadeIn(0)) {
            PrimaryActionButton(if (busy) "Preparing..." else "Export CSV", enabled = !busy, onClick = onExportAllCsv)
            SecondaryActionButton("Export ZIP", enabled = !busy, onClick = onExportZip)
            SecondaryActionButton("Export HR CSV", enabled = !busy, onClick = onExportHrCsv)
            SecondaryActionButton("Remove Local Data", enabled = !busy, onClick = onRequestClear)
            Text(
                uiText("Remove Local Data clears this app's cached records, summaries, and sync history. Health Connect data is not deleted."),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusMessageCard(status)
        }
    }
}

@Composable
fun SettingsAppearanceScreen(
    themeMode: AppThemeMode,
    themePalette: AppThemePalette,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemePaletteChange: (AppThemePalette) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppSection(title = "Appearance", subtitle = "Saved locally", modifier = Modifier.rowFadeIn(0)) {
            Text(uiText("Mode"), style = MaterialTheme.typography.titleSmall)
            SegmentedSwitch(
                options = AppThemeMode.values().toList(),
                selected = themeMode,
                label = { it.label },
                onSelected = onThemeModeChange
            )

            Text(uiText("Palette"), style = MaterialTheme.typography.titleSmall)
            AppThemePalette.values().toList().chunked(2).forEach { rowOptions ->
                AppActionRow {
                    rowOptions.forEach { palette ->
                        val selected = palette == themePalette
                        if (selected) {
                            PrimaryActionButton(
                                modifier = Modifier.weight(1f),
                                label = palette.label,
                                onClick = { onThemePaletteChange(palette) }
                            )
                        } else {
                            SecondaryActionButton(
                                modifier = Modifier.weight(1f),
                                label = palette.label,
                                onClick = { onThemePaletteChange(palette) }
                            )
                        }
                    }
                    if (rowOptions.size == 1) {
                        Text("", modifier = Modifier.weight(1f))
                    }
                }
            }
            Text(
                uiText(themePalette.description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

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
    var showSmokeDiagnostics by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppSection(
            title = "Smoke-Test Diagnostics",
            subtitle = "Counts and ranges only",
            modifier = Modifier.rowFadeIn(0)
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
            title = "Legacy Heart-Rate Tools",
            subtitle = "Kept for compatibility with the original demo",
            modifier = Modifier.rowFadeIn(1)
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
            modifier = Modifier.rowFadeIn(3)
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
            modifier = Modifier.rowFadeIn(4)
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

private fun UploadResultSeverity.toStatusTone(): StatusTone =
    when (this) {
        UploadResultSeverity.IDLE -> StatusTone.Neutral
        UploadResultSeverity.SUCCESS -> StatusTone.Success
        UploadResultSeverity.WARNING -> StatusTone.Warning
        UploadResultSeverity.ERROR -> StatusTone.Error
    }

private fun uploadLastTimeText(status: UploadStatus): String =
    status.lastUploadEpochMillis
        ?.let { "Last upload: ${Instant.ofEpochMilli(it)}" }
        ?: "Last upload: never"

@Composable
private fun ProfileValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            uiText(label),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            uiText(value),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun profileSexShortLabel(sex: ProfileSex): String =
    when (sex) {
        ProfileSex.NOT_SET -> "Not set"
        ProfileSex.FEMALE -> "Female"
        ProfileSex.MALE -> "Male"
        ProfileSex.OTHER -> "Other"
    }

private fun profileBrief(profile: UserProfile): String =
    profile.age?.let { "Age $it from DOB" } ?: "DOB not set"

private fun formatProfileWeight(weightKg: Double): String =
    if (weightKg % 1.0 == 0.0) weightKg.toInt().toString() else "%.1f".format(weightKg)

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
