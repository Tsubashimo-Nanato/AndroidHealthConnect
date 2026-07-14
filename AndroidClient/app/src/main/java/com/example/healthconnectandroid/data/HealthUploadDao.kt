package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthUploadDao {
    @Query(
        """
        SELECT MAX(
            0,
            (SELECT COUNT(*) FROM health_records) -
            (
                SELECT COUNT(*)
                FROM health_upload_ack ack
                WHERE ack.serverKey = :serverKey
                  AND ack.itemKind = 'record'
            )
        )
        """
    )
    suspend fun pendingAllRecordCount(serverKey: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM health_records r
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = 'record'
              AND ack.localId = r.localId
        )
          AND COALESCE(r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis
        """
    )
    suspend fun pendingRecentRecordCount(serverKey: String, startEpochMillis: Long): Int

    @Query(
        """
        SELECT MAX(
            0,
            (SELECT COUNT(*) FROM health_values) -
            (
                SELECT COUNT(*)
                FROM health_upload_ack ack
                WHERE ack.serverKey = :serverKey
                  AND ack.itemKind = 'value'
            )
        )
        """
    )
    suspend fun pendingAllValueCount(serverKey: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM health_values v
        LEFT JOIN health_records r ON r.localId = v.recordLocalId
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = 'value'
              AND ack.localId = v.localId
        )
          AND COALESCE(
              v.endEpochMillis,
              v.startEpochMillis,
              v.sampleEpochMillis,
              r.endEpochMillis,
              r.startEpochMillis
          ) >= :startEpochMillis
        """
    )
    suspend fun pendingRecentValueCount(serverKey: String, startEpochMillis: Long): Int

    @Query(
        """
        SELECT MAX(
            0,
            (SELECT COUNT(*) FROM health_aggregate_summaries) -
            (
                SELECT COUNT(*)
                FROM health_upload_ack ack
                WHERE ack.serverKey = :serverKey
                  AND ack.itemKind = 'aggregate'
            )
        )
        """
    )
    suspend fun pendingAllAggregateCount(serverKey: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM health_aggregate_summaries a
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = 'aggregate'
              AND ack.localId = a.localId
        )
          AND a.bucketEndEpochMillis >= :startEpochMillis
        """
    )
    suspend fun pendingRecentAggregateCount(serverKey: String, startEpochMillis: Long): Int

    @Query(
        """
        SELECT r.*
        FROM health_records r
        WHERE r.localId > :afterLocalId
          AND NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = 'record'
              AND ack.localId = r.localId
        )
          AND (:startEpochMillis IS NULL OR COALESCE(r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis)
        ORDER BY r.localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingRecords(
        serverKey: String,
        startEpochMillis: Long?,
        afterLocalId: Long,
        limit: Int
    ): List<HealthRecordEntity>

    @Query(
        """
        SELECT v.*
        FROM health_values v
        LEFT JOIN health_records r ON r.localId = v.recordLocalId
        WHERE v.localId > :afterLocalId
          AND NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = 'value'
              AND ack.localId = v.localId
        )
          AND (
              :startEpochMillis IS NULL
              OR COALESCE(
                  v.endEpochMillis,
                  v.startEpochMillis,
                  v.sampleEpochMillis,
                  r.endEpochMillis,
                  r.startEpochMillis
              ) >= :startEpochMillis
          )
        ORDER BY v.localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingValues(
        serverKey: String,
        startEpochMillis: Long?,
        afterLocalId: Long,
        limit: Int
    ): List<HealthValueEntity>

    @Query(
        """
        SELECT a.*
        FROM health_aggregate_summaries a
        WHERE a.localId > :afterLocalId
          AND NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = 'aggregate'
              AND ack.localId = a.localId
        )
          AND (:startEpochMillis IS NULL OR a.bucketEndEpochMillis >= :startEpochMillis)
        ORDER BY a.localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingAggregates(
        serverKey: String,
        startEpochMillis: Long?,
        afterLocalId: Long,
        limit: Int
    ): List<HealthAggregateEntity>

    @Query(
        """
        SELECT COUNT(*)
        FROM medicine_items item
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = :itemKind
              AND ack.localId = item.localId
              AND ack.uploadedAtEpochMillis >= item.updatedEpochMillis
        )
        """
    )
    suspend fun pendingMedicineItemCount(serverKey: String, itemKind: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM medicine_dose_logs log
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = :itemKind
              AND ack.localId = log.localId
        )
          AND (:startEpochMillis IS NULL OR log.recordedEpochMillis >= :startEpochMillis)
        """
    )
    suspend fun pendingMedicineDoseLogCount(serverKey: String, itemKind: String, startEpochMillis: Long?): Int

    @Query(
        """
        SELECT item.*
        FROM medicine_items item
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = :itemKind
              AND ack.localId = item.localId
              AND ack.uploadedAtEpochMillis >= item.updatedEpochMillis
        )
        ORDER BY item.updatedEpochMillis ASC, item.localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingMedicineItems(serverKey: String, itemKind: String, limit: Int): List<MedicineItemEntity>

    @Query(
        """
        SELECT *
        FROM medicine_schedules
        WHERE medicineLocalId IN (:medicineLocalIds)
        ORDER BY medicineLocalId ASC, slot ASC
        """
    )
    suspend fun schedulesForMedicineUpload(medicineLocalIds: List<Long>): List<MedicineScheduleEntity>

    @Query(
        """
        SELECT log.*
        FROM medicine_dose_logs log
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = :itemKind
              AND ack.localId = log.localId
        )
          AND (:startEpochMillis IS NULL OR log.recordedEpochMillis >= :startEpochMillis)
        ORDER BY log.recordedEpochMillis ASC, log.localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingMedicineDoseLogs(
        serverKey: String,
        itemKind: String,
        startEpochMillis: Long?,
        limit: Int
    ): List<MedicineDoseLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcks(acks: List<HealthUploadAckEntity>)

    @Query("DELETE FROM health_upload_ack")
    suspend fun clearAll()
}
