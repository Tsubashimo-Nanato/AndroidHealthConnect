package com.example.healthconnectandroid.data

data class HealthCsvRow(
    val localRecordId: Long,
    val localValueId: Long,
    val valueKey: String,
    val recordType: String,
    val recordKind: String,
    val healthConnectUid: String?,
    val dedupeKey: String,
    val sourcePackage: String?,
    val recordStartEpochMillis: Long,
    val recordEndEpochMillis: Long?,
    val valueStartEpochMillis: Long?,
    val valueEndEpochMillis: Long?,
    val localDate: String?,
    val zoneOffsetSeconds: Int?,
    val metric: String,
    val numericValue: Double?,
    val secondaryNumericValue: Double?,
    val unit: String?,
    val categoryOrStage: String?,
    val label: String?,
    val textValue: String?,
    val jsonValue: String?,
    val metadataJson: String?,
    val rawJson: String?
)
