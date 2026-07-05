package com.example.healthconnectandroid.ui.medicine

import android.Manifest
import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.medicine.MedicineDoseLog
import com.example.healthconnectandroid.medicine.MedicineDoseStatus
import com.example.healthconnectandroid.medicine.MedicineItem
import com.example.healthconnectandroid.medicine.MedicineLogSource
import com.example.healthconnectandroid.medicine.MedicineSlot
import com.example.healthconnectandroid.medicine.MedicineSnapshot
import com.example.healthconnectandroid.ui.AppActionRow
import com.example.healthconnectandroid.ui.AppSection
import com.example.healthconnectandroid.ui.EmptyState
import com.example.healthconnectandroid.ui.PrimaryActionButton
import com.example.healthconnectandroid.ui.SecondaryActionButton
import com.example.healthconnectandroid.ui.SegmentedSwitch
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MedicineScreen(
    snapshot: MedicineSnapshot,
    zoneId: ZoneId,
    defaultSlot: MedicineSlot,
    notificationPermissionGranted: Boolean,
    status: String,
    onLogDose: (MedicineSlot, MedicineDoseStatus, Set<Long>, String?) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSlot by remember(defaultSlot) { mutableStateOf(defaultSlot) }
    var selectedMedicineIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var extraMedicineName by remember { mutableStateOf("") }
    val scheduledForSlot = snapshot.schedules[selectedSlot].orEmpty()

    LaunchedEffect(snapshot, selectedSlot) {
        selectedMedicineIds = if (selectedSlot == MedicineSlot.AS_NEEDED) {
            emptySet()
        } else {
            scheduledForSlot.map { it.localId }.toSet()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(uiText("Medicine"), modifier = Modifier.rowFadeIn(0), style = MaterialTheme.typography.headlineSmall)

        AppSection(
            title = "Today",
            subtitle = "Medicine schedule and quick log",
            modifier = Modifier.rowFadeIn(1)
        ) {
            AppActionRow {
                StatusBadge("${snapshot.medicines.count { it.active }} active", StatusTone.Info)
                StatusBadge("${snapshot.todayLogs.size} logs today", StatusTone.Neutral)
            }
            if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= 33) {
                StatusMessageCard(
                    "Notification permission is needed for medicine checks.",
                    tone = StatusTone.Warning
                )
                SecondaryActionButton("Enable notifications", onClick = onRequestNotificationPermission)
            }
        }

        AppSection(
            title = "Quick Log",
            subtitle = "Records the actual time you answer",
            modifier = Modifier.rowFadeIn(2)
        ) {
            SegmentedSwitch(
                options = MedicineSlot.entries,
                selected = selectedSlot,
                label = { it.label },
                onSelected = { selectedSlot = it }
            )

            if (scheduledForSlot.isEmpty()) {
                EmptyState(
                    title = "No scheduled medicine",
                    message = "Add a medicine in Settings, or enter a one-off medicine below."
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    scheduledForSlot.forEach { medicine ->
                        SelectableMedicineRow(
                            medicine = medicine,
                            selected = medicine.localId in selectedMedicineIds,
                            onToggle = {
                                selectedMedicineIds = if (medicine.localId in selectedMedicineIds) {
                                    selectedMedicineIds - medicine.localId
                                } else {
                                    selectedMedicineIds + medicine.localId
                                }
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = extraMedicineName,
                onValueChange = { extraMedicineName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(uiText("One-off medicine")) },
                singleLine = true
            )

            AppActionRow {
                PrimaryActionButton(
                    label = "Taken",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onLogDose(selectedSlot, MedicineDoseStatus.TAKEN, selectedMedicineIds, extraMedicineName)
                        extraMedicineName = ""
                    }
                )
                SecondaryActionButton(
                    label = "Missed",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onLogDose(selectedSlot, MedicineDoseStatus.MISSED, selectedMedicineIds, extraMedicineName)
                        extraMedicineName = ""
                    }
                )
            }
            SecondaryActionButton(
                label = "Skipped",
                onClick = {
                    onLogDose(selectedSlot, MedicineDoseStatus.SKIPPED, selectedMedicineIds, extraMedicineName)
                    extraMedicineName = ""
                }
            )
            StatusMessageCard(status)
        }

        AppSection(
            title = "Current Medicines",
            subtitle = "Active schedule",
            modifier = Modifier.rowFadeIn(3)
        ) {
            val active = snapshot.medicines.filter { it.active }
            if (active.isEmpty()) {
                EmptyState("No medicine yet", "Add medicines from Settings.")
            } else {
                val daily = active.filter { medicine -> medicine.slots.any { it != MedicineSlot.AS_NEEDED } }
                val asNeeded = active.filter { medicine -> MedicineSlot.AS_NEEDED in medicine.slots }
                MedicineGroup(
                    title = "Daily medicines",
                    medicines = daily,
                    emptyMessage = "No daily medicines"
                )
                MedicineGroup(
                    title = "As needed medicines",
                    medicines = asNeeded,
                    emptyMessage = "No as needed medicines"
                )
            }
        }

        DoseLogSection("Today Log", snapshot.todayLogs, zoneId, Modifier.rowFadeIn(4))
        DoseLogSection("Yesterday Log", snapshot.yesterdayLogs, zoneId, Modifier.rowFadeIn(5))
    }
}

@Composable
private fun SelectableMedicineRow(
    medicine: MedicineItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    var expanded by remember(medicine.localId) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(role = Role.Checkbox, onClick = onToggle),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = uiText(medicine.name),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            medicine.doseText?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = uiText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            medicine.summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = uiText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            medicine.details?.takeIf { it.isNotBlank() && expanded }?.let {
                Text(
                    text = uiText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!medicine.details.isNullOrBlank()) {
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = uiText(if (expanded) "Hide medicine details" else "Show medicine details")
                )
            }
        }
    }
}

@Composable
private fun MedicineGroup(
    title: String,
    medicines: List<MedicineItem>,
    emptyMessage: String
) {
    var expanded by remember(title) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                uiText("$title (${medicines.size})"),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = uiText(if (expanded) "Collapse $title" else "Expand $title")
            )
        }

        if (expanded) {
            if (medicines.isEmpty()) {
                Text(
                    uiText(emptyMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                medicines.forEach { medicine ->
                    MedicineInfoRow(medicine)
                }
            }
        }
    }
}

@Composable
private fun MedicineInfoRow(medicine: MedicineItem) {
    var expanded by remember(medicine.localId) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(role = Role.Button) { expanded = !expanded },
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = uiText(medicine.name),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = uiText(if (expanded) "Hide medicine details" else "Show medicine details")
            )
        }
        medicine.doseText?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = uiText(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = uiText(medicine.slots.joinToString { it.label }.ifBlank { "No schedule" }),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        medicine.summary?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = uiText(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        medicine.details?.takeIf { it.isNotBlank() && expanded }?.let {
            Text(
                text = uiText(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DoseLogSection(
    title: String,
    logs: List<MedicineDoseLog>,
    zoneId: ZoneId,
    modifier: Modifier = Modifier
) {
    AppSection(title = title, subtitle = "Recorded locally", modifier = modifier) {
        if (logs.isEmpty()) {
            EmptyState("No logs", "No medicine log entries for this day.")
        } else {
            logs.forEach { log ->
                val time = Instant.ofEpochMilli(log.recordedEpochMillis)
                    .atZone(zoneId)
                    .format(TimeFormatter)
                val source = if (log.source == MedicineLogSource.REMINDER) "reminder" else "manual"
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(log.status.label, log.status.tone())
                    Text(
                        text = uiText("$time ${log.slot.label}: ${log.medicineName} ($source)"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private fun MedicineDoseStatus.tone(): StatusTone =
    when (this) {
        MedicineDoseStatus.TAKEN -> StatusTone.Success
        MedicineDoseStatus.MISSED -> StatusTone.Warning
        MedicineDoseStatus.SKIPPED -> StatusTone.Neutral
    }

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
