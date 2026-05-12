package com.example.healthconnectandroid.data

data class HealthAggregateCsvRow(
    val localId: Long,
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
