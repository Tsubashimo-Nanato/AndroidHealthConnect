package com.example.healthconnectandroid.ui.data

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.hc.HealthDataCategory
import com.example.healthconnectandroid.hc.HealthDataImplementationStatus
import com.example.healthconnectandroid.hc.HealthDataPermissionStatus
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.InspectorCategorySummary
import com.example.healthconnectandroid.hc.ReadableHealthRecord
import com.example.healthconnectandroid.hc.query.HealthDataCatalogQueryService
import com.example.healthconnectandroid.ui.StatusBadge
import com.example.healthconnectandroid.ui.StatusMessageCard
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.animation.rowFadeIn
import com.example.healthconnectandroid.ui.format.DisplayPreferences
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import com.example.healthconnectandroid.ui.i18n.uiText
import java.time.Instant

@Composable
fun DataCatalogScreen(
    catalogQueries: HealthDataCatalogQueryService,
    grantedPermissions: Set<String>,
    displayPreferences: DisplayPreferences,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var reloadVersion by remember { mutableIntStateOf(0) }
    var categories by remember { mutableStateOf<List<InspectorCategorySummary>>(emptyList()) }
    var status by remember { mutableStateOf("Loading local data...") }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(grantedPermissions, reloadVersion, displayPreferences.zoneId) {
        loading = true
        status = "Loading local data..."
        runCatching { catalogQueries.inspectorCategories(grantedPermissions, displayPreferences.zoneId) }
            .onSuccess {
                categories = it.sortedWith(
                    compareBy<InspectorCategorySummary> { featuredSortWeight(it.descriptor.key) }
                        .thenBy { dataCardSortWeight(it) }
                        .thenBy { categoryLabel(it.descriptor.category) }
                        .thenBy { it.descriptor.displayName }
                )
                status = "${MetricDisplayFormatter.formatCount(it.size)} types, local cache, " +
                    "${MetricDisplayFormatter.formatRecordCount(it.sumOf { summary -> summary.recordCount })}"
            }
            .onFailure { status = "Load failed: ${it.message}" }
        loading = false
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DataStatusStrip(
            categories = categories,
            status = status,
            loading = loading,
            displayPreferences = displayPreferences,
            onRefresh = { reloadVersion++ }
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loading && categories.isEmpty()) {
                itemsIndexed(List(4) { it }, key = { _, row -> "loading-$row" }) { index, _ ->
                    Row(
                        modifier = Modifier.rowFadeIn(index),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LoadingMetricPlaceholder(Modifier.weight(1f))
                        LoadingMetricPlaceholder(Modifier.weight(1f))
                    }
                }
            } else {
                itemsIndexed(
                    categories.chunked(2),
                    key = { _, row -> row.joinToString(":") { it.descriptor.key } }
                ) { index, row ->
                    Row(
                        modifier = Modifier.rowFadeIn(index, enabled = categories.size <= 40 && !loading),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { summary ->
                            val clickable = DataCardInteractionPolicy.isClickable(summary)
                            HealthMetricCard(
                                summary = summary,
                                displayPreferences = displayPreferences,
                                modifier = Modifier.weight(1f),
                                clickable = clickable,
                                onClick = { if (clickable) onOpenDetail(summary.descriptor.key) }
                            )
                        }
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DataStatusStrip(
    categories: List<InspectorCategorySummary>,
    status: String,
    loading: Boolean,
    displayPreferences: DisplayPreferences,
    onRefresh: () -> Unit
) {
    val latestSync: Instant? = categories.mapNotNull { it.lastSynced }.maxOrNull()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (loading && categories.isEmpty()) {
                        StatusBadge("Loading", StatusTone.Info)
                        StatusBadge("Local cache", StatusTone.Neutral)
                    } else {
                        StatusBadge("${MetricDisplayFormatter.formatCount(categories.size)} types", StatusTone.Info)
                        StatusBadge("Local cache", StatusTone.Info)
                        StatusBadge(
                            MetricDisplayFormatter.formatRecordCount(categories.sumOf { it.recordCount }),
                            StatusTone.Neutral
                        )
                    }
                }
                Text(
                    if (loading && categories.isEmpty()) {
                        uiText("Loading local cache")
                    } else {
                        uiText("Last sync ${MetricDisplayFormatter.formatShortInstant(latestSync, displayPreferences.zoneId)}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (loading && categories.isEmpty()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (status.startsWith("Load failed")) {
                    StatusMessageCard(status, tone = StatusTone.Error)
                }
            }
            IconButton(enabled = !loading, onClick = onRefresh) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = uiText("Refresh local data"))
                }
            }
        }
    }
}

@Composable
private fun LoadingMetricPlaceholder(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(148.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceholderBar(widthFraction = 0.58f)
                PlaceholderBar(widthFraction = 0.42f)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceholderBar(widthFraction = 0.70f)
                PlaceholderBar(widthFraction = 0.50f)
            }
        }
    }
}

@Composable
private fun PlaceholderBar(widthFraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(10.dp)
            .background(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                shape = RoundedCornerShape(999.dp)
            )
    )
}

@Composable
private fun HealthMetricCard(
    summary: InspectorCategorySummary,
    displayPreferences: DisplayPreferences,
    modifier: Modifier = Modifier,
    clickable: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val lowPriority = DataCardInteractionPolicy.isLowPriority(summary)
    val scale by animateFloatAsState(
        targetValue = if (clickable && pressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "metric-card-press"
    )
    val alpha by animateFloatAsState(
        targetValue = if (lowPriority) 0.58f else 1f,
        animationSpec = tween(180),
        label = "metric-card-alpha"
    )

    Card(
        modifier = modifier
            .height(148.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(alpha)
            .clickable(
                enabled = clickable,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (lowPriority) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    uiText(summary.descriptor.displayName),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    uiText(categoryLabel(summary.descriptor.category)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                uiText(cardPrimaryText(summary, displayPreferences.unitSystem)),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                uiText(cardSecondaryText(summary)),
                style = MaterialTheme.typography.bodySmall,
                color = cardSecondaryColor(summary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun cardPrimaryText(
    summary: InspectorCategorySummary,
    unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
): String {
    DataCardInteractionPolicy.disabledReason(summary)?.let { return it }
    if (summary.recordCount == 0) {
        return "No data"
    }

    summary.todayTotal?.let { total ->
        return when (summary.descriptor.key) {
            HealthDataTypeKeys.STEPS -> "Today ${MetricDisplayFormatter.formatCount(total.total.toInt())}"
            HealthDataTypeKeys.DISTANCE -> "Today ${MetricDisplayFormatter.formatDistance(total.total, unitSystem)}"
            HealthDataTypeKeys.ACTIVE_CALORIES,
            HealthDataTypeKeys.TOTAL_CALORIES -> "Today ${MetricDisplayFormatter.formatEnergy(total.total)}"
            else -> "Today ${MetricDisplayFormatter.formatCount(total.total.toInt())}"
        }
    }

    if (summary.recentRecordCount == 0 && summary.latestReadable == null) {
        return "No recent data"
    }

    val latest = summary.latestReadable
    return when (summary.descriptor.key) {
        HealthDataTypeKeys.HEART_RATE ->
            latest?.value?.let { "Latest ${MetricDisplayFormatter.formatBpm(it)}" }
                ?: fallbackDataText(summary)
        HealthDataTypeKeys.SLEEP_SESSION ->
            latest?.durationText?.let { "Last $it" } ?: fallbackDataText(summary)
        HealthDataTypeKeys.WEIGHT ->
            latest?.value?.let { MetricDisplayFormatter.formatWeight(it, unitSystem) } ?: latestText(latest, unitSystem)
        HealthDataTypeKeys.BODY_FAT ->
            latest?.value?.let { MetricDisplayFormatter.formatPercent(it) } ?: latestText(latest)
        HealthDataTypeKeys.OXYGEN_SATURATION ->
            latest?.value?.let { "SpO2 ${MetricDisplayFormatter.formatPercent(it)}" } ?: latestText(latest)
        HealthDataTypeKeys.RESTING_HEART_RATE ->
            latest?.value?.let { MetricDisplayFormatter.formatBpm(it) } ?: latestText(latest)
        else -> latestText(latest, unitSystem)
    }
}

private fun cardSecondaryText(summary: InspectorCategorySummary): String {
    DataCardInteractionPolicy.disabledReason(summary)?.let {
        return if (it == "Needs access") "Grant in Settings" else it
    }
    if (summary.recordCount == 0) return "No data"
    if (summary.recentRecordCount == 0) return "No recent data"
    if (summary.descriptor.key == HealthDataTypeKeys.SLEEP_SESSION) {
        return if (summary.recordCount == 1) {
            "1 session"
        } else {
            "${MetricDisplayFormatter.formatCount(summary.recordCount)} sessions"
        }
    }
    return MetricDisplayFormatter.formatRecordCount(summary.recordCount)
}

private fun latestText(
    latest: ReadableHealthRecord?,
    unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
): String =
    latest?.value?.let { value ->
        MetricDisplayFormatter.formatMeasurement(value, latest.unit, unitSystem)
    }
        ?: latest?.durationText?.let { "Last $it" }
        ?: latest?.metric?.takeIf { it.isNotBlank() }
        ?: "Has data"

private fun fallbackDataText(summary: InspectorCategorySummary): String =
    if (summary.recentRecordCount == 0) "No recent data" else "Has data"

@Composable
private fun cardSecondaryColor(summary: InspectorCategorySummary) =
    when {
        summary.permissionStatus == HealthDataPermissionStatus.MISSING -> MaterialTheme.colorScheme.tertiary
        summary.permissionStatus == HealthDataPermissionStatus.UNSUPPORTED -> MaterialTheme.colorScheme.error
        summary.recordCount > 0 -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

private fun dataCardSortWeight(summary: InspectorCategorySummary): Int =
    when {
        summary.descriptor.implementationStatus == HealthDataImplementationStatus.IMPLEMENTED &&
            summary.permissionStatus == HealthDataPermissionStatus.GRANTED &&
            summary.recordCount > 0 -> 0
        summary.permissionStatus == HealthDataPermissionStatus.MISSING &&
            summary.descriptor.implementationStatus == HealthDataImplementationStatus.IMPLEMENTED -> 1
        summary.descriptor.implementationStatus == HealthDataImplementationStatus.IMPLEMENTED &&
            summary.permissionStatus == HealthDataPermissionStatus.GRANTED -> 2
        summary.permissionStatus == HealthDataPermissionStatus.UNSUPPORTED -> 3
        else -> 4
    }

private fun featuredSortWeight(key: String): Int =
    when (key) {
        HealthDataTypeKeys.HEART_RATE -> 0
        HealthDataTypeKeys.SLEEP_SESSION -> 1
        else -> 2
    }

private fun categoryLabel(category: HealthDataCategory): String =
    when (category) {
        HealthDataCategory.VITALS -> "Vitals"
        HealthDataCategory.BODY -> "Body"
        HealthDataCategory.ACTIVITY -> "Activity"
        HealthDataCategory.SLEEP -> "Sleep"
        HealthDataCategory.EXERCISE -> "Exercise"
        HealthDataCategory.NUTRITION -> "Nutrition"
    }
