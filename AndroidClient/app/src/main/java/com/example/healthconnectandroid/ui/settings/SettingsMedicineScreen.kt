package com.example.healthconnectandroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.medicine.MedicineItem
import com.example.healthconnectandroid.medicine.MedicineReminderTime
import com.example.healthconnectandroid.medicine.MedicineSlot
import com.example.healthconnectandroid.medicine.MedicineSnapshot
import com.example.healthconnectandroid.medicine.displayLabel
import com.example.healthconnectandroid.medicine.displayText
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyState
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.i18n.uiText
import com.example.healthconnectandroid.ui.medicine.medicineUiText
import com.example.healthconnectandroid.ui.medicine.translateMedicineUiText
import com.example.healthconnectandroid.ui.statusToneForMessage

@Composable
fun SettingsMedicineScreen(
    snapshot: MedicineSnapshot,
    status: String,
    overlayReminderEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    exactAlarmAccessGranted: Boolean,
    onAddMedicine: (String, Set<MedicineSlot>) -> Unit,
    onArchiveMedicine: (Long) -> Unit,
    onSaveReminder: (MedicineSlot, String, Boolean, Boolean) -> Unit,
    onOverlayReminderChange: (Boolean) -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestExactAlarmAccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    var name by remember { mutableStateOf("") }
    var selectedSlots by remember { mutableStateOf(setOf(MedicineSlot.MORNING)) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        AppSection(
            title = medicineUiText("Add Medicine"),
            subtitle = medicineUiText("Choose when this medicine is usually taken"),
            modifier = Modifier.rowFadeIn(0)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(medicineUiText("Medicine name")) },
                singleLine = true
            )
            for (index in MedicineSlot.entries.indices step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (column in 0..1) {
                        val slot = MedicineSlot.entries.getOrNull(index + column)
                        if (slot == null) {
                            Spacer(Modifier.weight(1f))
                            continue
                        }
                        SlotCheckRow(
                            slot = slot,
                            checked = slot in selectedSlots,
                            modifier = Modifier.weight(1f),
                            onToggle = {
                                selectedSlots = if (slot in selectedSlots) {
                                    selectedSlots - slot
                                } else {
                                    selectedSlots + slot
                                }
                            }
                        )
                    }
                }
            }
            PrimaryActionButton(
                label = medicineUiText("Add medicine"),
                onClick = {
                    onAddMedicine(name, selectedSlots)
                    name = ""
                    selectedSlots = setOf(MedicineSlot.MORNING)
                }
            )
        }

        AppSection(
            title = medicineUiText("Reminder Times"),
            subtitle = medicineUiText("The notification asks whether you already took it"),
            modifier = Modifier.rowFadeIn(1)
        ) {
            OverlayReminderSection(
                enabled = overlayReminderEnabled,
                permissionGranted = overlayPermissionGranted,
                onEnabledChange = onOverlayReminderChange,
                onRequestPermission = onRequestOverlayPermission
            )
            if (
                snapshot.reminderTimes.values.any(MedicineReminderTime::alarmEnabled) &&
                !exactAlarmAccessGranted
            ) {
                StatusMessageCard(
                    message = medicineUiText("Exact alarm access is needed for Alarm mode."),
                    tone = StatusTone.Warning
                )
                SecondaryActionButton(
                    label = medicineUiText("Allow exact alarms"),
                    onClick = onRequestExactAlarmAccess
                )
            }
            MedicineSlot.scheduledSlots.forEach { slot ->
                ReminderTimeRow(
                    slot = slot,
                    reminder = snapshot.reminderTimes[slot],
                    exactAlarmAccessGranted = exactAlarmAccessGranted,
                    onSaveReminder = onSaveReminder
                )
            }
        }

        AppSection(
            title = medicineUiText("Current Medicines"),
            subtitle = medicineUiText("Stored locally"),
            modifier = Modifier.rowFadeIn(2)
        ) {
            if (snapshot.medicines.isEmpty()) {
                EmptyState(medicineUiText("No medicine yet"), medicineUiText("Add a medicine above."))
            } else {
                snapshot.medicines.forEach { medicine ->
                    MedicineSettingsRow(
                        medicine = medicine,
                        onArchiveMedicine = onArchiveMedicine
                    )
                }
            }
            if (status != "Medicine ready") {
                StatusMessageCard(translateMedicineUiText(status, language), tone = statusToneForMessage(status))
            }
        }
    }
}

@Composable
private fun SlotCheckRow(
    slot: MedicineSlot,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(slot.displayLabel(language), style = MaterialTheme.typography.bodyMedium)
            if (!slot.supportsReminder) {
                Text(
                    medicineUiText("Manual log only"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OverlayReminderSection(
    enabled: Boolean,
    permissionGranted: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onRequestPermission: () -> Unit
) {
    val statusText = when {
        !enabled -> "Overlay popup off"
        permissionGranted -> "Overlay popup enabled"
        else -> "Overlay permission is needed for direct popups."
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Switch) { onEnabledChange(!enabled) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(medicineUiText("Strong popup"), style = MaterialTheme.typography.bodyMedium)
                Text(
                    medicineUiText(statusText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        if (enabled && !permissionGranted) {
            StatusMessageCard(
                message = medicineUiText("Overlay permission is needed for direct popups."),
                tone = StatusTone.Warning
            )
            SecondaryActionButton(
                label = medicineUiText("Allow overlay popup"),
                onClick = onRequestPermission
            )
        }
    }
}

@Composable
private fun ReminderTimeRow(
    slot: MedicineSlot,
    reminder: MedicineReminderTime?,
    exactAlarmAccessGranted: Boolean,
    onSaveReminder: (MedicineSlot, String, Boolean, Boolean) -> Unit
) {
    val language = LocalAppLanguage.current
    val fallback = reminder ?: MedicineReminderTime(slot, hour = 8, minute = 0, enabled = true)
    var timeText by remember(slot, fallback.label) { mutableStateOf(fallback.label) }
    var enabled by remember(slot, fallback.enabled) { mutableStateOf(fallback.enabled) }
    var alarmEnabled by remember(slot, fallback.alarmEnabled) { mutableStateOf(fallback.alarmEnabled) }
    var expanded by remember(slot) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(slot.displayLabel(language), style = MaterialTheme.typography.titleSmall)
                Text(
                    medicineUiText(if (enabled) "Reminder enabled" else "Reminder off"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    expanded = true
                }
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = medicineUiText(if (expanded) "Collapse" else "Expand")
                )
            }
        }
        if (expanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Switch) { alarmEnabled = !alarmEnabled },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(medicineUiText("Alarm mode"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        medicineUiText(
                            if (alarmEnabled && !exactAlarmAccessGranted) {
                                "Uses a standard reminder until exact alarms are allowed"
                            } else {
                                "Uses a system alarm for this slot"
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = alarmEnabled, onCheckedChange = { alarmEnabled = it })
            }
            AppActionRow {
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(uiText("HH:mm")) },
                    singleLine = true
                )
                SecondaryActionButton(
                    label = uiText("Save"),
                    modifier = Modifier.weight(1f),
                    onClick = { onSaveReminder(slot, timeText, enabled, alarmEnabled) }
                )
            }
        }
    }
}

@Composable
private fun MedicineSettingsRow(
    medicine: MedicineItem,
    onArchiveMedicine: (Long) -> Unit
) {
    val language = LocalAppLanguage.current
    val display = medicine.displayText(language)

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = display.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = uiText(medicine.slots.joinToString { it.displayLabel(language) }.ifBlank { "No schedule" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (medicine.active) {
            SecondaryActionButton(
                label = medicineUiText("Archive"),
                modifier = Modifier.weight(1f),
                onClick = { onArchiveMedicine(medicine.localId) }
            )
        } else {
            StatusBadge(medicineUiText("Archived"), StatusTone.Neutral)
        }
    }
}
