package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_values",
    indices = [
        Index(value = ["recordLocalId"]),
        Index(value = ["recordLocalId", "valueKey"]),
        Index(value = ["metric", "sampleEpochMillis"]),
        Index(value = ["metric", "localDate"]),
        Index(value = ["metric", "startEpochMillis"])
    ]
)
data class HealthValueEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val recordLocalId: Long,
    val valueKey: String,
    val metric: String,
    val unit: String?,
    val label: String?,
    val category: String?,
    val numericValue: Double?,
    val secondaryNumericValue: Double?,
    val valueFloat: Double?,
    val valueInt: Long?,
    val valueText: String?,
    val valueJson: String?,
    val startEpochMillis: Long?,
    val endEpochMillis: Long?,
    val localDate: String?,
    val sampleEpochMillis: Long?,
    val sequence: Int?
)
