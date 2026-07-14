package com.example.healthconnectandroid.data

import androidx.room.Entity

@Entity(
    tableName = "health_upload_ack",
    primaryKeys = ["serverKey", "itemKind", "localId"]
)
data class HealthUploadAckEntity(
    val serverKey: String,
    val itemKind: String,
    val localId: Long,
    val batchId: String,
    val uploadedAtEpochMillis: Long
)
