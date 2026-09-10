package com.example.healthconnectandroid.hc.sync

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncRangePolicyTest {
    private val end: Instant = Instant.parse("2026-05-10T00:00:00Z")

    @Test
    fun smartStartAlwaysUsesLatestDay() {
        assertEquals(
            end.minus(SyncRangePolicy.SMART_INITIAL_WINDOW),
            SyncRangePolicy.smartStart(end)
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
