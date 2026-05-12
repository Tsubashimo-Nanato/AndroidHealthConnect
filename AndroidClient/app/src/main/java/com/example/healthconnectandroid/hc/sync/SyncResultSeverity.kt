package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult

enum class SyncResultSeverity {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR
}

object SyncResultSeverityPolicy {
    fun fromResults(results: List<HealthDataTypeSyncResult>): SyncResultSeverity {
        if (results.isEmpty()) return SyncResultSeverity.NEUTRAL
        val usefulRows = results.sumOf {
            it.recordsInserted + it.recordsUpdated + it.aggregateRowsStored
        }
        val anyError = results.any {
            it.errorMessage != null ||
                it.aggregateErrorMessage != null ||
                it.skippedReason != null ||
                it.terminalStatus in setOf(SyncRunStatus.ERROR, SyncRunStatus.TIMEOUT, SyncRunStatus.CANCELLED)
        }
        return when {
            anyError && usefulRows > 0 -> SyncResultSeverity.WARNING
            anyError -> SyncResultSeverity.ERROR
            usefulRows <= 0 -> SyncResultSeverity.ERROR
            else -> SyncResultSeverity.SUCCESS
        }
    }
}
