package com.example.healthconnectandroid.data

data class HealthSyncSummaryRow(
    val recordType: String,
    val lastFinishedEpochMillis: Long?,
    val lastStatus: String?,
    val lastErrorMessage: String?
)
