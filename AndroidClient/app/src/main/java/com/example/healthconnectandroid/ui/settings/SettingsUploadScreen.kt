package com.example.healthconnectandroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.upload.UploadEndpointPolicy
import com.example.healthconnectandroid.hc.upload.UploadEndpointValidation
import com.example.healthconnectandroid.hc.upload.UploadPendingCounts
import com.example.healthconnectandroid.hc.upload.UploadProgress
import com.example.healthconnectandroid.hc.upload.UploadResultSeverity
import com.example.healthconnectandroid.hc.upload.UploadServerMode
import com.example.healthconnectandroid.hc.upload.UploadSettings
import com.example.healthconnectandroid.hc.upload.UploadStatus
import com.example.healthconnectandroid.hc.upload.UploadTimeRange
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.SegmentedSwitch
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant

@Composable
fun SettingsUploadScreen(
    settings: UploadSettings,
    uploadStatus: UploadStatus,
    pendingCounts: UploadPendingCounts,
    debugEnabled: Boolean,
    status: String,
    busy: Boolean,
    progress: UploadProgress?,
    onSaveSettings: (UploadSettings) -> Unit,
    onTestConnection: (UploadSettings) -> Unit,
    onUploadNow: (UploadSettings, UploadTimeRange) -> Unit,
    onScanPairingQr: () -> Unit,
    onApplyPairingText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var serverMode by rememberSaveable(settings, debugEnabled) {
        mutableStateOf(if (debugEnabled) settings.serverMode else UploadServerMode.PRODUCTION)
    }
    var localUrl by rememberSaveable(settings) { mutableStateOf(settings.localBaseUrl) }
    var apiKey by rememberSaveable(settings) { mutableStateOf(settings.apiKey) }
    var pairingDialogOpen by rememberSaveable { mutableStateOf(false) }
    var pairingText by rememberSaveable { mutableStateOf("") }
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
    var uploadMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AppSection(title = "Upload", subtitle = "Server destination", modifier = Modifier.rowFadeIn(0)) {
            if (debugEnabled) {
                SegmentedSwitch(
                    options = UploadServerMode.values().toList(),
                    selected = serverMode,
                    label = { it.label },
                    onSelected = { serverMode = it }
                )
            } else {
                AppActionRow {
                    StatusBadge("Production", StatusTone.Info)
                }
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = UploadEndpointPolicy.PRODUCTION_BASE_URL,
                onValueChange = {},
                label = { Text(uiText("Production URL")) },
                enabled = false,
                singleLine = true
            )
            if (debugEnabled && serverMode == UploadServerMode.LOCAL_DEBUG) {
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
            AppActionRow {
                SecondaryActionButton(
                    modifier = Modifier.weight(1f),
                    label = "Scan Pairing QR",
                    enabled = !busy,
                    onClick = onScanPairingQr
                )
                SecondaryActionButton(
                    modifier = Modifier.weight(1f),
                    label = "Paste Pairing Text",
                    enabled = !busy,
                    onClick = {
                        pairingText = ""
                        pairingDialogOpen = true
                    }
                )
            }
            Text(
                uiText("Pairing accepts a QR with upload URL, API key, or both. Local/private URLs switch to Local debug automatically."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PrimaryActionButton("Save", enabled = !busy, onClick = { onSaveSettings(editedSettings) })
            StatusMessageCard(status)
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
                Box(modifier = Modifier.weight(1f)) {
                    PrimaryActionButton(
                        label = if (busy) "Uploading..." else "Upload",
                        enabled = canRun,
                        onClick = { uploadMenuExpanded = true }
                    )
                    DropdownMenu(
                        expanded = uploadMenuExpanded,
                        onDismissRequest = { uploadMenuExpanded = false }
                    ) {
                        UploadTimeRange.values().forEach { range ->
                            DropdownMenuItem(
                                text = { Text(uiText(range.label)) },
                                enabled = canRun,
                                onClick = {
                                    uploadMenuExpanded = false
                                    onUploadNow(editedSettings, range)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (pairingDialogOpen) {
        AlertDialog(
            onDismissRequest = { pairingDialogOpen = false },
            title = { Text(uiText("Paste pairing text")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        uiText("Use this if QR scanning is unavailable. Paste the QR content, upload URL, or API key."),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = pairingText,
                        onValueChange = { pairingText = it.take(1200) },
                        label = { Text(uiText("Pairing text")) },
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pairingText.isNotBlank(),
                    onClick = {
                        val text = pairingText
                        pairingDialogOpen = false
                        pairingText = ""
                        onApplyPairingText(text)
                    }
                ) {
                    Text(uiText("Apply"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pairingDialogOpen = false }) {
                    Text(uiText("Cancel"))
                }
            }
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
