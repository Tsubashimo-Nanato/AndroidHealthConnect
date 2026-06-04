package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_sync_coverage",
    indices = [
        Index(value = ["recordType", "coveredStartEpochMillis", "coveredEndEpochMillis"]),
        Index(value = ["recordType", "status"])
    ]
)
data class HealthSyncCoverageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val recordType: String,
    val coveredStartEpochMillis: Long,
    val coveredEndEpochMillis: Long,
    val status: String,
    val mode: String,
    val updatedAtEpochMillis: Long,
    val recordsRead: Int,
    val recordsInserted: Int,
    val recordsSkippedDuplicate: Int,
    val errorMessage: String?
)
