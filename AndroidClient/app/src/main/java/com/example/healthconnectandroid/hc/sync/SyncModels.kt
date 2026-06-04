package com.example.healthconnectandroid.hc.sync

import java.time.Duration
import java.time.Instant

enum class SyncMode(val label: String) {
    SMART("Smart Sync"),
    FULL_HISTORY("Full Resync"),
    SELECTED_TYPE("Selected Sync"),
    PERIODIC("Periodic Sync"),
    LEGACY_HR_DEBUG("Legacy HR Debug")
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
    val selectedSyncTimeout: Duration = Duration.ofMinutes(10),
    val smartSyncTimeout: Duration = Duration.ofMinutes(30),
    val fullSyncGlobalTimeout: Duration = Duration.ofHours(12)
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
    val message: String? = null
) {
    val progressFraction: Float?
        get() = totalTypes.takeIf { it > 0 && !isIndeterminate }
            ?.let {
                val currentPhaseFraction = if (currentType != null) phase.fraction else 0f
                ((completedTypes + currentPhaseFraction) / it).coerceIn(0f, 1f)
            }
}
