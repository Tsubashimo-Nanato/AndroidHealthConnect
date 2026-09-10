package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

enum class SyncMode(val label: String) {
    SMART("Sync New Data"),
    FULL_HISTORY("Full Resync"),
    SELECTED_TYPE("Selected Sync"),
    PERIODIC("Periodic Sync"),
    HISTORY_BACKFILL("Background history"),
    LEGACY_HR_DEBUG("Heart-Rate Compatibility Debug")
}

enum class SyncRunStatus(val id: String) {
    SUCCESS("success"),
    SKIPPED("skipped"),
    ERROR("error"),
    CANCELLED("cancelled"),
    TIMEOUT("timeout")
}

enum class SyncProgressPhase(val label: String, val fraction: Float) {
    PREPARING("Preparing", 0.05f),
    FETCHING("Fetching", 0.12f),
    STORING("Storing", 0.62f),
    AGGREGATING("Aggregating", 0.82f),
    INSERTED_DATA("Inserted data", 1f),
    NO_SOURCE_DATA("No data", 1f),
    NO_NEW_DATA("No new data", 1f),
    COMPLETE("Complete", 1f),
    SKIPPED("Skipped", 1f),
    FAILED("Failed", 1f),
    CANCELLED("Cancelled", 1f),
    TIMEOUT("Timed out", 1f)
}

data class SyncTypeProgress(
    val phase: SyncProgressPhase,
    val recordsRead: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val duplicates: Int = 0,
    val errors: Int = 0,
    val sourceBytesRead: Long = 0,
    val localBytesWritten: Long = 0,
    val message: String? = null
)

data class SyncTimeoutConfig(
    val perTypeTimeout: Duration = Duration.ofMinutes(5),
    val historyBackfillTypeTimeout: Duration = Duration.ofMinutes(2),
    val selectedSyncTimeout: Duration = Duration.ofMinutes(10),
    val smartSyncTimeout: Duration = Duration.ofMinutes(30),
    val fullSyncGlobalTimeout: Duration = Duration.ofHours(12)
)

internal data class HistoryBackfillBatchResult(
    val results: List<HealthDataTypeSyncResult>,
    val hasMore: Boolean,
    val nextStartIndex: Int
)

data class SyncProgress(
    val mode: SyncMode,
    val currentType: String?,
    val completedTypes: Int,
    val totalTypes: Int,
    val read: Int = 0,
    val inserted: Int,
    val updated: Int,
    val duplicates: Int,
    val errors: Int,
    val sourceBytesRead: Long = 0,
    val localBytesWritten: Long = 0,
    val rangeStart: Instant?,
    val rangeEnd: Instant?,
    val isCancellable: Boolean,
    val isIndeterminate: Boolean,
    val phase: SyncProgressPhase = SyncProgressPhase.PREPARING,
    val message: String? = null,
    val reportedFraction: Float? = null
) {
    val progressFraction: Float?
        get() = reportedFraction ?: totalTypes.takeIf { it > 0 }
            ?.let {
                val currentPhaseFraction = if (currentType != null) {
                    phase.fraction.coerceAtMost(MAX_RUNNING_TYPE_FRACTION)
                } else {
                    0f
                }
                ((completedTypes + currentPhaseFraction) / it).coerceIn(0f, 1f)
            }

    val progressPercent: Int
        get() = ((progressFraction ?: 0f) * 100f).roundToInt().coerceIn(0, 100)

    companion object {
        private const val MAX_RUNNING_TYPE_FRACTION = 0.95f

        fun initial(
            mode: SyncMode,
            totalTypes: Int,
            isCancellable: Boolean,
            rangeStart: Instant? = null,
            rangeEnd: Instant? = null
        ): SyncProgress = SyncProgress(
            mode = mode,
            currentType = null,
            completedTypes = 0,
            totalTypes = totalTypes,
            inserted = 0,
            updated = 0,
            duplicates = 0,
            errors = 0,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            isCancellable = isCancellable,
            isIndeterminate = totalTypes <= 0,
            phase = SyncProgressPhase.PREPARING,
            message = "Preparing ${mode.label}"
        )
    }
}

internal fun HealthDataTypeSyncResult.isSuccessfulCoverageWindow(): Boolean {
    val start = requestedStart ?: return false
    val end = requestedEnd ?: return false
    return start.isBefore(end) &&
        terminalStatus == null &&
        errorMessage == null &&
        aggregateErrorMessage == null &&
        skippedReason == null
}
