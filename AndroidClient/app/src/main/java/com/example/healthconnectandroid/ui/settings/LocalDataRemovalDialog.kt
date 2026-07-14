package com.example.healthconnectandroid.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.local.LocalDataRetention
import com.example.healthconnectandroid.hc.local.LocalDataRemovalPhase
import com.example.healthconnectandroid.hc.local.LocalDataRemovalProgress
import com.example.healthconnectandroid.ui.i18n.uiText

@Composable
fun LocalDataRemovalDialog(
    busy: Boolean,
    progress: LocalDataRemovalProgress?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDataRetention) -> Unit
) {
    var selectedRetention by remember { mutableStateOf(LocalDataRetention.ONE_MONTH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(uiText("Release local data")) },
        text = {
            if (busy) {
                RemovalProgress(progress)
            } else {
                RetentionOptions(
                    selectedRetention = selectedRetention,
                    onSelect = { selectedRetention = it }
                )
            }
        },
        confirmButton = {
            if (!busy) {
                HoldToRemoveButton(
                    enabled = true,
                    onConfirmed = { onConfirm(selectedRetention) }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(uiText(if (busy) "Continue in background" else "Cancel"))
            }
        }
    )
}

@Composable
private fun RetentionOptions(
    selectedRetention: LocalDataRetention,
    onSelect: (LocalDataRetention) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            uiText("Choose how much health data this app should keep. Health Connect source data and medicine history are not deleted."),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LocalDataRetention.values().forEach { retention ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(retention) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = retention == selectedRetention,
                    onClick = { onSelect(retention) }
                )
                Text(uiText(retention.label), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun RemovalProgress(progress: LocalDataRemovalProgress?) {
    val phase = progress?.phase ?: LocalDataRemovalPhase.PREPARING
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            uiText(phase.label),
            style = MaterialTheme.typography.bodyLarge
        )
        val fraction = progress?.fraction
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${progress.completedItems} / ${progress.totalItems}",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            uiText("Deletion continues after this window is closed."),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val LocalDataRemovalPhase.label: String
    get() = when (this) {
        LocalDataRemovalPhase.PREPARING -> "Preparing local data"
        LocalDataRemovalPhase.HEALTH_RECORDS -> "Removing health records"
        LocalDataRemovalPhase.LEGACY_HEART_RATE -> "Removing older heart-rate data"
        LocalDataRemovalPhase.RELATED_DATA -> "Cleaning related data"
        LocalDataRemovalPhase.RECLAIMING_SPACE -> "Finalizing local storage"
        LocalDataRemovalPhase.COMPLETE -> "Local data removal complete"
    }

@Composable
private fun HoldToRemoveButton(
    enabled: Boolean,
    onConfirmed: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (!pressed) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(HOLD_DURATION_MILLIS.toInt(), easing = LinearEasing))
        if (pressed) {
            pressed = false
            onConfirmed()
        }
    }

    Surface(
        modifier = Modifier
            .semantics {
                role = Role.Button
                contentDescription = "Hold for three seconds to remove local data"
            }
            .focusable(enabled)
            .onPreviewKeyEvent { event ->
                if (!enabled || (event.key != Key.Enter && event.key != Key.DirectionCenter)) {
                    return@onPreviewKeyEvent false
                }
                pressed = event.type == KeyEventType.KeyDown
                true
            }
            .pointerInput(enabled, onConfirmed) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    pressed = false
                }
            },
        shape = MaterialTheme.shapes.small,
        color = if (enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(uiText(if (pressed) "Keep holding..." else "Hold 3 seconds to remove"))
            if (pressed) {
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.onError,
                    trackColor = MaterialTheme.colorScheme.onError.copy(alpha = 0.24f)
                )
            }
        }
    }
}

private const val HOLD_DURATION_MILLIS = 3_000L
