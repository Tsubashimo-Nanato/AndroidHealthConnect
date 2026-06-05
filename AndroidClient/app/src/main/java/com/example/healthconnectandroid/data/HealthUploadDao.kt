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
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = 'record'
              AND ack.localId = r.localId
        )
          AND (:startEpochMillis IS NULL OR COALESCE(r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis)
        ORDER BY r.updatedEpochMillis ASC, r.localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingRecords(serverKey: String, startEpochMillis: Long?, limit: Int): List<HealthRecordEntity>

    @Query(
        """
        SELECT v.*
        FROM health_values v
        LEFT JOIN health_records r ON r.localId = v.recordLocalId
        WHERE NOT EXISTS (
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
        ORDER BY v.recordLocalId ASC, v.sequence ASC, v.localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingValues(serverKey: String, startEpochMillis: Long?, limit: Int): List<HealthValueEntity>

    @Query(
        """
        SELECT a.*
        FROM health_aggregate_summaries a
        WHERE NOT EXISTS (
            SELECT 1 FROM health_upload_ack ack
            WHERE ack.serverKey = :serverKey
              AND ack.itemKind = 'aggregate'
              AND ack.localId = a.localId
        )
          AND (:startEpochMillis IS NULL OR a.bucketEndEpochMillis >= :startEpochMillis)
        ORDER BY a.computedEpochMillis ASC, a.localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingAggregates(serverKey: String, startEpochMillis: Long?, limit: Int): List<HealthAggregateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcks(acks: List<HealthUploadAckEntity>)

    @Query("DELETE FROM health_upload_ack")
    suspend fun clearAll()
}
