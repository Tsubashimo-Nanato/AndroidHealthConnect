package com.example.healthconnectandroid.hc.sync

import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRangePolicyTest {
    private val end: Instant = Instant.parse("2026-05-10T00:00:00Z")

    @Test
    fun smartStartUsesDescriptorDefaultWhenNeverSynced() {
        assertEquals(
            end.minus(SyncRangePolicy.SMART_INITIAL_WINDOW),
            SyncRangePolicy.smartStart(end, latestSuccessfulFinishedAt = null)
        )
    }

    @Test
    fun smartStartUsesLastSuccessMinusOverlap() {
        val lastSuccess = Instant.parse("2026-05-09T12:00:00Z")

        assertEquals(
            lastSuccess.minus(SyncRangePolicy.SMART_SYNC_OVERLAP_MINUTES, ChronoUnit.MINUTES),
            SyncRangePolicy.smartStart(end, latestSuccessfulFinishedAt = lastSuccess)
        )
    }

    @Test
    fun smartStartDoesNotReturnFutureStart() {
        val futureSuccess = end.plus(1, ChronoUnit.HOURS)

        assertEquals(
            end.minus(SyncRangePolicy.SMART_SYNC_OVERLAP_MINUTES, ChronoUnit.MINUTES),
            SyncRangePolicy.smartStart(end, latestSuccessfulFinishedAt = futureSuccess)
        )
    }

    @Test
    fun fullHistoryStartUsesEpoch() {
        assertEquals(
            Instant.EPOCH,
            SyncRangePolicy.fullHistoryStart()
        )
    }
}
