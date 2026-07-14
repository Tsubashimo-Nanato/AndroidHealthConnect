package com.example.healthconnectandroid.data

data class HealthRecordRetentionRow(
    val localId: Long,
    val startEpochMillis: Long,
    val endEpochMillis: Long?
) {
    fun endsBefore(cutoffEpochMillis: Long): Boolean =
        (endEpochMillis ?: startEpochMillis) < cutoffEpochMillis
}
