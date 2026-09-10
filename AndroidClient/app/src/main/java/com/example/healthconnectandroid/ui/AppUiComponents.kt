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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.semantics.Role
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
    val content: Color
)

private val StudioEase = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val NoErrorText = Regex("\\bno errors?\\b")
private val ZeroErrorCount = Regex("\\berrors?\\s*[:=]?\\s*0\\b")
private val ZeroSkippedCount = Regex("\\bskipped\\s*[:=]?\\s*0\\b")

@Composable
private fun toneColors(tone: StatusTone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        StatusTone.Neutral -> ToneColors(
            container = scheme.surfaceVariant.copy(alpha = 0.58f),
            content = scheme.onSurfaceVariant
        )
        StatusTone.Success -> ToneColors(
            container = scheme.secondaryContainer.copy(alpha = 0.72f),
            content = scheme.onSecondaryContainer
        )
        StatusTone.Warning -> ToneColors(
            container = scheme.tertiaryContainer.copy(alpha = 0.72f),
            content = scheme.onTertiaryContainer
        )
        StatusTone.Info -> ToneColors(
            container = scheme.primary.copy(alpha = 0.13f),
            content = scheme.primary
        )
        StatusTone.Error -> ToneColors(
            container = scheme.error.copy(alpha = 0.14f),
            content = scheme.error
        )
        StatusTone.Destructive -> ToneColors(
            container = scheme.error.copy(alpha = 0.20f),
            content = scheme.error
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                uiText(title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
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
        shape = MaterialTheme.shapes.medium,
        color = colors.container
    ) {
        Column(
            Modifier.padding(12.dp),
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
        shape = MaterialTheme.shapes.small,
        color = colors.container
    ) {
        Text(
            text = uiText(text),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Column(
            Modifier.padding(14.dp),
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
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp
        )
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
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
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
    var expanded by remember(message, tone) { mutableStateOf(!expandable) }
    val title = if (message.startsWith("Sync note:", ignoreCase = true)) "Sync note" else "Latest result"
    val busy = isBusyMessage(message)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(260, easing = StudioEase))
            .clickable(enabled = expandable, role = Role.Button) { expanded = !expanded },
        shape = MaterialTheme.shapes.medium,
        color = colors.container
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
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Row(
            Modifier.padding(14.dp),
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
    val fraction = progress.progressFraction
    val animatedFraction by animateFloatAsState(
        targetValue = fraction ?: 0f,
        animationSpec = tween(180, easing = StudioEase),
        label = "sync-progress"
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
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
            fraction?.let {
                LinearProgressIndicator(
                    progress = { animatedFraction },
                    modifier = Modifier.fillMaxWidth()
                )
            } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                uiText(progress.currentType ?: progress.phase.label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            Text(
                uiText(progress.message.orEmpty()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
        else -> "${progress.progressPercent}%"
    }

fun statusToneForMessage(message: String): StatusTone {
    val lower = message.lowercase()
    val failureText = lower
        .replace(NoErrorText, "")
        .replace(ZeroErrorCount, "")
        .replace(ZeroSkippedCount, "")
    return when {
        "failed" in failureText || "error" in failureText -> StatusTone.Error
        "missing" in lower || "grant" in lower || "skipped" in failureText -> StatusTone.Warning
        "cleared" in lower -> StatusTone.Destructive
        "complete" in lower || "exported" in lower || "logged" in lower || "synced" in lower || "success" in lower -> StatusTone.Success
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
