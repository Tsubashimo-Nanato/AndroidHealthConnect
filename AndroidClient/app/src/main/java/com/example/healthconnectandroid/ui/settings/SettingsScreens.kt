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
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.AgeCalculator
import com.example.healthconnectandroid.AppThemeMode
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
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.QueryCard
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.SyncProgressCard
import com.example.healthconnectandroid.ui.animation.rowFadeIn
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
        Text("Settings", modifier = Modifier.rowFadeIn(0), style = MaterialTheme.typography.headlineSmall)
        SettingsNavCard("Profile", profileBrief(userProfile), onOpenProfile, Modifier.rowFadeIn(1))
        SettingsNavCard("Preferences", "Units, week, timezone", onOpenPreferences, Modifier.rowFadeIn(2))
        SettingsNavCard("Permissions", "Health Connect access", onOpenPermissions, Modifier.rowFadeIn(3))
        SettingsNavCard("Sync", if (periodicEnabled) "Periodic on" else "Periodic off", onOpenSync, Modifier.rowFadeIn(4))
        SettingsNavCard("Data Settings", "Exports and local data", onOpenDataSettings, Modifier.rowFadeIn(5))
        SettingsNavCard("Appearance", "System, light, dark", onOpenAppearance, Modifier.rowFadeIn(6))
        SettingsNavCard("Debug", "Legacy tools", onOpenDebug, Modifier.rowFadeIn(7))
        StatusMessageCard(status, modifier = Modifier.rowFadeIn(8))
    }
}

@Composable
private fun SettingsNavCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppSection(
        title = title,
        subtitle = subtitle,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text("Open", color = MaterialTheme.colorScheme.primary)
    }
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
            Text("Week starts on", style = MaterialTheme.typography.titleSmall)
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

            Text("Units", style = MaterialTheme.typography.titleSmall)
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

            Text("Timezone", style = MaterialTheme.typography.titleSmall)
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
                    label = { Text("Timezone ID") },
                    placeholder = { Text("Asia/Tokyo") },
                    supportingText = {
                        Text(if (customTimeZoneValid) "Current: ${userPreferences.zoneId}" else "Use a valid IANA timezone.")
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
                            customTimeZoneId = customTimeZoneText
                                .trim()
                                .takeIf { timeZoneMode == TimeZonePreferenceMode.CUSTOM && it.isNotBlank() }
                        )
                    )
                }
            )
            Text(
                "Preferences change display grouping and units only. Stored data and CSV export remain canonical.",
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
                    Text("Sex", style = MaterialTheme.typography.titleSmall)
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
                        label = { Text("Date of birth") },
                        placeholder = { Text("YYYY-MM-DD") },
                        supportingText = {
                            Text(
                                when {
                                    dobInvalid -> "Use YYYY-MM-DD, not a future date."
                                    derivedAge != null -> "Age $derivedAge"
                                    else -> "Optional"
                                }
                            )
                        },
                        isError = dobInvalid,
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = weightText,
                        onValueChange = { raw -> weightText = raw.filter { it.isDigit() || it == '.' }.take(6) },
                        label = { Text("Weight (kg)") },
                        supportingText = { Text(if (weightInvalid) "Enter 20-350 kg." else "Optional") },
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
                "DOB-derived age affects HR reference bands. Other values are saved for later.",
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
            Text(dataPermissionSummary, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                periodicSyncStatusText(periodicEnabled, lastPeriodicSync, lastPeriodicStatus, lastPeriodicSummary),
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
                "Full resync reads from the full historical floor to now and can be slow. Periodic sync uses WorkManager smart sync; Android may delay it, so it is not real-time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusMessageCard(status)
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
                "Remove Local Data clears this app's cached records, summaries, and sync history. Health Connect data is not deleted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusMessageCard(status)
        }
    }
}

@Composable
fun SettingsAppearanceScreen(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
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
            AppThemeMode.values().forEach { mode ->
                if (mode == themeMode) {
                    PrimaryActionButton(mode.label, onClick = { onThemeModeChange(mode) })
                } else {
                    SecondaryActionButton(mode.label, onClick = { onThemeModeChange(mode) })
                }
            }
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
                "Requires platform and Health Connect heart-rate access.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppActionRow {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = platformGranted && hrHcGranted,
                    onClick = { onSyncHours(6) }
                ) { Text("Sync 6h") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = platformGranted && hrHcGranted,
                    onClick = { onSyncHours(24) }
                ) { Text("Sync 24h") }
            }
        }

        QueryCard(onQuery = onQuery)

        AppSection(
            title = "Sleep Scoring",
            subtitle = "Placeholder visual guide",
            modifier = Modifier.rowFadeIn(3)
        ) {
            Text(
                "Sleep tags and quality colors currently use simple duration, nap, and extreme stage-churn rules only. " +
                    "They are not medical advice or a validated sleep score.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AppSection(
            title = "Cache Management",
            subtitle = "Developer-only local database action",
            modifier = Modifier.rowFadeIn(4)
        ) {
            Text(
                "Removing local data clears app rows and sync history. Health Connect data is not deleted.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onRequestClear, modifier = Modifier.fillMaxWidth()) {
                Text("Remove Local Data")
            }
        }

        StatusMessageCard(status)
    }
}

@Composable
private fun DiagnosticMatrixBlock(value: MatrixGestureDiagnostic) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Matrix Gesture", style = MaterialTheme.typography.titleSmall)
        Text(value.updatedAt?.toString() ?: "No event", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Sync", style = MaterialTheme.typography.titleSmall)
        Text(value.updatedAt?.toString() ?: "No event", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text("Detail Query", style = MaterialTheme.typography.titleSmall)
        Text(value.updatedAt?.toString() ?: "No event", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            label,
            modifier = Modifier.weight(0.42f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(0.58f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ProfileValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
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
