package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.HealthDataImplementationStatus
import com.example.healthconnectandroid.hc.HealthDataPermissionStatus
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.HealthDisplayFormatter
import com.example.healthconnectandroid.hc.InspectorDetailData
import com.example.healthconnectandroid.hc.VisualizationType
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import com.example.healthconnectandroid.ui.StatusTone
import com.example.healthconnectandroid.ui.format.MetricDisplayFormatter
import java.time.Instant
import java.time.ZoneId

internal fun InspectorDetailData.hasNoDisplayData(): Boolean =
    recordListTotalCount == 0 &&
        readableRows.isEmpty() &&
        chartPoints.isEmpty() &&
        dailyTotals.isEmpty() &&
        dailyNumericSummaries.isEmpty()

internal fun emptyReason(
    detail: InspectorDetailData,
    permissionStatus: HealthDataPermissionStatus
): String = when {
    detail.descriptor.implementationStatus != HealthDataImplementationStatus.IMPLEMENTED ->
        "0 records. This registry type is planned and does not have a reader yet."
    permissionStatus == HealthDataPermissionStatus.UNSUPPORTED ->
        "0 records. This record type or permission is unsupported by the current SDK/device."
    permissionStatus == HealthDataPermissionStatus.MISSING ->
        "0 records shown for this range. Permission is missing, so sync is disabled."
    detail.totalLocalRecordsForType == 0 && detail.lastSynced == null ->
        "0 records found. Sync has not run for this type yet."
    detail.totalLocalRecordsForType == 0 ->
        "0 records found. Permission appears granted, but Health Connect returned no local data."
    else ->
        "0 records found for ${detail.range.label}. Try a wider range."
}

internal fun permissionStatusText(status: HealthDataPermissionStatus): String = status.label

internal fun implementationStatusText(status: HealthDataImplementationStatus): String =
    when (status) {
        HealthDataImplementationStatus.IMPLEMENTED -> "Ready"
        HealthDataImplementationStatus.PLANNED -> "Planned"
    }

internal fun implementationTone(status: HealthDataImplementationStatus): StatusTone =
    when (status) {
        HealthDataImplementationStatus.IMPLEMENTED -> StatusTone.Success
        HealthDataImplementationStatus.PLANNED -> StatusTone.Info
    }

internal fun permissionTone(status: HealthDataPermissionStatus): StatusTone =
    when (status) {
        HealthDataPermissionStatus.GRANTED -> StatusTone.Success
        HealthDataPermissionStatus.MISSING -> StatusTone.Warning
        HealthDataPermissionStatus.UNSUPPORTED -> StatusTone.Warning
        HealthDataPermissionStatus.NOT_IMPLEMENTED -> StatusTone.Info
    }

internal fun visualizationLabel(type: VisualizationType): String =
    when (type) {
        VisualizationType.TIME_SERIES -> "Time-series chart"
        VisualizationType.TREND -> "Trend chart"
        VisualizationType.DAILY_AGGREGATE -> "Daily totals"
        VisualizationType.SESSION_TIMELINE -> "Sleep sessions"
        VisualizationType.MEASUREMENT_LIST -> "Measurement list"
        VisualizationType.RAW_TABLE -> "Raw table"
    }

internal fun formatInstant(value: Instant?, zoneId: ZoneId): String =
    value?.let { HealthDisplayFormatter.formatInstantForUi(it, zoneId) }?.ifBlank { null } ?: "Never"

internal fun lastSyncedDataText(value: Instant?, zoneId: ZoneId): String =
    "Last synced data: ${formatInstant(value, zoneId)}"

internal fun syncStatusText(result: HealthDataTypeSyncResult, zoneId: ZoneId): String =
    when {
        result.terminalStatus == SyncRunStatus.TIMEOUT -> "${result.key} sync timed out: ${result.errorMessage.orEmpty()}"
        result.terminalStatus == SyncRunStatus.CANCELLED -> "${result.key} sync cancelled"
        result.errorMessage != null -> "${result.key} sync failed: ${result.errorMessage}"
        result.skippedReason != null -> "${result.key} skipped: ${result.skippedReason}"
        result.recordsInserted + result.recordsUpdated + result.aggregateRowsStored + result.valuesStored > 0 ->
            "${result.key}: inserted data, read ${result.recordsRead}, inserted ${result.recordsInserted}, " +
                "updated ${result.recordsUpdated}, duplicates ${result.recordsSkippedDuplicate}, " +
                "daily summaries ${result.aggregateRowsStored}${result.syncDataSizeText()}${result.sourceRangeText(zoneId)}${result.syncRangeText(zoneId)}"
        result.localDaysChecked > 0 && result.localDaysRequested == 0 ->
            "${result.key}: no missing local days${result.syncDataSizeText()}${result.syncRangeText(zoneId)}"
        result.recordsRead + result.aggregateRowsRead == 0 ->
            "${result.key}: no source data returned${result.syncDataSizeText()}${result.syncRangeText(zoneId)}"
        result.recordsSkippedDuplicate > 0 ->
            "${result.key}: no new data, read ${result.recordsRead}, duplicates ${result.recordsSkippedDuplicate}" +
                result.syncDataSizeText() + result.sourceRangeText(zoneId) + result.syncRangeText(zoneId)
        else -> "${result.key}: read ${result.recordsRead}, inserted ${result.recordsInserted}, " +
            "updated ${result.recordsUpdated}, duplicates ${result.recordsSkippedDuplicate}, " +
            "daily summaries ${result.aggregateRowsStored}${result.syncDataSizeText()}${result.sourceRangeText(zoneId)}${result.syncRangeText(zoneId)}"
    }

private fun HealthDataTypeSyncResult.syncDataSizeText(): String =
    ", read ${formatBytesMb(sourceBytesRead)}, wrote ${formatBytesMb(localBytesWritten)}"

private fun HealthDataTypeSyncResult.sourceRangeText(zoneId: ZoneId): String {
    val start = sourceStart ?: return ""
    val end = sourceEnd ?: return ""
    return ", source ${MetricDisplayFormatter.formatShortInstant(start, zoneId)} to ${MetricDisplayFormatter.formatShortInstant(end, zoneId)}"
}

private fun formatBytesMb(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb < 0.01) "<0.01 MB" else String.format(java.util.Locale.US, "%.2f MB", mb)
}

private fun HealthDataTypeSyncResult.syncRangeText(zoneId: ZoneId): String {
    val start = requestedStart ?: return ""
    val end = requestedEnd ?: return ""
    return ", range ${MetricDisplayFormatter.formatShortInstant(start, zoneId)} to ${MetricDisplayFormatter.formatShortInstant(end, zoneId)}"
}
