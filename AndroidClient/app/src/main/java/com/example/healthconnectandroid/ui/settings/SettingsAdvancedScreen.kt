package com.example.healthconnectandroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.debug.DeviceSmokeDiagnostics
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant

@Composable
fun SettingsAdvancedScreen(
    debugEnabled: Boolean,
    platformGranted: Boolean,
    hrHcGranted: Boolean,
    status: String,
    diagnostics: DeviceSmokeDiagnostics,
    onToggleDebug: () -> Unit,
    onSyncHours: (Long) -> Unit,
    onQuery: (Instant) -> Unit,
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
        AppSection(
            title = "Debug Mode",
            subtitle = if (debugEnabled) "Debug tools and local upload are visible" else "Debug tools hidden",
            modifier = Modifier.rowFadeIn(0)
        ) {
            AppActionRow {
                Column(Modifier.weight(1f)) {
                    Text(uiText("Advanced diagnostics"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        uiText("Turn on only when testing local server upload or debug diagnostics."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusBadge(if (debugEnabled) "On" else "Off", if (debugEnabled) StatusTone.Warning else StatusTone.Neutral)
                Switch(checked = debugEnabled, onCheckedChange = { onToggleDebug() })
            }
        }

        if (debugEnabled) {
            DebugToolsSection(
                platformGranted = platformGranted,
                hrHcGranted = hrHcGranted,
                status = status,
                diagnostics = diagnostics,
                onSyncHours = onSyncHours,
                onQuery = onQuery,
                onRequestClear = onRequestClear,
                firstRowIndex = 1
            )
        } else {
            StatusMessageCard(status, modifier = Modifier.rowFadeIn(1))
        }
    }
}
