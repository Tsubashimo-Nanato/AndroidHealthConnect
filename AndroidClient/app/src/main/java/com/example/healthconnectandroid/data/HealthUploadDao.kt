package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthUploadDao {
    @Query(
        """
        SELECT COUNT(*)
        FROM health_records
        WHERE localId NOT IN (
            SELECT localId FROM health_upload_ack
            WHERE serverKey = :serverKey AND itemKind = 'record'
        )
        """
    )
    suspend fun pendingRecordCount(serverKey: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM health_values
        WHERE localId NOT IN (
            SELECT localId FROM health_upload_ack
            WHERE serverKey = :serverKey AND itemKind = 'value'
        )
        """
    )
    suspend fun pendingValueCount(serverKey: String): Int

    @Query(
        """
        SELECT COUNT(*)
        FROM health_aggregate_summaries
        WHERE localId NOT IN (
            SELECT localId FROM health_upload_ack
            WHERE serverKey = :serverKey AND itemKind = 'aggregate'
        )
        """
    )
    suspend fun pendingAggregateCount(serverKey: String): Int

    @Query(
        """
        SELECT *
        FROM health_records
        WHERE localId NOT IN (
            SELECT localId FROM health_upload_ack
            WHERE serverKey = :serverKey AND itemKind = 'record'
        )
        ORDER BY updatedEpochMillis ASC, localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingRecords(serverKey: String, limit: Int): List<HealthRecordEntity>

    @Query(
        """
        SELECT *
        FROM health_values
        WHERE localId NOT IN (
            SELECT localId FROM health_upload_ack
            WHERE serverKey = :serverKey AND itemKind = 'value'
        )
        ORDER BY recordLocalId ASC, COALESCE(sequence, 2147483647) ASC, localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingValues(serverKey: String, limit: Int): List<HealthValueEntity>

    @Query(
        """
        SELECT *
        FROM health_aggregate_summaries
        WHERE localId NOT IN (
            SELECT localId FROM health_upload_ack
            WHERE serverKey = :serverKey AND itemKind = 'aggregate'
        )
        ORDER BY computedEpochMillis ASC, localId ASC
        LIMIT :limit
        """
    )
    suspend fun pendingAggregates(serverKey: String, limit: Int): List<HealthAggregateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcks(acks: List<HealthUploadAckEntity>)

    @Query("DELETE FROM health_upload_ack")
    suspend fun clearAll()
}
