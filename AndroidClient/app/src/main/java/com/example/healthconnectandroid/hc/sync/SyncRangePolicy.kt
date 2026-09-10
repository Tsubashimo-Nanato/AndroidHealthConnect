package com.example.healthconnectandroid.hc.sync

import java.time.Duration
import java.time.Instant

object SyncRangePolicy {
    val SMART_INITIAL_WINDOW: Duration = Duration.ofHours(24)
    val FULL_HISTORY_START: Instant = Instant.EPOCH

    fun smartStart(end: Instant): Instant =
        end.minus(SMART_INITIAL_WINDOW)

    fun fullHistoryStart(): Instant = FULL_HISTORY_START
}
