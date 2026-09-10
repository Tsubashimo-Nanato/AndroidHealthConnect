package com.example.healthconnectandroid.ui.medicine

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.medicine.MedicineDoseEvent
import com.example.healthconnectandroid.medicine.MedicineDoseLog
import com.example.healthconnectandroid.medicine.MedicineDoseStatus
import com.example.healthconnectandroid.medicine.MedicineLogSource
import com.example.healthconnectandroid.medicine.displayLabel
import com.example.healthconnectandroid.medicine.displayMedicineName
import com.example.healthconnectandroid.medicine.toDoseEvents
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyState
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun DoseLogSection(
    title: String,
    logs: List<MedicineDoseLog>,
    zoneId: ZoneId,
    onDeleteDoseLogs: (Set<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val events = remember(logs) { logs.toDoseEvents() }
    AppSection(title = title, subtitle = medicineUiText("Recorded locally"), modifier = modifier) {
        if (events.isEmpty()) {
            EmptyState(medicineUiText("No logs"), medicineUiText("No medicine log entries for this day."))
        } else {
            events.forEach { event ->
                MedicineDoseEventRow(
                    event = event,
                    zoneId = zoneId,
                    onDeleteDoseLogs = onDeleteDoseLogs
                )
            }
        }
    }
}

@Composable
private fun MedicineDoseEventRow(
    event: MedicineDoseEvent,
    zoneId: ZoneId,
    onDeleteDoseLogs: (Set<Long>) -> Unit
) {
    val language = LocalAppLanguage.current
    var expanded by remember(event.logIds) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(event.logIds) { mutableStateOf(false) }
    val time = Instant.ofEpochMilli(event.recordedEpochMillis)
        .atZone(zoneId)
        .format(TimeFormatter)
    val source = sourceLabel(event.source, language)
    val medicineCount = event.medicineNames.size
    val countText = if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
        "$medicineCount 个药物，$source"
    } else {
        "$medicineCount medicines, $source"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(180)),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusBadge(event.status.displayLabel(language), event.status.tone())
            Column(Modifier.weight(1f)) {
                Text(
                    text = uiText("$time ${event.slot.displayLabel(language)}"),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = medicineUiText(if (expanded) "Hide medicine details" else "Show medicine details")
                )
            }
        }

        if (expanded) {
            event.medicineNames.forEach { name ->
                Text(
                    text = displayMedicineName(name, language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SecondaryActionButton(
                label = medicineUiText("Delete log"),
                onClick = { showDeleteConfirmation = true }
            )
        }
    }

    if (showDeleteConfirmation) {
        val deleteMessage = if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
            "将删除这次用药中的 $medicineCount 条本地记录。此操作无法撤销。"
        } else {
            "Delete $medicineCount local medicine records from this dose? This cannot be undone."
        }
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(medicineUiText("Delete this dose log?")) },
            text = { Text(deleteMessage) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDeleteDoseLogs(event.logIds.toSet())
                    }
                ) {
                    Text(medicineUiText("Delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(medicineUiText("Cancel"))
                }
            }
        )
    }
}

private fun MedicineDoseStatus.tone(): StatusTone =
    when (this) {
        MedicineDoseStatus.TAKEN -> StatusTone.Success
        MedicineDoseStatus.MISSED -> StatusTone.Warning
        MedicineDoseStatus.SKIPPED -> StatusTone.Neutral
    }

private fun sourceLabel(source: MedicineLogSource, language: AppLanguagePreference): String =
    if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
        if (source == MedicineLogSource.REMINDER) "提醒" else "手动"
    } else {
        if (source == MedicineLogSource.REMINDER) "reminder" else "manual"
    }

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
