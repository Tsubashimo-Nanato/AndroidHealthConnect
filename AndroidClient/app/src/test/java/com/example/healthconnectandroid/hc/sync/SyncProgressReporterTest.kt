package com.example.healthconnectandroid.hc.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProgressReporterTest {
    @Test
    fun progressStartsAtZeroEndsAtOneHundredAndNeverMovesBackward() {
        var nowNanos = 0L
        val emitted = mutableListOf<SyncProgress>()
        val reporter = SyncProgressReporter(
            onProgress = emitted::add,
            nowNanos = { nowNanos },
            minimumIntervalNanos = 100L
        )

        reporter.emit(progress(completedTypes = 0, currentType = null, SyncProgressPhase.PREPARING))
        nowNanos += 100L
        reporter.emit(progress(completedTypes = 0, currentType = "Heart Rate", SyncProgressPhase.AGGREGATING))
        nowNanos += 100L
        reporter.emit(progress(completedTypes = 0, currentType = "Heart Rate", SyncProgressPhase.FETCHING))
        nowNanos += 100L
        reporter.emit(progress(completedTypes = 4, currentType = null, SyncProgressPhase.COMPLETE))

        val percentages = emitted.map { it.progressPercent }
        assertEquals(0, percentages.first())
        assertEquals(100, percentages.last())
        assertTrue(percentages.zipWithNext().all { (previous, next) -> next >= previous })
    }

    @Test
    fun sameTypePhaseChangesAreThrottledToKeepProgressStable() {
        var nowNanos = 0L
        val emitted = mutableListOf<SyncProgress>()
        val reporter = SyncProgressReporter(
            onProgress = emitted::add,
            nowNanos = { nowNanos },
            minimumIntervalNanos = 100L
        )

        reporter.emit(progress(0, "Steps", SyncProgressPhase.FETCHING))
        nowNanos += 10L
        reporter.emit(progress(0, "Steps", SyncProgressPhase.FETCHING).copy(read = 100))
        nowNanos += 10L
        reporter.emit(progress(0, "Steps", SyncProgressPhase.STORING).copy(read = 100))

        assertEquals(1, emitted.size)
        assertEquals(SyncProgressPhase.FETCHING, emitted.first().phase)
    }

    @Test
    fun completingATypeStillEmitsInsideThrottleWindow() {
        var nowNanos = 0L
        val emitted = mutableListOf<SyncProgress>()
        val reporter = SyncProgressReporter(
            onProgress = emitted::add,
            nowNanos = { nowNanos },
            minimumIntervalNanos = 100L
        )

        reporter.emit(progress(0, "Steps", SyncProgressPhase.FETCHING))
        nowNanos += 10L
        reporter.emit(progress(1, null, SyncProgressPhase.NO_NEW_DATA))

        assertEquals(listOf(3, 25), emitted.map { it.progressPercent })
    }

    private fun progress(
        completedTypes: Int,
        currentType: String?,
        phase: SyncProgressPhase
    ) = SyncProgress(
        mode = SyncMode.SMART,
        currentType = currentType,
        completedTypes = completedTypes,
        totalTypes = 4,
        inserted = 0,
        updated = 0,
        duplicates = 0,
        errors = 0,
        rangeStart = null,
        rangeEnd = null,
        isCancellable = false,
        isIndeterminate = false,
        phase = phase
    )
}
