package com.example.healthconnectandroid.ui.settings

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
import com.example.healthconnectandroid.AppThemeMode
import com.example.healthconnectandroid.AppThemePalette
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.SegmentedSwitch
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText

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
        DataManagementSection(
            status = status,
            busy = busy,
            onExportHrCsv = onExportHrCsv,
            onExportAllCsv = onExportAllCsv,
            onExportZip = onExportZip,
            onRequestClear = onRequestClear,
            modifier = Modifier.rowFadeIn(0)
        )
    }
}

@Composable
fun DataManagementSection(
    status: String,
    busy: Boolean,
    onExportHrCsv: () -> Unit,
    onExportAllCsv: () -> Unit,
    onExportZip: () -> Unit,
    onRequestClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppSection(title = "Data Settings", subtitle = "Exports and local data", modifier = modifier) {
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
        AppearanceSettingsSection(
            themeMode = themeMode,
            themePalette = themePalette,
            onThemeModeChange = onThemeModeChange,
            onThemePaletteChange = onThemePaletteChange,
            modifier = Modifier.rowFadeIn(0)
        )
    }
}

@Composable
fun AppearanceSettingsSection(
    themeMode: AppThemeMode,
    themePalette: AppThemePalette,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onThemePaletteChange: (AppThemePalette) -> Unit,
    modifier: Modifier = Modifier
) {
    AppSection(title = "Appearance", subtitle = "Saved locally", modifier = modifier) {
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
