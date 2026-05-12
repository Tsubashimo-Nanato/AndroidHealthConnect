package com.example.healthconnectandroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.hc.sync.SyncRangePolicy
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import java.time.Instant

enum class StatusTone {
    Neutral,
    Success,
    Warning,
    Info,
    Error,
    Destructive
}

private data class ToneColors(
    val container: Color,
    val content: Color,
    val border: Color
)

@Composable
private fun toneColors(tone: StatusTone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    return when (tone) {
        StatusTone.Neutral -> ToneColors(
            container = scheme.surfaceVariant.copy(alpha = 0.58f),
            content = scheme.onSurfaceVariant,
            border = scheme.outline.copy(alpha = 0.24f)
        )
        StatusTone.Success -> if (dark) ToneColors(
            container = Color(0xFF183327),
            content = Color(0xFFC5E7D2),
            border = Color(0xFF3F7759)
        ) else ToneColors(
            container = Color(0xFFE7F4EC),
            content = Color(0xFF24563A),
            border = Color(0xFF8BB99E)
        )
        StatusTone.Warning -> if (dark) ToneColors(
            container = Color(0xFF3A2D14),
            content = Color(0xFFF3D79A),
            border = Color(0xFF8A6A2E)
        ) else ToneColors(
            container = Color(0xFFFFF3D8),
            content = Color(0xFF73510B),
            border = Color(0xFFDAB66C)
        )
        StatusTone.Info -> if (dark) ToneColors(
            container = Color(0xFF173040),
            content = Color(0xFFBFE0EE),
            border = Color(0xFF49778C)
        ) else ToneColors(
            container = Color(0xFFE8F1F8),
            content = Color(0xFF244B66),
            border = Color(0xFF8EAFC6)
        )
        StatusTone.Error -> if (dark) ToneColors(
            container = Color(0xFF3E1F1C),
            content = Color(0xFFF2C0B9),
            border = Color(0xFF91554D)
        ) else ToneColors(
            container = Color(0xFFFCE8E6),
            content = Color(0xFF8A2D24),
            border = Color(0xFFD49A92)
        )
        StatusTone.Destructive -> if (dark) ToneColors(
            container = Color(0xFF431F19),
            content = Color(0xFFFFC9BE),
            border = Color(0xFF9B5B50)
        ) else ToneColors(
            container = Color(0xFFFFECE8),
            content = Color(0xFF8B2F21),
            border = Color(0xFFE0A194)
        )
    }
}

@Composable
fun AppSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    tone: StatusTone = StatusTone.Neutral
) {
    val colors = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = colors.container,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.content)
            Text(value, style = MaterialTheme.typography.titleLarge, color = colors.content)
            supporting?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = colors.content.copy(alpha = 0.76f))
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    tone: StatusTone,
    modifier: Modifier = Modifier
) {
    val colors = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.container,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (primaryActionLabel != null && onPrimaryAction != null) {
                Button(onClick = onPrimaryAction) { Text(primaryActionLabel) }
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(150)),
        enabled = enabled,
        onClick = onClick
    ) {
        Text(label)
    }
}

@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(150)),
        enabled = enabled,
        onClick = onClick
    ) {
        Text(label)
    }
}

@Composable
fun StatusMessageCard(
    message: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = statusToneForMessage(message)
) {
    val colors = toneColors(tone)
    val expandable = message.length > 96 || message.contains('\n')
    var expanded by remember(message) { mutableStateOf(!expandable) }
    val title = if (message.startsWith("Sync note:", ignoreCase = true)) "Sync note" else "Latest result"
    val busy = isBusyMessage(message)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(180))
            .clickable(enabled = expandable) { expanded = !expanded },
        shape = RoundedCornerShape(18.dp),
        color = colors.container,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = if (expanded) 12.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.content
                    )
                }
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.content
                )
                if (expandable) {
                    Text(
                        if (expanded) "Collapse" else "Expand",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.content.copy(alpha = 0.72f)
                    )
                }
            }
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.content,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LoadingStateCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SyncProgressCard(
    progress: SyncProgress?,
    modifier: Modifier = Modifier
) {
    if (progress == null) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    progress.mode.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    "${progress.completedTypes}/${progress.totalTypes}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            progress.progressFraction?.let { fraction ->
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            progress.currentType?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Inserted ${progress.inserted}, updated ${progress.updated}, duplicates ${progress.duplicates}, errors ${progress.errors}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val range = syncProgressRangeText(progress.rangeStart, progress.rangeEnd)
            if (range.isNotBlank()) {
                Text(
                    range,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            progress.message?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AppActionRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun EmptyStateText(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun syncProgressRangeText(start: Instant?, end: Instant?): String {
    if (start == null || end == null) return ""
    val startText = if (start == SyncRangePolicy.FULL_HISTORY_START) {
        "Full history"
    } else {
        MetricDisplayFormatter.formatShortInstant(start)
    }
    return "Range $startText-${MetricDisplayFormatter.formatShortInstant(end)}"
}

fun statusToneForMessage(message: String): StatusTone {
    val lower = message.lowercase()
    return when {
        "failed" in lower || "error" in lower -> StatusTone.Error
        "missing" in lower || "grant" in lower || "skipped" in lower -> StatusTone.Warning
        "cleared" in lower -> StatusTone.Destructive
        "exported" in lower || "synced" in lower || "success" in lower -> StatusTone.Success
        "syncing" in lower || "querying" in lower -> StatusTone.Info
        else -> StatusTone.Neutral
    }
}

fun isBusyMessage(message: String): Boolean {
    val lower = message.lowercase()
    return "syncing" in lower ||
        "loading" in lower ||
        "running" in lower ||
        "exporting" in lower ||
        "querying" in lower ||
        "choose" in lower
}
