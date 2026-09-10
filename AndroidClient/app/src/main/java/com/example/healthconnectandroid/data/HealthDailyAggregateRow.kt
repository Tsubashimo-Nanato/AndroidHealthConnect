package com.example.healthconnectandroid.data

data class HealthDailyAggregateRow(
    val localDate: String,
    val total: Double,
    val unit: String?
)
