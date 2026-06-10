package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailStatusTextTest {
    @Test
    fun syncStatusTextReportsTimeoutWithReason() {
        val result = HealthDataTypeSyncResult(
            key = "heart_rate",
            terminalStatus = SyncRunStatus.TIMEOUT,
            errorMessage = "deadline exceeded"
        )

        assertEquals(
            "heart_rate sync timed out: deadline exceeded",
            syncStatusText(result, ZoneOffset.UTC)
        )
    }

    @Test
    fun syncStatusTextReportsSkippedReason() {
        val result = HealthDataTypeSyncResult(
            key = "steps",
            skippedReason = "permission missing"
        )

        assertEquals(
            "steps skipped: permission missing",
            syncStatusText(result, ZoneOffset.UTC)
        )
    }

    @Test
    fun syncStatusTextKeepsInsertedDataSummaryShape() {
        val result = HealthDataTypeSyncResult(
            key = "steps",
            recordsRead = 3,
            recordsInserted = 2,
            recordsUpdated = 1,
            aggregateRowsStored = 4
        )

        assertEquals(
            "steps: inserted data, read 3, inserted 2, updated 1, duplicates 0, daily summaries 4, read 0 MB, wrote 0 MB",
            syncStatusText(result, ZoneOffset.UTC)
        )
    }
}
