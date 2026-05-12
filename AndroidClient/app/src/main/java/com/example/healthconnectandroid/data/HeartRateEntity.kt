package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "heart_rate")
data class HeartRateEntity(
    @PrimaryKey val epochSecond: Long,   // UTC seconds since epoch
    val bpm: Float                       // beats per minute
)
