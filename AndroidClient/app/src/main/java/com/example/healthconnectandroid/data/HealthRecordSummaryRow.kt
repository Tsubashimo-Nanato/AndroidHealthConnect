package com.example.healthconnectandroid.data

data class HealthRecordSummaryRow(
    val recordType: String,
    val recordCount: Int,
    val recentRecordCount: Int,
    val lastSyncedEpochMillis: Long?,
    val latestRecordEpochMillis: Long?
)
