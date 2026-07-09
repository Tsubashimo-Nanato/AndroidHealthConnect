package com.example.healthconnectandroid.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medicine_items",
    indices = [
        Index(value = ["active", "name"]),
        Index(value = ["updatedEpochMillis"])
    ]
)
data class MedicineItemEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val name: String,
    val notes: String?,
    val doseText: String?,
    val summary: String?,
    val details: String?,
    val active: Boolean,
    val createdEpochMillis: Long,
    val updatedEpochMillis: Long
)

@Entity(
    tableName = "medicine_schedules",
    primaryKeys = ["medicineLocalId", "slot"],
    foreignKeys = [
        ForeignKey(
            entity = MedicineItemEntity::class,
            parentColumns = ["localId"],
            childColumns = ["medicineLocalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["slot"]),
        Index(value = ["medicineLocalId"])
    ]
)
data class MedicineScheduleEntity(
    val medicineLocalId: Long,
    val slot: String,
    val createdEpochMillis: Long
)

@Entity(tableName = "medicine_reminder_settings")
data class MedicineReminderSettingEntity(
    @PrimaryKey val slot: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean,
    val alarmEnabled: Boolean,
    val updatedEpochMillis: Long
)

@Entity(
    tableName = "medicine_dose_logs",
    indices = [
        Index(value = ["localDate", "slot"]),
        Index(value = ["medicineLocalId"]),
        Index(value = ["status"]),
        Index(value = ["recordedEpochMillis"])
    ]
)
data class MedicineDoseLogEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val medicineLocalId: Long?,
    val medicineName: String,
    val slot: String,
    val localDate: String,
    val scheduledEpochMillis: Long?,
    val recordedEpochMillis: Long,
    val status: String,
    val source: String,
    val note: String?,
    val createdEpochMillis: Long
)
