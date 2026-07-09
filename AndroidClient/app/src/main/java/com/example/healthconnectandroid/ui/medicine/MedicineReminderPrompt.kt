package com.example.healthconnectandroid.ui.medicine

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.healthconnectandroid.medicine.MedicineItem
import com.example.healthconnectandroid.medicine.MedicineReminderPromptPolicy
import com.example.healthconnectandroid.medicine.MedicineSlot
import com.example.healthconnectandroid.medicine.displayLabel
import com.example.healthconnectandroid.medicine.displayText
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.i18n.uiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MedicineReminderPrompt(
    slot: MedicineSlot,
    loadMedicines: suspend () -> List<MedicineItem>,
    onTaken: (Set<Long>) -> Unit,
    onCancel: () -> Unit
) {
    var medicines by remember(slot) { mutableStateOf<List<MedicineItem>?>(null) }
    var selectedIds by remember(slot) { mutableStateOf<Set<Long>>(emptySet()) }
    var expanded by remember(slot) { mutableStateOf(false) }
    var loadError by remember(slot) { mutableStateOf<String?>(null) }

    LaunchedEffect(slot) {
        runCatching { withContext(Dispatchers.IO) { loadMedicines() } }
            .onSuccess { loaded ->
                medicines = loaded
                selectedIds = MedicineReminderPromptPolicy.defaultSelectedIds(loaded)
            }
            .onFailure { throwable ->
                medicines = emptyList()
                loadError = "Medicine check failed: ${throwable.message ?: throwable.javaClass.simpleName}"
            }
    }

    MedicineReminderPromptDialog(
        slot = slot,
        medicines = medicines,
        selectedIds = selectedIds,
        expanded = expanded,
        loadError = loadError,
        onExpandedChange = { expanded = it },
        onSelectedIdsChange = { selectedIds = it },
        onTaken = { onTaken(selectedIds) },
        onCancel = onCancel
    )
}

@Composable
private fun MedicineReminderPromptDialog(
    slot: MedicineSlot,
    medicines: List<MedicineItem>?,
    selectedIds: Set<Long>,
    expanded: Boolean,
    loadError: String?,
    onExpandedChange: (Boolean) -> Unit,
    onSelectedIdsChange: (Set<Long>) -> Unit,
    onTaken: () -> Unit,
    onCancel: () -> Unit
) {
    val loaded = medicines.orEmpty()
    val canTake = medicines != null && MedicineReminderPromptPolicy.canConfirmTaken(selectedIds)

    AlertDialog(
        onDismissRequest = {},
        title = { Text(medicineUiText("Medicine check")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MedicineReminderSummary(
                    slot = slot,
                    loadedCount = loaded.size,
                    selectedCount = selectedIds.size
                )
                loadError?.let {
                    Text(
                        translateMedicineUiText(it, LocalAppLanguage.current),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (medicines == null) {
                    Text(
                        medicineUiText("Loading scheduled medicines"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (loaded.isEmpty()) {
                    Text(
                        medicineUiText("No scheduled medicine for this reminder."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    MedicineReviewToggle(
                        expanded = expanded,
                        onExpandedChange = onExpandedChange
                    )
                    if (expanded) {
                        MedicineReminderSelectionList(
                            medicines = loaded,
                            selectedIds = selectedIds,
                            onSelectedIdsChange = onSelectedIdsChange
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onTaken, enabled = canTake) {
                Text(medicineUiText("Taken"))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(medicineUiText("Cancel"))
            }
        }
    )
}

@Composable
private fun MedicineReminderSummary(
    slot: MedicineSlot,
    loadedCount: Int,
    selectedCount: Int
) {
    val language = LocalAppLanguage.current
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            slot.displayLabel(language),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            medicineUiText("Selected medicines: $selectedCount/$loadedCount"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MedicineReviewToggle(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onExpandedChange(!expanded) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            medicineUiText("Review medicines"),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = uiText(if (expanded) "Collapse" else "Expand")
        )
    }
}

@Composable
private fun MedicineReminderSelectionList(
    medicines: List<MedicineItem>,
    selectedIds: Set<Long>,
    onSelectedIdsChange: (Set<Long>) -> Unit
) {
    val language = LocalAppLanguage.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        medicines.forEach { medicine ->
            val selected = medicine.localId in selectedIds
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Checkbox) {
                        onSelectedIdsChange(toggleId(selectedIds, medicine.localId))
                    },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = {
                        onSelectedIdsChange(toggleId(selectedIds, medicine.localId))
                    }
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = medicine.displayText(language).name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun toggleId(selectedIds: Set<Long>, localId: Long): Set<Long> =
    if (localId in selectedIds) selectedIds - localId else selectedIds + localId
