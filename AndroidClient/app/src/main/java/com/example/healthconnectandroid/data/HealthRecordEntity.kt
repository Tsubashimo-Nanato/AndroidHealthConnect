package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_records",
    indices = [
        Index(value = ["recordType", "startEpochMillis"]),
        Index(value = ["recordType", "recordUid"]),
        Index(value = ["recordType", "dedupeKey"], unique = true),
        Index(value = ["recordType", "localDate"]),
        Index(value = ["syncStatus", "updatedEpochMillis"]),
        Index(value = ["updatedEpochMillis", "localId"])
    ]
)
data class HealthRecordEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val recordUid: String?,
    val dedupeKey: String,
    val recordType: String,
    val recordKind: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long?,
    val localDate: String,
    val startZoneOffsetSeconds: Int?,
    val endZoneOffsetSeconds: Int?,
    val sourcePackage: String?,
    val metadataJson: String?,
    val rawJson: String?,
    val syncStatus: String = "local",
    val exportStatus: String = "pending",
    val createdEpochMillis: Long,
    val updatedEpochMillis: Long,
    val lastReadEpochMillis: Long,
    val syncedEpochMillis: Long? = null,
    val exportedEpochMillis: Long? = null
)
