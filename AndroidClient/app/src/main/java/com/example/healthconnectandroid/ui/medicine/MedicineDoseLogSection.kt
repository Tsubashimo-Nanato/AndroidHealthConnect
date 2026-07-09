package com.example.healthconnectandroid.ui.medicine

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            .animateContentSize()
            .clickable(role = Role.Button) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            IconButton(onClick = { expanded = !expanded }) {
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
                onClick = { onDeleteDoseLogs(event.logIds.toSet()) }
            )
        }
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
