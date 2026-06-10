package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusTextTest {
    @Test
    fun syncAllStatusTextSummarizesResultCounts() {
        val text = syncAllStatusText(
            results = listOf(
                HealthDataTypeSyncResult(
                    key = "heart_rate",
                    recordsInserted = 3,
                    recordsUpdated = 1,
                    recordsSkippedDuplicate = 2,
                    aggregateRowsStored = 4
                ),
                HealthDataTypeSyncResult(
                    key = "sleep_session",
                    skippedReason = "missing permission"
                ),
                HealthDataTypeSyncResult(
                    key = "steps",
                    terminalStatus = SyncRunStatus.TIMEOUT,
                    errorMessage = "timed out"
                ),
                HealthDataTypeSyncResult(
                    key = "weight",
                    terminalStatus = SyncRunStatus.CANCELLED,
                    errorMessage = "cancelled"
                ),
                HealthDataTypeSyncResult(
                    key = "distance",
                    errorMessage = "read failed"
                )
            ),
            label = "Smart sync"
        )

        assertEquals(
            "Smart sync complete: types 5, inserted 3, updated 1, duplicates 2, " +
                "summaries 4, skipped 1, timeouts 1, cancelled 1, errors 1",
            text
        )
    }
}
