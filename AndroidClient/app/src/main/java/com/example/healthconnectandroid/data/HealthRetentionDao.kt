package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthRetentionDao {
    @Query(
        """
        SELECT r.*
        FROM health_records r
        WHERE r.recordType = :recordType
          AND COALESCE(r.endEpochMillis, r.startEpochMillis) < :cutoffEpochMillis
          AND EXISTS (
              SELECT 1 FROM health_upload_ack recordAck
              WHERE recordAck.serverKey = :serverKey
                AND recordAck.itemKind = 'record'
                AND recordAck.localId = r.localId
                AND recordAck.uploadedAtEpochMillis >= r.updatedEpochMillis
          )
          AND NOT EXISTS (
              SELECT 1
              FROM health_values v
              WHERE v.recordLocalId = r.localId
                AND NOT EXISTS (
                    SELECT 1 FROM health_upload_ack valueAck
                    WHERE valueAck.serverKey = :serverKey
                      AND valueAck.itemKind = 'value'
                      AND valueAck.localId = v.localId
                )
          )
        ORDER BY r.startEpochMillis ASC, r.localId ASC
        LIMIT :limit
        """
    )
    suspend fun uploadedRecordCandidates(
        serverKey: String,
        recordType: String,
        cutoffEpochMillis: Long,
        limit: Int
    ): List<HealthRecordEntity>

    @Query("SELECT * FROM health_values WHERE recordLocalId IN (:recordLocalIds) ORDER BY recordLocalId, localId")
    suspend fun valuesForRecords(recordLocalIds: List<Long>): List<HealthValueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyArchive(rows: List<HealthDailyArchiveEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSleepArchive(rows: List<HealthSleepArchiveEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: HealthRetentionStateEntity)

    @Query("SELECT archivedBeforeEpochMillis FROM health_retention_state WHERE recordType = :recordType")
    suspend fun archivedBeforeEpochMillis(recordType: String): Long?

    @Query(
        """
        DELETE FROM health_upload_ack
        WHERE itemKind = 'value'
          AND localId IN (SELECT localId FROM health_values WHERE recordLocalId IN (:recordLocalIds))
        """
    )
    suspend fun deleteValueAcks(recordLocalIds: List<Long>)

    @Query(
        """
        DELETE FROM health_upload_ack
        WHERE itemKind = 'record'
          AND localId IN (:recordLocalIds)
        """
    )
    suspend fun deleteRecordAcks(recordLocalIds: List<Long>)

    @Query("DELETE FROM health_values WHERE recordLocalId IN (:recordLocalIds)")
    suspend fun deleteValues(recordLocalIds: List<Long>)

    @Query("DELETE FROM health_records WHERE localId IN (:recordLocalIds)")
    suspend fun deleteRecords(recordLocalIds: List<Long>)

    @Query(
        """
        DELETE FROM health_upload_ack
        WHERE itemKind = 'aggregate'
          AND localId IN (
              SELECT localId FROM health_aggregate_summaries
              WHERE bucketEndEpochMillis < :cutoffEpochMillis
          )
        """
    )
    suspend fun deleteAggregateAcksBefore(cutoffEpochMillis: Long)

    @Query("DELETE FROM health_aggregate_summaries WHERE bucketEndEpochMillis < :cutoffEpochMillis")
    suspend fun deleteAggregatesBefore(cutoffEpochMillis: Long): Int

    @Query("DELETE FROM health_daily_archive WHERE localDate < :cutoffDate")
    suspend fun deleteDailyArchiveBefore(cutoffDate: String): Int

    @Query(
        """
        DELETE FROM health_sleep_archive
        WHERE COALESCE(recordEndEpochMillis, recordStartEpochMillis) < :cutoffEpochMillis
        """
    )
    suspend fun deleteSleepArchiveBefore(cutoffEpochMillis: Long): Int

    @Query(
        """
        DELETE FROM health_upload_ack
        WHERE rowid IN (
            SELECT rowid FROM health_upload_ack
            WHERE itemKind IN ('record', 'value', 'aggregate')
            LIMIT :limit
        )
        """
    )
    suspend fun deleteHealthAckBatch(limit: Int): Int

    @Query(
        """
        SELECT
            localDate AS localDate,
            SUM(sampleCount) AS sampleCount,
            SUM(totalValue) / SUM(sampleCount) AS averageValue,
            MIN(minValue) AS minValue,
            MAX(maxValue) AS maxValue,
            MAX(unit) AS unit
        FROM health_daily_archive
        WHERE recordType = :recordType
          AND localDate >= :startDate
          AND localDate <= :endDate
        GROUP BY localDate
        ORDER BY localDate ASC
        """
    )
    suspend fun dailyNumericSummaries(
        recordType: String,
        startDate: String,
        endDate: String
    ): List<HealthDailyNumericSummaryRow>

    @Query(
        """
        SELECT
            localDate AS localDate,
            SUM(totalValue) AS total,
            MAX(unit) AS unit
        FROM health_daily_archive
        WHERE recordType = :recordType
          AND localDate >= :startDate
          AND localDate <= :endDate
        GROUP BY localDate
        ORDER BY localDate ASC
        """
    )
    suspend fun dailyTotals(
        recordType: String,
        startDate: String,
        endDate: String
    ): List<HealthDailyAggregateRow>

    @Query("SELECT COUNT(DISTINCT sourceRecordLocalId) FROM health_daily_archive WHERE recordType = :recordType")
    suspend fun archivedRecordCount(recordType: String): Int

    @Query("SELECT COUNT(DISTINCT sourceRecordLocalId) FROM health_sleep_archive")
    suspend fun archivedSleepRecordCount(): Int

    @Query(
        """
        SELECT COUNT(DISTINCT sourceRecordLocalId)
        FROM health_sleep_archive
        WHERE recordStartEpochMillis < :endEpochMillis
          AND COALESCE(recordEndEpochMillis, recordStartEpochMillis) >= :startEpochMillis
        """
    )
    suspend fun archivedSleepRecordCountForRange(startEpochMillis: Long, endEpochMillis: Long): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM health_sleep_archive
        WHERE recordStartEpochMillis < :endEpochMillis
          AND COALESCE(recordEndEpochMillis, recordStartEpochMillis) >= :startEpochMillis
        """
    )
    suspend fun archivedSleepRowCountForRange(startEpochMillis: Long, endEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM health_sleep_archive
        WHERE recordStartEpochMillis < :endEpochMillis
          AND COALESCE(recordEndEpochMillis, recordStartEpochMillis) >= :startEpochMillis
        ORDER BY recordStartEpochMillis DESC, sourceRecordLocalId DESC, sourceValueLocalId ASC
        LIMIT :limit
        """
    )
    suspend fun sleepRowsForRange(
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int
    ): List<HealthSleepArchiveEntity>

    @Query("DELETE FROM health_daily_archive")
    suspend fun clearDailyArchive()

    @Query("DELETE FROM health_sleep_archive")
    suspend fun clearSleepArchive()

    @Query("DELETE FROM health_retention_state")
    suspend fun clearState()
}
