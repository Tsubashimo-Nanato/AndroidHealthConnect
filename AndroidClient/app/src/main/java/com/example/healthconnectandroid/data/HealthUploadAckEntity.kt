package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "health_upload_ack",
    primaryKeys = ["serverKey", "itemKind", "localId"],
    indices = [
        Index(value = ["serverKey", "batchId"]),
        Index(value = ["serverKey", "uploadedAtEpochMillis"])
    ]
)
data class HealthUploadAckEntity(
    val serverKey: String,
    val itemKind: String,
    val localId: Long,
    val batchId: String,
    val uploadedAtEpochMillis: Long
)
