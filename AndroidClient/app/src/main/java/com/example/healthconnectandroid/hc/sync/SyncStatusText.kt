package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun syncAllStatusText(
    results: List<HealthDataTypeSyncResult>,
    label: String = "Sync"
): String {
    val inserted = results.sumOf { it.recordsInserted }
    val updated = results.sumOf { it.recordsUpdated }
    val duplicates = results.sumOf { it.recordsSkippedDuplicate }
    val summaries = results.sumOf { it.aggregateRowsStored }
    val skipped = results.count { it.skippedReason != null }
    val timeouts = results.count { it.terminalStatus == SyncRunStatus.TIMEOUT }
    val cancelled = results.count { it.terminalStatus == SyncRunStatus.CANCELLED }
    val errors = results.count {
        it.errorMessage != null && it.terminalStatus !in setOf(SyncRunStatus.TIMEOUT, SyncRunStatus.CANCELLED)
    }
    val range = syncRangeText(results)

    return "$label complete: types ${results.size}, inserted $inserted, updated $updated, duplicates $duplicates, " +
        "summaries $summaries, skipped $skipped, timeouts $timeouts, cancelled $cancelled, errors $errors$range"
}

private fun syncRangeText(results: List<HealthDataTypeSyncResult>): String {
    val start = results.mapNotNull { it.requestedStart }.minOrNull()
    val end = results.mapNotNull { it.requestedEnd }.maxOrNull()
    if (start == null || end == null) return ""
    return ", ${syncRangeText(start, end)}"
}

private fun syncRangeText(start: Instant, end: Instant): String {
    val startText = if (start == SyncRangePolicy.FULL_HISTORY_START) {
        "range full history ($start)"
    } else {
        "range ${shortInstant(start)}"
    }
    return "$startText to ${shortInstant(end)}"
}

private fun shortInstant(value: Instant): String =
    DateTimeFormatter.ofPattern("M/d HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(value)
