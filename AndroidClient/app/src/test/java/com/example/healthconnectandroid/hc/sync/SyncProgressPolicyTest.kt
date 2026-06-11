package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncProgressPolicyTest {
    @Test
    fun terminalPhaseTreatsStoredValuesAsInsertedData() {
        val phase = SyncProgressPolicy.terminalPhase(
            HealthDataTypeSyncResult(
                key = "heart_rate",
                valuesStored = 12
            )
        )

        assertEquals(SyncProgressPhase.INSERTED_DATA, phase)
    }

    @Test
    fun selectedCompletionMessageReportsCoveredRangeWithoutMissingDays() {
        val message = SyncProgressPolicy.selectedCompletionMessage(
            HealthDataTypeSyncResult(
                key = "steps",
                localDaysChecked = 7,
                localDaysRequested = 0
            )
        )

        assertEquals("Selected sync found no missing local days", message)
    }

    @Test
    fun combineSelectedTypeResultsPreservesTotalsAndErrorPriority() {
        val start = Instant.parse("2026-06-01T00:00:00Z")
        val end = Instant.parse("2026-06-03T00:00:00Z")

        val result = SyncProgressPolicy.combineSelectedTypeResults(
            key = "heart_rate",
            requestedStart = start,
            requestedEnd = end,
            localDaysChecked = 2,
            localDaysRequested = 2,
            results = listOf(
                HealthDataTypeSyncResult(
                    key = "heart_rate",
                    recordsRead = 10,
                    recordsInserted = 3,
                    valuesStored = 30,
                    sourceStart = Instant.parse("2026-06-01T01:00:00Z"),
                    sourceEnd = Instant.parse("2026-06-01T02:00:00Z")
                ),
                HealthDataTypeSyncResult(
                    key = "heart_rate",
                    recordsRead = 2,
                    terminalStatus = SyncRunStatus.TIMEOUT,
                    errorMessage = "timed out"
                )
            )
        )

        assertEquals(12, result.recordsRead)
        assertEquals(3, result.recordsInserted)
        assertEquals(30, result.valuesStored)
        assertEquals(SyncRunStatus.TIMEOUT, result.terminalStatus)
        assertEquals("timed out", result.errorMessage)
        assertEquals(Instant.parse("2026-06-01T01:00:00Z"), result.sourceStart)
        assertEquals(Instant.parse("2026-06-01T02:00:00Z"), result.sourceEnd)
    }

    @Test
    fun typeStepProgressAddsPreviousResultsAndCurrentStep() {
        val progress = SyncProgressPolicy.progressForTypeStep(
            mode = SyncMode.SELECTED_TYPE,
            typeName = "Heart Rate",
            completedTypes = 1,
            totalTypes = 3,
            previousResults = listOf(
                HealthDataTypeSyncResult(
                    key = "steps",
                    recordsRead = 4,
                    aggregateRowsRead = 2,
                    recordsInserted = 1,
                    recordsSkippedDuplicate = 1,
                    sourceBytesRead = 100,
                    localBytesWritten = 50
                )
            ),
            typeProgress = SyncTypeProgress(
                phase = SyncProgressPhase.STORING,
                recordsRead = 5,
                inserted = 2,
                updated = 1,
                duplicates = 3,
                sourceBytesRead = 200,
                localBytesWritten = 80,
                message = "Saving rows"
            ),
            rangeStart = null,
            rangeEnd = null,
            isCancellable = true,
            messagePrefix = "Day 1/2: "
        )

        assertEquals("Heart Rate", progress.currentType)
        assertEquals(11, progress.read)
        assertEquals(3, progress.inserted)
        assertEquals(1, progress.updated)
        assertEquals(4, progress.duplicates)
        assertEquals(300, progress.sourceBytesRead)
        assertEquals(130, progress.localBytesWritten)
        assertEquals("Day 1/2: Saving rows", progress.message)
    }
}
