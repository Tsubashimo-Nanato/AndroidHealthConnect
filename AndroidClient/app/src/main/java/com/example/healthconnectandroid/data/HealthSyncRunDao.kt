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
            latest.recordType AS recordType,
            latest.finishedEpochMillis AS lastFinishedEpochMillis,
            latest.status AS lastStatus,
            latest.errorMessage AS lastErrorMessage
        FROM health_sync_runs latest
        INNER JOIN (
            SELECT recordType, MAX(finishedEpochMillis) AS maxFinishedEpochMillis
            FROM health_sync_runs
            GROUP BY recordType
        ) grouped
            ON latest.recordType = grouped.recordType
            AND latest.finishedEpochMillis = grouped.maxFinishedEpochMillis
        """
    )
    suspend fun latestSummaries(): List<HealthSyncSummaryRow>

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

    @Query(
        """
        SELECT * FROM health_sync_runs
        WHERE recordType = :recordType
        ORDER BY startedEpochMillis DESC
        LIMIT :limit
        """
    )
    suspend fun latestRunsForType(
        recordType: String,
        limit: Int = 20
    ): List<HealthSyncRunEntity>

    @Query(
        """
        SELECT MAX(finishedEpochMillis) FROM health_sync_runs
        WHERE recordType = :recordType
          AND status = 'success'
          AND finishedEpochMillis IS NOT NULL
        """
    )
    suspend fun latestSuccessfulFinishedEpochMillis(recordType: String): Long?

    @Query("DELETE FROM health_sync_runs")
    suspend fun clearAll()
}
