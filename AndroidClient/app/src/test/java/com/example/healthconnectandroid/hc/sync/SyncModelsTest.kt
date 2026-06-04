package com.example.healthconnectandroid.hc.sync

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncModelsTest {
    @Test
    fun cancelledAndTimeoutAreNotSuccessStatuses() {
        assertNotEquals(SyncRunStatus.SUCCESS.id, SyncRunStatus.CANCELLED.id)
        assertNotEquals(SyncRunStatus.SUCCESS.id, SyncRunStatus.TIMEOUT.id)
    }

    @Test
    fun progressFractionUsesCompletedTypes() {
        val progress = SyncProgress(
            mode = SyncMode.FULL_HISTORY,
            currentType = null,
            completedTypes = 2,
            totalTypes = 4,
            inserted = 0,
            updated = 0,
            duplicates = 0,
            errors = 0,
            rangeStart = null,
            rangeEnd = null,
            isCancellable = true,
            isIndeterminate = false
        )

        assertEquals(0.5f, progress.progressFraction ?: -1f, 0.0001f)
    }

    @Test
    fun progressFractionUsesCurrentTypePhaseWhileRunning() {
        val progress = SyncProgress(
            mode = SyncMode.SELECTED_TYPE,
            currentType = "Heart rate",
            completedTypes = 0,
            totalTypes = 1,
            inserted = 0,
            updated = 0,
            duplicates = 0,
            errors = 0,
            rangeStart = null,
            rangeEnd = null,
            isCancellable = true,
            isIndeterminate = false,
            phase = SyncProgressPhase.FETCHING
        )

        assertEquals(0.12f, progress.progressFraction ?: -1f, 0.0001f)
    }

    @Test
    fun selectedTypeCompleteProgressIsFull() {
        val progress = SyncProgress(
            mode = SyncMode.SELECTED_TYPE,
            currentType = null,
            completedTypes = 1,
            totalTypes = 1,
            inserted = 12,
            updated = 0,
            duplicates = 0,
            errors = 0,
            rangeStart = null,
            rangeEnd = null,
            isCancellable = false,
            isIndeterminate = false,
            phase = SyncProgressPhase.INSERTED_DATA
        )

        assertEquals(1f, progress.progressFraction ?: -1f, 0.0001f)
    }
}
