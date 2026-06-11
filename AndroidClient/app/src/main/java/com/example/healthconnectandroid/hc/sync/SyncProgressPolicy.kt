package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import java.time.Instant

internal object SyncProgressPolicy {
    fun progressForResults(
        mode: SyncMode,
        currentType: String?,
        completedTypes: Int,
        totalTypes: Int,
        results: List<HealthDataTypeSyncResult>,
        rangeStart: Instant?,
        rangeEnd: Instant?,
        isCancellable: Boolean,
        phase: SyncProgressPhase = if (currentType != null) SyncProgressPhase.PREPARING else SyncProgressPhase.COMPLETE,
        message: String?
    ): SyncProgress {
        val totals = results.progressTotals()
        return SyncProgress(
            mode = mode,
            currentType = currentType,
            completedTypes = completedTypes,
            totalTypes = totalTypes,
            read = totals.read,
            inserted = totals.inserted,
            updated = totals.updated,
            duplicates = totals.duplicates,
            errors = totals.errors,
            sourceBytesRead = totals.sourceBytesRead,
            localBytesWritten = totals.localBytesWritten,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isCancellable = isCancellable,
            isIndeterminate = totalTypes <= 0,
            phase = phase,
            message = message
        )
    }

    fun progressForTypeStep(
        mode: SyncMode,
        typeName: String,
        completedTypes: Int,
        totalTypes: Int,
        previousResults: List<HealthDataTypeSyncResult>,
        typeProgress: SyncTypeProgress,
        rangeStart: Instant?,
        rangeEnd: Instant?,
        isCancellable: Boolean,
        messagePrefix: String = ""
    ): SyncProgress {
        val previousTotals = previousResults.progressTotals()
        return SyncProgress(
            mode = mode,
            currentType = typeName,
            completedTypes = completedTypes,
            totalTypes = totalTypes,
            read = previousTotals.read + typeProgress.recordsRead,
            inserted = previousTotals.inserted + typeProgress.inserted,
            updated = previousTotals.updated + typeProgress.updated,
            duplicates = previousTotals.duplicates + typeProgress.duplicates,
            errors = previousTotals.errors + typeProgress.errors,
            sourceBytesRead = previousTotals.sourceBytesRead + typeProgress.sourceBytesRead,
            localBytesWritten = previousTotals.localBytesWritten + typeProgress.localBytesWritten,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isCancellable = isCancellable,
            isIndeterminate = totalTypes <= 0,
            phase = typeProgress.phase,
            message = messagePrefix + (typeProgress.message ?: typeProgress.phase.label)
        )
    }

    fun combineSelectedTypeResults(
        key: String,
        requestedStart: Instant,
        requestedEnd: Instant,
        localDaysChecked: Int,
        localDaysRequested: Int,
        results: List<HealthDataTypeSyncResult>
    ): HealthDataTypeSyncResult {
        val terminalStatus = when {
            results.any { it.terminalStatus == SyncRunStatus.TIMEOUT } -> SyncRunStatus.TIMEOUT
            results.any { it.terminalStatus == SyncRunStatus.CANCELLED } -> SyncRunStatus.CANCELLED
            results.any { it.errorMessage != null || it.aggregateErrorMessage != null } -> SyncRunStatus.ERROR
            else -> null
        }
        val errorMessage = results.mapNotNull { it.errorMessage }.distinct().joinToString("; ")
            .ifBlank { null }
        val aggregateErrorMessage = results.mapNotNull { it.aggregateErrorMessage }.distinct().joinToString("; ")
            .ifBlank { null }

        return HealthDataTypeSyncResult(
            key = key,
            requestedStart = requestedStart,
            requestedEnd = requestedEnd,
            recordsRead = results.sumOf { it.recordsRead },
            recordsInserted = results.sumOf { it.recordsInserted },
            recordsUpdated = results.sumOf { it.recordsUpdated },
            recordsSkippedDuplicate = results.sumOf { it.recordsSkippedDuplicate },
            valuesStored = results.sumOf { it.valuesStored },
            aggregateRowsRead = results.sumOf { it.aggregateRowsRead },
            aggregateRowsStored = results.sumOf { it.aggregateRowsStored },
            sourceBytesRead = results.sumOf { it.sourceBytesRead },
            localBytesWritten = results.sumOf { it.localBytesWritten },
            sourceStart = results.mapNotNull { it.sourceStart }.minOrNull(),
            sourceEnd = results.mapNotNull { it.sourceEnd }.maxOrNull(),
            localDaysChecked = localDaysChecked,
            localDaysRequested = localDaysRequested,
            aggregateErrorMessage = aggregateErrorMessage,
            errorMessage = errorMessage,
            terminalStatus = terminalStatus
        )
    }

    fun terminalPhase(result: HealthDataTypeSyncResult): SyncProgressPhase =
        when {
            result.terminalStatus == SyncRunStatus.TIMEOUT -> SyncProgressPhase.TIMEOUT
            result.terminalStatus == SyncRunStatus.CANCELLED -> SyncProgressPhase.CANCELLED
            result.errorMessage != null || result.aggregateErrorMessage != null -> SyncProgressPhase.FAILED
            result.skippedReason != null -> SyncProgressPhase.SKIPPED
            result.recordsInserted + result.recordsUpdated + result.aggregateRowsStored + result.valuesStored > 0 ->
                SyncProgressPhase.INSERTED_DATA
            result.localDaysChecked > 0 && result.localDaysRequested == 0 -> SyncProgressPhase.NO_NEW_DATA
            result.recordsRead + result.aggregateRowsRead == 0 -> SyncProgressPhase.NO_SOURCE_DATA
            result.recordsSkippedDuplicate > 0 -> SyncProgressPhase.NO_NEW_DATA
            else -> SyncProgressPhase.COMPLETE
        }

    fun selectedCompletionMessage(result: HealthDataTypeSyncResult): String =
        when {
            result.terminalStatus == SyncRunStatus.TIMEOUT -> "Selected sync timed out"
            result.terminalStatus == SyncRunStatus.CANCELLED -> "Selected sync cancelled"
            result.errorMessage != null -> "Selected sync failed: ${result.errorMessage}"
            result.aggregateErrorMessage != null -> "Selected sync failed: ${result.aggregateErrorMessage}"
            result.skippedReason != null -> "Selected sync skipped"
            result.localDaysChecked > 0 && result.localDaysRequested == 0 ->
                "Selected sync found no missing local days"
            terminalPhase(result) == SyncProgressPhase.INSERTED_DATA -> "Selected sync inserted data"
            terminalPhase(result) == SyncProgressPhase.NO_SOURCE_DATA -> "Selected sync found no source data"
            terminalPhase(result) == SyncProgressPhase.NO_NEW_DATA -> "Selected sync found no new data"
            else -> "Selected sync complete"
        }

    fun typeCompletionMessage(
        typeName: String,
        result: HealthDataTypeSyncResult
    ): String =
        when (terminalPhase(result)) {
            SyncProgressPhase.INSERTED_DATA -> "$typeName inserted data"
            SyncProgressPhase.NO_SOURCE_DATA -> "$typeName no source data"
            SyncProgressPhase.NO_NEW_DATA -> "$typeName no new data"
            SyncProgressPhase.FAILED -> "$typeName failed"
            SyncProgressPhase.SKIPPED -> "$typeName skipped"
            SyncProgressPhase.TIMEOUT -> "$typeName timed out"
            SyncProgressPhase.CANCELLED -> "$typeName cancelled"
            else -> "$typeName complete"
        }

    private fun List<HealthDataTypeSyncResult>.progressTotals(): ProgressTotals {
        // Progress is updated during long sync runs; keep aggregation to one pass over completed types.
        var read = 0
        var inserted = 0
        var updated = 0
        var duplicates = 0
        var errors = 0
        var sourceBytesRead = 0L
        var localBytesWritten = 0L

        for (result in this) {
            read += result.recordsRead + result.aggregateRowsRead
            inserted += result.recordsInserted
            updated += result.recordsUpdated
            duplicates += result.recordsSkippedDuplicate
            if (
                result.errorMessage != null ||
                result.aggregateErrorMessage != null ||
                result.terminalStatus == SyncRunStatus.TIMEOUT
            ) {
                errors += 1
            }
            sourceBytesRead += result.sourceBytesRead
            localBytesWritten += result.localBytesWritten
        }

        return ProgressTotals(
            read = read,
            inserted = inserted,
            updated = updated,
            duplicates = duplicates,
            errors = errors,
            sourceBytesRead = sourceBytesRead,
            localBytesWritten = localBytesWritten
        )
    }

    private data class ProgressTotals(
        val read: Int,
        val inserted: Int,
        val updated: Int,
        val duplicates: Int,
        val errors: Int,
        val sourceBytesRead: Long,
        val localBytesWritten: Long
    )
}
