package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HealthSyncCoverageDao {
    @Insert
    suspend fun insert(coverage: HealthSyncCoverageEntity): Long

    @Query(
        """
        SELECT * FROM health_sync_coverage
        WHERE recordType = :recordType
          AND status = 'success'
          AND coveredStartEpochMillis < :endEpochMillis
          AND coveredEndEpochMillis > :startEpochMillis
        ORDER BY coveredStartEpochMillis ASC
        """
    )
    suspend fun successfulCoverageForTypeRange(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): List<HealthSyncCoverageEntity>

    @Query("DELETE FROM health_sync_coverage")
    suspend fun clearAll()

    @Query("DELETE FROM health_sync_coverage WHERE coveredEndEpochMillis < :cutoffEpochMillis")
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int
}
