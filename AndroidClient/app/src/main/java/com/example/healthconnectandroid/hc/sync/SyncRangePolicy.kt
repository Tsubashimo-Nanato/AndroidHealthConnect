package com.example.healthconnectandroid.hc.sync

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

object SyncRangePolicy {
    const val SMART_SYNC_OVERLAP_MINUTES = 30L
    val SMART_INITIAL_WINDOW: Duration = Duration.ofDays(90)
    val FULL_HISTORY_START: Instant = Instant.EPOCH

    fun smartStart(
        end: Instant,
        latestSuccessfulFinishedAt: Instant?
    ): Instant =
        if (latestSuccessfulFinishedAt == null) {
            initialSmartStart(end)
        } else {
            latestSuccessfulFinishedAt
                .minus(SMART_SYNC_OVERLAP_MINUTES, ChronoUnit.MINUTES)
                .takeIf { it.isBefore(end) }
                ?: end.minus(SMART_SYNC_OVERLAP_MINUTES, ChronoUnit.MINUTES)
        }

    fun initialSmartStart(end: Instant): Instant =
        end.minus(SMART_INITIAL_WINDOW)

    fun fullHistoryStart(): Instant = FULL_HISTORY_START
}
