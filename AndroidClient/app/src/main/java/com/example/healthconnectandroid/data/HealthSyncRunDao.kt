package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HealthSyncRunDao {
    @Insert
    suspend fun insert(run: HealthSyncRunEntity): Long

    @Query(
        """
        SELECT
            recordType AS recordType,
            finishedEpochMillis AS lastFinishedEpochMillis,
            status AS lastStatus,
            errorMessage AS lastErrorMessage
        FROM health_sync_runs
        WHERE finishedEpochMillis IS NOT NULL
        ORDER BY finishedEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun latestOverallSummary(): HealthSyncSummaryRow?

    @Query(
        """
        SELECT
            recordType AS recordType,
            finishedEpochMillis AS lastFinishedEpochMillis,
            status AS lastStatus,
            errorMessage AS lastErrorMessage
        FROM health_sync_runs
        WHERE recordType = :recordType
          AND finishedEpochMillis IS NOT NULL
        ORDER BY finishedEpochMillis DESC
        LIMIT 1
        """
    )
    suspend fun latestSummaryForType(recordType: String): HealthSyncSummaryRow?

    @Query("DELETE FROM health_sync_runs")
    suspend fun clearAll()

    @Query("DELETE FROM health_sync_runs WHERE COALESCE(finishedEpochMillis, startedEpochMillis) < :cutoffEpochMillis")
    suspend fun deleteBefore(cutoffEpochMillis: Long): Int
}
