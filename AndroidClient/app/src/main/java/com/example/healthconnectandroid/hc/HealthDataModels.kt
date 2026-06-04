package com.example.healthconnectandroid.hc

import com.example.healthconnectandroid.hc.sync.SyncRunStatus
import java.time.Instant

/** Simple value used by UI/debug tools when querying a single time point. */
data class HrSample(val time: Instant, val bpm: Float)

data class HealthDataTypeSyncResult(
    val key: String,
    val requestedStart: Instant? = null,
    val requestedEnd: Instant? = null,
    val recordsRead: Int = 0,
    val recordsInserted: Int = 0,
    val recordsUpdated: Int = 0,
    val recordsSkippedDuplicate: Int = 0,
    val recordsStored: Int = recordsInserted + recordsUpdated,
    val valuesStored: Int = 0,
    val aggregateRowsRead: Int = 0,
    val aggregateRowsStored: Int = 0,
    val sourceBytesRead: Long = 0,
    val localBytesWritten: Long = 0,
    val sourceStart: Instant? = null,
    val sourceEnd: Instant? = null,
    val localDaysChecked: Int = 0,
    val localDaysRequested: Int = 0,
    val aggregateErrorMessage: String? = null,
    val skippedReason: String? = null,
    val errorMessage: String? = null,
    val terminalStatus: SyncRunStatus? = null
)
