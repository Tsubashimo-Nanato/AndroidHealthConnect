package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_catalog_snapshot",
    indices = [
        Index(value = ["dirty"]),
        Index(value = ["generatedAtEpochMillis"])
    ]
)
data class HealthCatalogSnapshotEntity(
    @PrimaryKey val recordType: String,
    val recordCount: Int,
    val recentRecordCount: Int,
    val lastSyncedEpochMillis: Long?,
    val lastSyncStatus: String?,
    val lastSyncError: String?,
    val latestRecordEpochMillis: Long?,
    val summaryText: String,
    val latestMetric: String?,
    val latestPrimaryText: String?,
    val latestSecondaryText: String?,
    val latestValue: Double?,
    val latestSecondaryValue: Double?,
    val latestUnit: String?,
    val latestStartEpochMillis: Long?,
    val latestEndEpochMillis: Long?,
    val latestLocalDate: String?,
    val latestDurationText: String?,
    val todayLocalDate: String?,
    val todayTotal: Double?,
    val todayUnit: String?,
    val zoneId: String,
    val generatedAtEpochMillis: Long,
    val dirty: Boolean
)
