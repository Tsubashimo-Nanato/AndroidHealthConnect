package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "health_aggregate_summaries",
    indices = [
        Index(
            value = [
                "recordType",
                "metric",
                "bucketPeriod",
                "bucketStartEpochMillis",
                "bucketEndEpochMillis",
                "source"
            ],
            unique = true
        ),
        Index(value = ["recordType", "localDate"]),
        Index(value = ["source", "computedEpochMillis"])
    ]
)
data class HealthAggregateEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val recordType: String,
    val metric: String,
    val bucketPeriod: String,
    val bucketStartEpochMillis: Long,
    val bucketEndEpochMillis: Long,
    val localDate: String,
    val timezoneId: String?,
    val value: Double,
    val unit: String?,
    val source: String,
    val computedEpochMillis: Long,
    val requestedStartEpochMillis: Long,
    val requestedEndEpochMillis: Long,
    val rawJson: String?
)
