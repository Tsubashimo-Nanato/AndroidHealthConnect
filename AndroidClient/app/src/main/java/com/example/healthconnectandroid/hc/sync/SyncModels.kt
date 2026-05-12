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
    val inserted: Int,
    val updated: Int,
    val duplicates: Int,
    val errors: Int,
    val rangeStart: Instant?,
    val rangeEnd: Instant?,
    val isCancellable: Boolean,
    val isIndeterminate: Boolean,
    val message: String? = null
) {
    val progressFraction: Float?
        get() = totalTypes.takeIf { it > 0 && !isIndeterminate }
            ?.let { (completedTypes.toFloat() / it).coerceIn(0f, 1f) }
}
