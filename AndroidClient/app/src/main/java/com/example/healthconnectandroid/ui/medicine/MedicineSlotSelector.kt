package com.example.healthconnectandroid.ui.medicine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.medicine.MedicineSlot
import com.example.healthconnectandroid.medicine.displayLabel
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage

@Composable
fun MedicineSlotSelector(
    selected: MedicineSlot,
    onSelected: (MedicineSlot) -> Unit,
    modifier: Modifier = Modifier,
    options: List<MedicineSlot> = MedicineSlot.entries
) {
    BoxWithConstraints(modifier = modifier) {
        val itemWidth = ((maxWidth - 16.dp) / 3).coerceAtLeast(72.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(3).forEach { rowSlots ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    rowSlots.forEach { slot ->
                        MedicineSlotChip(
                            slot = slot,
                            selected = slot == selected,
                            width = itemWidth,
                            onSelected = { onSelected(slot) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicineSlotChip(
    slot: MedicineSlot,
    selected: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onSelected: () -> Unit
) {
    val language = LocalAppLanguage.current
    Surface(
        modifier = Modifier
            .width(width)
            .heightIn(min = 46.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelected
            ),
        shape = MaterialTheme.shapes.small,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f)
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        border = BorderStroke(
            1.dp,
            if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
            }
        )
    ) {
        Text(
            text = slot.displayLabel(language),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
