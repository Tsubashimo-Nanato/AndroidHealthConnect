package com.example.healthconnectandroid.ui.medicine

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.medicine.MedicineDisplayText
import com.example.healthconnectandroid.medicine.MedicineDoseStatus
import com.example.healthconnectandroid.medicine.MedicineItem
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
import com.example.healthconnectandroid.ui.statusToneForMessage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MedicineScreen(
    snapshot: MedicineSnapshot,
    zoneId: ZoneId,
    weekStart: DayOfWeek,
    defaultSlot: MedicineSlot,
    notificationPermissionGranted: Boolean,
    status: String,
    onLogDose: (MedicineSlot, MedicineDoseStatus, Set<Long>, String?) -> Unit,
    onDeleteDoseLogs: (Set<Long>) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val language = LocalAppLanguage.current
    var selectedSlot by remember(defaultSlot) { mutableStateOf(defaultSlot) }
    var selectedMedicineIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var extraMedicineName by remember { mutableStateOf("") }
    var selectionExpanded by remember(selectedSlot) { mutableStateOf(false) }
    val scheduledForSlot = snapshot.schedules[selectedSlot].orEmpty()
    val canLog = selectedMedicineIds.isNotEmpty() || extraMedicineName.isNotBlank()

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
        Text(medicineUiText("Medicine"), modifier = Modifier.rowFadeIn(0), style = MaterialTheme.typography.headlineSmall)

        AppSection(
            title = medicineUiText("Today"),
            subtitle = medicineUiText("Medicine schedule and quick log"),
            modifier = Modifier.rowFadeIn(1)
        ) {
            AppActionRow {
                StatusBadge("${snapshot.medicines.count { it.active }} active", StatusTone.Info)
                StatusBadge("${snapshot.todayLogs.size} logs today", StatusTone.Neutral)
            }
            if (!notificationPermissionGranted && Build.VERSION.SDK_INT >= 33) {
                StatusMessageCard(
                    medicineUiText("Notification permission is needed for medicine checks."),
                    tone = StatusTone.Warning
                )
                SecondaryActionButton(medicineUiText("Enable notifications"), onClick = onRequestNotificationPermission)
            }
        }

        AppSection(
            title = medicineUiText("Quick Log"),
            subtitle = medicineUiText("Records the actual time you answer"),
            modifier = Modifier.rowFadeIn(2)
        ) {
            MedicineSlotSelector(
                selected = selectedSlot,
                onSelected = { selectedSlot = it }
            )

            if (scheduledForSlot.isEmpty()) {
                EmptyState(
                    title = medicineUiText("No scheduled medicine"),
                    message = medicineUiText("Add a medicine in Settings, or enter a one-off medicine below.")
                )
            } else {
                Text(
                    quickLogSelectionText(
                        slot = selectedSlot,
                        selectedCount = selectedMedicineIds.size,
                        totalCount = scheduledForSlot.size,
                        language = language
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecondaryActionButton(
                    label = medicineUiText(if (selectionExpanded) "Hide selected medicines" else "Adjust selected medicines"),
                    onClick = { selectionExpanded = !selectionExpanded }
                )
                if (selectionExpanded) {
                    MedicineSelectionList(
                        medicines = scheduledForSlot,
                        selectedMedicineIds = selectedMedicineIds,
                        onToggle = { medicine ->
                            selectedMedicineIds = if (medicine.localId in selectedMedicineIds) {
                                selectedMedicineIds - medicine.localId
                            } else {
                                selectedMedicineIds + medicine.localId
                            }
                        }
                    )
                }
            }

            OutlinedTextField(
                value = extraMedicineName,
                onValueChange = { extraMedicineName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(medicineUiText("One-off medicine")) },
                singleLine = true
            )

            AppActionRow {
                PrimaryActionButton(
                    label = medicineUiText("Taken"),
                    modifier = Modifier.weight(1f),
                    enabled = canLog,
                    onClick = {
                        onLogDose(selectedSlot, MedicineDoseStatus.TAKEN, selectedMedicineIds, extraMedicineName)
                        extraMedicineName = ""
                    }
                )
                SecondaryActionButton(
                    label = medicineUiText("Missed"),
                    modifier = Modifier.weight(1f),
                    enabled = canLog,
                    onClick = {
                        onLogDose(selectedSlot, MedicineDoseStatus.MISSED, selectedMedicineIds, extraMedicineName)
                        extraMedicineName = ""
                    }
                )
            }
            SecondaryActionButton(
                label = medicineUiText("Skipped"),
                enabled = canLog,
                onClick = {
                    onLogDose(selectedSlot, MedicineDoseStatus.SKIPPED, selectedMedicineIds, extraMedicineName)
                    extraMedicineName = ""
                }
            )
            StatusMessageCard(translateMedicineUiText(status, language), tone = statusToneForMessage(status))
        }

        AppSection(
            title = medicineUiText("Current Medicines"),
            subtitle = medicineUiText("Active schedule"),
            modifier = Modifier.rowFadeIn(3)
        ) {
            val active = snapshot.medicines.filter { it.active }
            if (active.isEmpty()) {
                EmptyState(medicineUiText("No medicine yet"), medicineUiText("Add medicines from Settings."))
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

        MedicineCalendarSection(
            daySummaries = snapshot.recentDaySummaries,
            today = LocalDate.now(zoneId),
            weekStart = weekStart,
            modifier = Modifier.rowFadeIn(4)
        )
        DoseLogSection(medicineUiText("Today Log"), snapshot.todayLogs, zoneId, onDeleteDoseLogs, Modifier.rowFadeIn(5))
        DoseLogSection(medicineUiText("Yesterday Log"), snapshot.yesterdayLogs, zoneId, onDeleteDoseLogs, Modifier.rowFadeIn(6))
    }
}

@Composable
private fun MedicineSelectionList(
    medicines: List<MedicineItem>,
    selectedMedicineIds: Set<Long>,
    onToggle: (MedicineItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        medicines.forEach { medicine ->
            SelectableMedicineRow(
                medicine = medicine,
                selected = medicine.localId in selectedMedicineIds,
                onToggle = { onToggle(medicine) }
            )
        }
    }
}

private fun quickLogSelectionText(
    slot: MedicineSlot,
    selectedCount: Int,
    totalCount: Int,
    language: AppLanguagePreference
): String {
    if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
        return if (slot == MedicineSlot.AS_NEEDED) {
            "按需药默认不勾选，需要时再展开选择。"
        } else {
            "默认已选择 $selectedCount/$totalCount 个${slot.displayLabel(language)}药物。"
        }
    }
    return if (slot == MedicineSlot.AS_NEEDED) {
        "As-needed medicines are unchecked until selected."
    } else {
        "Default: $selectedCount/$totalCount ${slot.displayLabel(language)} medicines selected."
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectableMedicineRow(
    medicine: MedicineItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    var expanded by remember(medicine.localId) { mutableStateOf(false) }
    val language = LocalAppLanguage.current
    val display = medicine.displayText(language)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                role = Role.Checkbox,
                onClick = {
                    if (expanded) {
                        expanded = false
                    } else {
                        onToggle()
                    }
                },
                onLongClick = { expanded = true }
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = display.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (expanded) {
                MedicineExpandedDetails(display = display, medicine = medicine)
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
    val displayTitle = medicineUiText(title)

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
                "$displayTitle (${medicines.size})",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = medicineUiText(if (expanded) "Collapse" else "Expand")
            )
        }

        if (expanded) {
            if (medicines.isEmpty()) {
                Text(
                    medicineUiText(emptyMessage),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MedicineInfoRow(medicine: MedicineItem) {
    var expanded by remember(medicine.localId) { mutableStateOf(false) }
    val language = LocalAppLanguage.current
    val display = medicine.displayText(language)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .combinedClickable(
                role = Role.Button,
                onClick = { if (expanded) expanded = false },
                onLongClick = { expanded = true }
            ),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = display.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (expanded) {
            MedicineExpandedDetails(display = display, medicine = medicine)
        }
    }
}

@Composable
private fun MedicineExpandedDetails(
    display: MedicineDisplayText,
    medicine: MedicineItem
) {
    val language = LocalAppLanguage.current
    val slotText = medicine.slots.joinToString { it.displayLabel(language) }.ifBlank { "No schedule" }

    Text(
        text = uiText(slotText),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    display.doseText?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    display.summary?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    display.details?.takeIf { it.isNotBlank() }?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
