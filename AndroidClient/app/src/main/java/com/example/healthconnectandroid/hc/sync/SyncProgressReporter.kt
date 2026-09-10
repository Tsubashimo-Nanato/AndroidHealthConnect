package com.example.healthconnectandroid.hc.sync

import kotlin.math.max

internal class SyncProgressReporter(
    private val onProgress: (SyncProgress) -> Unit,
    private val nowNanos: () -> Long = System::nanoTime,
    private val minimumIntervalNanos: Long = DEFAULT_MINIMUM_INTERVAL_NANOS
) {
    private var lastFraction = 0f
    private var lastEmittedAtNanos: Long? = null
    private var lastSignature: ProgressSignature? = null

    fun emit(progress: SyncProgress) {
        val wasComplete = lastFraction >= 1f
        val normalized = normalize(progress)
        val signature = ProgressSignature(
            currentType = normalized.currentType,
            completedTypes = normalized.completedTypes
        )
        val now = nowNanos()
        val intervalElapsed = lastEmittedAtNanos
            ?.let { now - it >= minimumIntervalNanos }
            ?: true
        val mustEmit = signature != lastSignature || intervalElapsed ||
            (normalized.progressPercent == 100 && !wasComplete)
        if (!mustEmit) return

        onProgress(normalized)
        lastSignature = signature
        lastEmittedAtNanos = now
    }

    private fun normalize(progress: SyncProgress): SyncProgress {
        if (progress.totalTypes <= 0) {
            return progress.copy(isIndeterminate = true, reportedFraction = null)
        }
        lastFraction = max(lastFraction, progress.progressFraction ?: 0f)
        return progress.copy(
            isIndeterminate = false,
            reportedFraction = lastFraction.coerceIn(0f, 1f)
        )
    }

    private data class ProgressSignature(
        val currentType: String?,
        val completedTypes: Int
    )

    private companion object {
        const val DEFAULT_MINIMUM_INTERVAL_NANOS = 200_000_000L
    }
}
