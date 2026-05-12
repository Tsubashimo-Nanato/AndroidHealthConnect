package com.example.healthconnectandroid.data

/** Lightweight projection used for CSV export. */
data class HrRow(
    val epochSecond: Long,
    val bpm: Float
)
