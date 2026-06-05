package com.example.healthconnectandroid.data

data class HealthDailyNumericSummaryRow(
    val localDate: String,
    val sampleCount: Int,
    val averageValue: Double?,
    val minValue: Double?,
    val maxValue: Double?,
    val unit: String?
)
