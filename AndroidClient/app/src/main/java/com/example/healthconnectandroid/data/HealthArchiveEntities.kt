package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_daily_archive",
    primaryKeys = ["sourceRecordLocalId", "metric"],
    indices = [
        Index(value = ["recordType", "localDate"]),
        Index(value = ["localDate"])
    ]
)
data class HealthDailyArchiveEntity(
    val sourceRecordLocalId: Long,
    val metric: String,
    val recordType: String,
    val localDate: String,
    val sampleCount: Int,
    val totalValue: Double,
    val minValue: Double,
    val maxValue: Double,
    val unit: String?,
    val lastSampleEpochMillis: Long?,
    val archivedAtEpochMillis: Long
)

@Entity(
    tableName = "health_sleep_archive",
    primaryKeys = ["sourceRecordLocalId", "sourceValueLocalId"],
    indices = [
        Index(value = ["localDate"]),
        Index(value = ["recordStartEpochMillis"])
    ]
)
data class HealthSleepArchiveEntity(
    val sourceRecordLocalId: Long,
    val sourceValueLocalId: Long,
    val valueKey: String,
    val recordStartEpochMillis: Long,
    val recordEndEpochMillis: Long?,
    val valueStartEpochMillis: Long?,
    val valueEndEpochMillis: Long?,
    val localDate: String,
    val metric: String,
    val numericValue: Double?,
    val secondaryNumericValue: Double?,
    val unit: String?,
    val category: String?,
    val label: String?,
    val textValue: String?,
    val jsonValue: String?,
    val sourcePackage: String?,
    val archivedAtEpochMillis: Long
)

@Entity(tableName = "health_retention_state")
data class HealthRetentionStateEntity(
    @PrimaryKey val recordType: String,
    val archivedBeforeEpochMillis: Long,
    val updatedAtEpochMillis: Long
)
