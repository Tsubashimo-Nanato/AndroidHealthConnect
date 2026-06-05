package com.example.healthconnectandroid.data

data class HealthDashboardSummaryRow(
    val localRecordCount: Int,
    val localDataTypeCount: Int,
    val latestLocalReadEpochMillis: Long?
)
