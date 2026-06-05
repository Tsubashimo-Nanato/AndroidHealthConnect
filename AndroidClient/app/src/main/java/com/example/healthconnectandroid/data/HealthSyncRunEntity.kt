package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_sync_runs",
    indices = [
        Index(value = ["recordType", "startedEpochMillis"]),
        Index(value = ["recordType", "finishedEpochMillis"]),
        Index(value = ["finishedEpochMillis"]),
        Index(value = ["status"])
    ]
)
data class HealthSyncRunEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val recordType: String,
    val requestedStartEpochMillis: Long,
    val requestedEndEpochMillis: Long,
    val startedEpochMillis: Long,
    val finishedEpochMillis: Long?,
    val status: String,
    val recordsRead: Int,
    val recordsInserted: Int,
    val recordsUpdated: Int,
    val recordsSkippedDuplicate: Int,
    val valuesStored: Int,
    val errorMessage: String?
)
