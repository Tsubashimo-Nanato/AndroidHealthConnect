package com.example.healthconnectandroid.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.sync.SyncMode
import com.example.healthconnectandroid.hc.sync.SyncProgress
import com.example.healthconnectandroid.hc.sync.SyncRangePolicy
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import java.time.Instant

data class MatrixGestureDiagnostic(
    val matrixType: String = "None",
    val action: String = "None",
    val deltaSnap: String = "None",
    val selectedCount: Int = 0,
    val parentScrollPreserved: Boolean = false,
    val updatedAt: Instant? = null
)

data class SyncDiagnostic(
    val mode: String = "None",
    val currentType: String? = null,
    val requestedRange: String = "None",
    val inserted: Int = 0,
    val updated: Int = 0,
    val duplicates: Int = 0,
    val errors: Int = 0,
    val state: String = "Idle",
    val updatedAt: Instant? = null
)

data class DetailQueryDiagnostic(
    val dataType: String = "None",
    val queryRange: String = "None",
    val chartPointCount: Int = 0,
    val recordPageSize: Int = 0,
    val expandedRecordId: Long? = null,
    val sleepSessionCount: Int? = null,
    val matrixCellCount: Int? = null,
    val updatedAt: Instant? = null
)

class DeviceSmokeDiagnostics {
    var matrix by mutableStateOf(MatrixGestureDiagnostic())
        private set

    var sync by mutableStateOf(SyncDiagnostic())
        private set

    var detail by mutableStateOf(DetailQueryDiagnostic())
        private set

    fun recordMatrixGesture(
        matrixType: String,
        action: String,
        deltaSnap: Int? = null,
        selectedCount: Int,
        parentScrollPreserved: Boolean
    ) {
        matrix = MatrixGestureDiagnostic(
            matrixType = matrixType,
            action = action,
            deltaSnap = deltaSnapLabel(deltaSnap),
            selectedCount = selectedCount,
            parentScrollPreserved = parentScrollPreserved,
            updatedAt = Instant.now()
        )
    }

    fun recordSyncProgress(progress: SyncProgress) {
        sync = SyncDiagnostic(
            mode = progress.mode.label,
            currentType = progress.currentType,
            requestedRange = rangeLabel(progress.rangeStart, progress.rangeEnd),
            inserted = progress.inserted,
            updated = progress.updated,
            duplicates = progress.duplicates,
            errors = progress.errors,
            state = if (progress.errors > 0) "Running with errors" else "Running",
            updatedAt = Instant.now()
        )
    }

    fun recordSyncResults(mode: SyncMode, results: List<HealthDataTypeSyncResult>) {
        val last = results.lastOrNull()
        sync = SyncDiagnostic(
            mode = mode.label,
            currentType = last?.key,
            requestedRange = rangeLabel(last?.requestedStart, last?.requestedEnd),
            inserted = results.sumOf { it.recordsInserted + it.aggregateRowsStored },
            updated = results.sumOf { it.recordsUpdated },
            duplicates = results.sumOf { it.recordsSkippedDuplicate },
            errors = results.count { it.errorMessage != null || it.aggregateErrorMessage != null || it.terminalStatus == SyncRunStatus.TIMEOUT },
            state = terminalState(results),
            updatedAt = Instant.now()
        )
    }

    fun recordSyncResult(mode: SyncMode, result: HealthDataTypeSyncResult) {
        recordSyncResults(mode, listOf(result))
    }

    fun recordSyncCancelled(mode: SyncMode) {
        sync = sync.copy(
            mode = mode.label,
            state = "Cancelled",
            updatedAt = Instant.now()
        )
    }

    fun recordSyncFailure(mode: SyncMode, currentType: String?, rangeStart: Instant?, rangeEnd: Instant?) {
        sync = SyncDiagnostic(
            mode = mode.label,
            currentType = currentType,
            requestedRange = rangeLabel(rangeStart, rangeEnd),
            state = "Fatal error",
            updatedAt = Instant.now()
        )
    }

    fun recordLegacyHrDebug(sampleCount: Int) {
        sync = SyncDiagnostic(
            mode = SyncMode.LEGACY_HR_DEBUG.label,
            currentType = "heart_rate",
            requestedRange = "Debug query",
            inserted = sampleCount,
            state = if (sampleCount > 0) "Success" else "No data",
            updatedAt = Instant.now()
        )
    }

    fun recordDetailQuery(
        dataType: String,
        start: Instant,
        end: Instant,
        chartPointCount: Int,
        recordPageSize: Int = detail.recordPageSize,
        expandedRecordId: Long? = detail.expandedRecordId,
        sleepSessionCount: Int? = null,
        matrixCellCount: Int? = null
    ) {
        detail = DetailQueryDiagnostic(
            dataType = dataType,
            queryRange = rangeLabel(start, end),
            chartPointCount = chartPointCount,
            recordPageSize = recordPageSize,
            expandedRecordId = expandedRecordId,
            sleepSessionCount = sleepSessionCount,
            matrixCellCount = matrixCellCount,
            updatedAt = Instant.now()
        )
    }

    fun recordRecordPage(dataType: String, loadedRows: Int) {
        detail = detail.copy(
            dataType = dataType,
            recordPageSize = loadedRows,
            updatedAt = Instant.now()
        )
    }

    fun recordExpandedRecord(dataType: String, localRecordId: Long?) {
        detail = detail.copy(
            dataType = dataType,
            expandedRecordId = localRecordId,
            updatedAt = Instant.now()
        )
    }

    private fun terminalState(results: List<HealthDataTypeSyncResult>): String {
        if (results.isEmpty()) return "No data"
        val hasTimeout = results.any { it.terminalStatus == SyncRunStatus.TIMEOUT }
        val hasCancelled = results.any { it.terminalStatus == SyncRunStatus.CANCELLED }
        val hasError = results.any { it.errorMessage != null || it.aggregateErrorMessage != null }
        val useful = results.sumOf { it.recordsInserted + it.recordsUpdated + it.aggregateRowsStored }
        return when {
            hasCancelled -> "Cancelled"
            hasTimeout -> "Timeout"
            hasError && useful > 0 -> "Partial error"
            hasError -> "Fatal error"
            useful > 0 -> "Success"
            else -> "No data"
        }
    }
}

private fun deltaSnapLabel(deltaSnap: Int?): String =
    when {
        deltaSnap == null -> "no-op"
        deltaSnap < 0 -> "previous"
        deltaSnap > 0 -> "next"
        else -> "no-op"
    }

private fun rangeLabel(start: Instant?, end: Instant?): String {
    if (start == null || end == null) return "None"
    val startText = if (start == SyncRangePolicy.FULL_HISTORY_START) "Full history" else start.toString()
    return "$startText to ${end}"
}
