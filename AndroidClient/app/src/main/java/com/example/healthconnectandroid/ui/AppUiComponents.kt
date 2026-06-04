package com.example.healthconnectandroid.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.i18n.translateUiText
import com.example.healthconnectandroid.ui.i18n.uiText
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

private val StudioEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 22.dp, bottomEnd = 32.dp, bottomStart = 24.dp)
private val ControlShape = RoundedCornerShape(topStart = 22.dp, topEnd = 28.dp, bottomEnd = 18.dp, bottomStart = 24.dp)
private val ChipShape = RoundedCornerShape(topStart = 18.dp, topEnd = 22.dp, bottomEnd = 16.dp, bottomStart = 20.dp)

@Composable
private fun toneColors(tone: StatusTone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.Neutral -> ToneColors(
            container = scheme.surfaceVariant.copy(alpha = 0.58f),
            content = scheme.onSurfaceVariant,
            border = scheme.outline.copy(alpha = 0.24f)
        )
        StatusTone.Success -> ToneColors(
            container = scheme.secondaryContainer.copy(alpha = 0.72f),
            content = scheme.onSecondaryContainer,
            border = scheme.secondary.copy(alpha = 0.50f)
        )
        StatusTone.Warning -> ToneColors(
            container = scheme.tertiaryContainer.copy(alpha = 0.72f),
            content = scheme.onTertiaryContainer,
            border = scheme.tertiary.copy(alpha = 0.46f)
        )
        StatusTone.Info -> ToneColors(
            container = scheme.primary.copy(alpha = 0.13f),
            content = scheme.primary,
            border = scheme.primary.copy(alpha = 0.38f)
        )
        StatusTone.Error -> ToneColors(
            container = scheme.error.copy(alpha = 0.14f),
            content = scheme.error,
            border = scheme.error.copy(alpha = 0.42f)
        )
        StatusTone.Destructive -> ToneColors(
            container = scheme.error.copy(alpha = 0.20f),
            content = scheme.error,
            border = scheme.error.copy(alpha = 0.55f)
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
        shape = SheetShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleMedium)
                subtitle?.let {
                    Text(
                        uiText(it),
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
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 16.dp, bottomEnd = 24.dp, bottomStart = 18.dp),
        color = colors.container,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(uiText(label), style = MaterialTheme.typography.labelMedium, color = colors.content)
            Text(value, style = MaterialTheme.typography.titleLarge, color = colors.content)
            supporting?.let {
                Text(uiText(it), style = MaterialTheme.typography.bodySmall, color = colors.content.copy(alpha = 0.76f))
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
        shape = ChipShape,
        color = colors.container,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Text(
            text = uiText(text),
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
        shape = SheetShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(uiText(title), style = MaterialTheme.typography.titleMedium)
            Text(uiText(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (primaryActionLabel != null && onPrimaryAction != null) {
                Button(onClick = onPrimaryAction) { Text(uiText(primaryActionLabel)) }
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
            .animateContentSize(animationSpec = tween(260, easing = StudioEase)),
        enabled = enabled,
        onClick = onClick,
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)
    ) {
        Text(uiText(label))
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
            .animateContentSize(animationSpec = tween(260, easing = StudioEase)),
        enabled = enabled,
        onClick = onClick,
        shape = ControlShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Text(uiText(label))
    }
}

@Composable
fun StatusMessageCard(
    message: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = statusToneForMessage(message)
) {
    val colors = toneColors(tone)
    val expandable = message.length > 72 || message.contains('\n')
    var expanded by remember(message, tone) { mutableStateOf(!expandable || tone == StatusTone.Error) }
    val title = if (message.startsWith("Sync note:", ignoreCase = true)) "Sync note" else "Latest result"
    val busy = isBusyMessage(message)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(260, easing = StudioEase))
            .clickable(enabled = expandable) { expanded = !expanded },
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 26.dp, bottomEnd = 18.dp, bottomStart = 23.dp),
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
                    uiText(title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.content
                )
                if (expandable) {
                    Text(
                        uiText(if (expanded) "Collapse" else "Expand"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.content.copy(alpha = 0.72f)
                    )
                }
            }
            Text(
                uiText(message),
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
        shape = SheetShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(uiText(title), style = MaterialTheme.typography.titleSmall)
                Text(
                    uiText(message),
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
    val progressMessage = progress.message.orEmpty()
    val messageExpandable = progressMessage.length > 72 || progressMessage.contains('\n')
    var messageExpanded by remember(progressMessage, progress.phase) {
        mutableStateOf(!messageExpandable || progress.phase.name == "FAILED")
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SheetShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    uiText(progress.mode.label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    syncProgressCounterText(progress, LocalAppLanguage.current),
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
                    uiText(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                syncProgressStatsText(progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val range = syncProgressRangeText(progress.rangeStart, progress.rangeEnd)
            if (range.isNotBlank()) {
                Text(
                    uiText(range),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            progress.message?.takeIf { it.isNotBlank() }?.let {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(180, easing = StudioEase))
                        .clickable(enabled = messageExpandable) { messageExpanded = !messageExpanded },
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (messageExpandable) {
                        Text(
                            uiText(if (messageExpanded) "Collapse" else "Expand"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        uiText(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (messageExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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
        uiText(message),
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

@Composable
private fun syncProgressStatsText(progress: SyncProgress): String =
    uiText(
        "Read ${progress.read} (${formatBytesMb(progress.sourceBytesRead)}), " +
            "wrote ${formatBytesMb(progress.localBytesWritten)}, " +
            "inserted ${progress.inserted}, updated ${progress.updated}, " +
            "duplicates ${progress.duplicates}, errors ${progress.errors}"
    )

private fun formatBytesMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb < 0.01) "<0.01 MB" else String.format(java.util.Locale.US, "%.2f MB", mb)
}

private fun syncProgressCounterText(
    progress: SyncProgress,
    language: com.example.healthconnectandroid.AppLanguagePreference
): String =
    when {
        progress.totalTypes <= 0 -> translateUiText(progress.phase.label, language)
        progress.currentType != null -> translateUiText(progress.phase.label, language)
        progress.totalTypes == 1 -> translateUiText(progress.phase.label, language)
        else -> "${progress.completedTypes}/${progress.totalTypes}"
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
