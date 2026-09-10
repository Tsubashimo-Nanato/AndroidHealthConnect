package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_change_tokens")
data class HealthChangeTokenEntity(
    @PrimaryKey val recordType: String,
    val token: String,
    val updatedAtEpochMillis: Long
)
