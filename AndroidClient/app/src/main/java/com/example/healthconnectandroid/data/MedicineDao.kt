package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

data class MedicineScheduleRow(
    val medicineLocalId: Long,
    val medicineName: String,
    val medicineDoseText: String?,
    val medicineSummary: String?,
    val medicineDetails: String?,
    val slot: String
)

@Dao
interface MedicineDao {
    @Query(
        """
        SELECT * FROM medicine_items
        WHERE active = 1
        ORDER BY name COLLATE NOCASE ASC, localId ASC
        """
    )
    suspend fun activeMedicines(): List<MedicineItemEntity>

    @Query("SELECT * FROM medicine_items WHERE localId = :localId")
    suspend fun medicineById(localId: Long): MedicineItemEntity?

    @Query(
        """
        SELECT * FROM medicine_items
        ORDER BY active DESC, name COLLATE NOCASE ASC, localId ASC
        """
    )
    suspend fun allMedicines(): List<MedicineItemEntity>

    @Query(
        """
        SELECT
            item.localId AS medicineLocalId,
            item.name AS medicineName,
            item.doseText AS medicineDoseText,
            item.summary AS medicineSummary,
            item.details AS medicineDetails,
            schedule.slot AS slot
        FROM medicine_schedules schedule
        INNER JOIN medicine_items item ON item.localId = schedule.medicineLocalId
        WHERE item.active = 1
        ORDER BY item.name COLLATE NOCASE ASC, schedule.slot ASC
        """
    )
    suspend fun activeSchedules(): List<MedicineScheduleRow>

    @Query(
        """
        SELECT
            item.localId AS medicineLocalId,
            item.name AS medicineName,
            item.doseText AS medicineDoseText,
            item.summary AS medicineSummary,
            item.details AS medicineDetails,
            schedule.slot AS slot
        FROM medicine_schedules schedule
        INNER JOIN medicine_items item ON item.localId = schedule.medicineLocalId
        WHERE item.active = 1
          AND schedule.slot = :slot
        ORDER BY item.name COLLATE NOCASE ASC, item.localId ASC
        """
    )
    suspend fun activeSchedulesForSlot(slot: String): List<MedicineScheduleRow>

    @Query("SELECT * FROM medicine_schedules WHERE medicineLocalId = :medicineLocalId ORDER BY slot ASC")
    suspend fun schedulesForMedicine(medicineLocalId: Long): List<MedicineScheduleEntity>

    @Query("SELECT * FROM medicine_reminder_settings")
    suspend fun reminderSettings(): List<MedicineReminderSettingEntity>

    @Query(
        """
        SELECT * FROM medicine_dose_logs
        WHERE localDate BETWEEN :startDate AND :endDate
        ORDER BY recordedEpochMillis DESC, localId DESC
        """
    )
    suspend fun logsBetween(startDate: String, endDate: String): List<MedicineDoseLogEntity>

    @Query(
        """
        SELECT * FROM medicine_dose_logs
        WHERE localDate = :localDate
        ORDER BY recordedEpochMillis DESC, localId DESC
        """
    )
    suspend fun logsForDate(localDate: String): List<MedicineDoseLogEntity>

    @Insert
    suspend fun insertMedicine(item: MedicineItemEntity): Long

    @Update
    suspend fun updateMedicine(item: MedicineItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSchedules(schedules: List<MedicineScheduleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminderSetting(setting: MedicineReminderSettingEntity)

    @Insert
    suspend fun insertDoseLogs(logs: List<MedicineDoseLogEntity>): List<Long>

    @Query("DELETE FROM medicine_schedules WHERE medicineLocalId = :medicineLocalId")
    suspend fun deleteSchedulesForMedicine(medicineLocalId: Long)

    @Query("DELETE FROM medicine_dose_logs WHERE localId = :localId")
    suspend fun deleteDoseLog(localId: Long)

    @Query("DELETE FROM medicine_dose_logs WHERE localId IN (:localIds)")
    suspend fun deleteDoseLogs(localIds: List<Long>): Int
}
