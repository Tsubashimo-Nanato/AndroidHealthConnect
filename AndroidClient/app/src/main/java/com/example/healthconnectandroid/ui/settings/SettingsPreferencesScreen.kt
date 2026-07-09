package com.example.healthconnectandroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.AppThemeMode
import com.example.healthconnectandroid.AppThemePalette
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.TimeZonePreferenceMode
import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.UserProfile
import com.example.healthconnectandroid.UserPreferences
import com.example.healthconnectandroid.WeekStartPreference
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.ZoneId

@Composable
fun SettingsPreferencesScreen(
    userProfile: UserProfile,
    userPreferences: UserPreferences,
    themeMode: AppThemeMode,
    themePalette: AppThemePalette,
    onUserProfileSave: (UserProfile) -> Unit,
    onUserPreferencesSave: (UserPreferences) -> Unit,
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
        PreferenceDisplaySection(
            userPreferences = userPreferences,
            onUserPreferencesSave = onUserPreferencesSave,
            modifier = Modifier.rowFadeIn(0)
        )
        AppearanceSettingsSection(
            themeMode = themeMode,
            themePalette = themePalette,
            onThemeModeChange = onThemeModeChange,
            onThemePaletteChange = onThemePaletteChange,
            modifier = Modifier.rowFadeIn(1)
        )
        ProfileSettingsSection(
            userProfile = userProfile,
            onUserProfileSave = onUserProfileSave,
            modifier = Modifier.rowFadeIn(2)
        )
    }
}

@Composable
fun PreferenceDisplaySection(
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

    AppSection(title = "Preferences", subtitle = "Display only", modifier = modifier) {
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
