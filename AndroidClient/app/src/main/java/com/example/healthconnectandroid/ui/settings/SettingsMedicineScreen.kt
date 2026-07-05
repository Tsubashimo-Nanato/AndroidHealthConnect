package com.example.healthconnectandroid.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.medicine.MedicineItem
import com.example.healthconnectandroid.medicine.MedicineReminderTime
import com.example.healthconnectandroid.medicine.MedicineSlot
import com.example.healthconnectandroid.medicine.MedicineSnapshot
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyState
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText

@Composable
fun SettingsMedicineScreen(
    snapshot: MedicineSnapshot,
    status: String,
    onAddMedicine: (String, Set<MedicineSlot>) -> Unit,
    onArchiveMedicine: (Long) -> Unit,
    onSaveReminder: (MedicineSlot, String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember { mutableStateOf("") }
    var selectedSlots by remember { mutableStateOf(setOf(MedicineSlot.MORNING)) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(uiText("Medicine"), modifier = Modifier.rowFadeIn(0), style = MaterialTheme.typography.headlineSmall)

        AppSection(
            title = "Add Medicine",
            subtitle = "Choose when this medicine is usually taken",
            modifier = Modifier.rowFadeIn(1)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(uiText("Medicine name")) },
                singleLine = true
            )
            MedicineSlot.entries.forEach { slot ->
                SlotCheckRow(
                    slot = slot,
                    checked = slot in selectedSlots,
                    onToggle = {
                        selectedSlots = if (slot in selectedSlots) {
                            selectedSlots - slot
                        } else {
                            selectedSlots + slot
                        }
                    }
                )
            }
            PrimaryActionButton(
                label = "Add medicine",
                onClick = {
                    onAddMedicine(name, selectedSlots)
                    name = ""
                    selectedSlots = setOf(MedicineSlot.MORNING)
                }
            )
        }

        AppSection(
            title = "Reminder Times",
            subtitle = "The notification asks whether you already took it",
            modifier = Modifier.rowFadeIn(2)
        ) {
            MedicineSlot.scheduledSlots.forEach { slot ->
                ReminderTimeRow(
                    slot = slot,
                    reminder = snapshot.reminderTimes[slot],
                    onSaveReminder = onSaveReminder
                )
            }
        }

        AppSection(
            title = "Current Medicines",
            subtitle = "Stored locally",
            modifier = Modifier.rowFadeIn(3)
        ) {
            if (snapshot.medicines.isEmpty()) {
                EmptyState("No medicine yet", "Add a medicine above.")
            } else {
                snapshot.medicines.forEach { medicine ->
                    MedicineSettingsRow(
                        medicine = medicine,
                        onArchiveMedicine = onArchiveMedicine
                    )
                }
            }
            StatusMessageCard(status)
        }
    }
}

@Composable
private fun SlotCheckRow(
    slot: MedicineSlot,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onToggle),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(uiText(slot.label), style = MaterialTheme.typography.bodyMedium)
            if (!slot.supportsReminder) {
                Text(
                    uiText("Manual log only"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ReminderTimeRow(
    slot: MedicineSlot,
    reminder: MedicineReminderTime?,
    onSaveReminder: (MedicineSlot, String, Boolean) -> Unit
) {
    val fallback = reminder ?: MedicineReminderTime(slot, hour = 8, minute = 0, enabled = true)
    var timeText by remember(slot, fallback.label) { mutableStateOf(fallback.label) }
    var enabled by remember(slot, fallback.enabled) { mutableStateOf(fallback.enabled) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(uiText(slot.label), style = MaterialTheme.typography.titleSmall)
                Text(
                    uiText(if (enabled) "Reminder enabled" else "Reminder off"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = { enabled = it })
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
                label = "Save",
                modifier = Modifier.weight(1f),
                onClick = { onSaveReminder(slot, timeText, enabled) }
            )
        }
    }
}

@Composable
private fun MedicineSettingsRow(
    medicine: MedicineItem,
    onArchiveMedicine: (Long) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                text = uiText(medicine.name),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = uiText(medicine.slots.joinToString { it.label }.ifBlank { "No schedule" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            medicine.doseText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = uiText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            medicine.summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = uiText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (medicine.active) {
            SecondaryActionButton(
                label = "Archive",
                modifier = Modifier.weight(1f),
                onClick = { onArchiveMedicine(medicine.localId) }
            )
        } else {
            StatusBadge("Archived", StatusTone.Neutral)
        }
    }
}
