package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncResultSeverityPolicyTest {
    @Test
    fun neutralWhenNoSyncHasRun() {
        assertEquals(SyncResultSeverity.NEUTRAL, SyncResultSeverityPolicy.fromResults(emptyList()))
    }

    @Test
    fun successWhenAnyRowsStoredWithoutErrors() {
        val severity = SyncResultSeverityPolicy.fromResults(
            listOf(HealthDataTypeSyncResult(key = "heart_rate", recordsInserted = 1))
        )

        assertEquals(SyncResultSeverity.SUCCESS, severity)
    }

    @Test
    fun warningWhenRowsStoredWithErrors() {
        val severity = SyncResultSeverityPolicy.fromResults(
            listOf(
                HealthDataTypeSyncResult(key = "heart_rate", recordsInserted = 1),
                HealthDataTypeSyncResult(key = "sleep", errorMessage = "missing permission")
            )
        )

        assertEquals(SyncResultSeverity.WARNING, severity)
    }

    @Test
    fun errorWhenNoUsefulRowsStored() {
        val severity = SyncResultSeverityPolicy.fromResults(
            listOf(HealthDataTypeSyncResult(key = "heart_rate", recordsSkippedDuplicate = 10))
        )

        assertEquals(SyncResultSeverity.ERROR, severity)
    }

    @Test
    fun errorWhenCancelledBeforeUsefulRows() {
        val severity = SyncResultSeverityPolicy.fromResults(
            listOf(
                HealthDataTypeSyncResult(
                    key = "sleep_session",
                    terminalStatus = SyncRunStatus.CANCELLED
                )
            )
        )

        assertEquals(SyncResultSeverity.ERROR, severity)
    }
}
