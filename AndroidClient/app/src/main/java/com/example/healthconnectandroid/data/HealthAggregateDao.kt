package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthAggregateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<HealthAggregateEntity>)

    @Query(
        """
        DELETE FROM health_aggregate_summaries
        WHERE recordType = :recordType
          AND source = :source
          AND localDate >= :startDate
          AND localDate <= :endDate
        """
    )
    suspend fun deleteForTypeDateRange(
        recordType: String,
        source: String,
        startDate: String,
        endDate: String
    )

    @Query("DELETE FROM health_aggregate_summaries")
    suspend fun clearAll()

    @Query(
        """
        SELECT
            localDate AS localDate,
            SUM(value) AS total,
            MAX(unit) AS unit
        FROM health_aggregate_summaries
        WHERE recordType = :recordType
          AND source = :source
          AND bucketPeriod = 'day'
          AND localDate = :localDate
        GROUP BY localDate
        """
    )
    suspend fun dailyTotalForTypeOnDate(
        recordType: String,
        localDate: String,
        source: String = SOURCE_HEALTH_CONNECT_AGGREGATE
    ): HealthDailyAggregateRow?

    @Query(
        """
        SELECT
            recordType AS recordType,
            localDate AS localDate,
            SUM(value) AS total,
            MAX(unit) AS unit
        FROM health_aggregate_summaries
        WHERE source = :source
          AND bucketPeriod = 'day'
          AND localDate = :localDate
        GROUP BY recordType, localDate
        """
    )
    suspend fun dailyTotalsForTypesOnDate(
        localDate: String,
        source: String = SOURCE_HEALTH_CONNECT_AGGREGATE
    ): List<HealthDailyAggregateByTypeRow>

    @Query(
        """
        SELECT
            localDate AS localDate,
            SUM(value) AS total,
            MAX(unit) AS unit
        FROM health_aggregate_summaries
        WHERE recordType = :recordType
          AND source = :source
          AND bucketPeriod = 'day'
          AND localDate >= :startDate
          AND localDate <= :endDate
        GROUP BY localDate
        ORDER BY localDate ASC
        """
    )
    suspend fun dailyTotalsForType(
        recordType: String,
        startDate: String,
        endDate: String,
        source: String = SOURCE_HEALTH_CONNECT_AGGREGATE
    ): List<HealthDailyAggregateRow>

    @Query(
        """
        SELECT
            localId AS localId,
            recordType AS recordType,
            metric AS metric,
            bucketPeriod AS bucketPeriod,
            bucketStartEpochMillis AS bucketStartEpochMillis,
            bucketEndEpochMillis AS bucketEndEpochMillis,
            localDate AS localDate,
            timezoneId AS timezoneId,
            value AS value,
            unit AS unit,
            source AS source,
            computedEpochMillis AS computedEpochMillis,
            requestedStartEpochMillis AS requestedStartEpochMillis,
            requestedEndEpochMillis AS requestedEndEpochMillis,
            rawJson AS rawJson
        FROM health_aggregate_summaries
        ORDER BY recordType ASC, bucketStartEpochMillis ASC, metric ASC
        """
    )
    suspend fun exportRows(): List<HealthAggregateCsvRow>

    @Query(
        """
        SELECT DISTINCT recordType
        FROM health_aggregate_summaries
        ORDER BY recordType ASC
        """
    )
    suspend fun exportRecordTypes(): List<String>

    @Query(
        """
        SELECT
            localId AS localId,
            recordType AS recordType,
            metric AS metric,
            bucketPeriod AS bucketPeriod,
            bucketStartEpochMillis AS bucketStartEpochMillis,
            bucketEndEpochMillis AS bucketEndEpochMillis,
            localDate AS localDate,
            timezoneId AS timezoneId,
            value AS value,
            unit AS unit,
            source AS source,
            computedEpochMillis AS computedEpochMillis,
            requestedStartEpochMillis AS requestedStartEpochMillis,
            requestedEndEpochMillis AS requestedEndEpochMillis,
            rawJson AS rawJson
        FROM health_aggregate_summaries
        ORDER BY recordType ASC, bucketStartEpochMillis ASC, metric ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun exportRowsPaged(limit: Int, offset: Int): List<HealthAggregateCsvRow>

    @Query(
        """
        SELECT
            localId AS localId,
            recordType AS recordType,
            metric AS metric,
            bucketPeriod AS bucketPeriod,
            bucketStartEpochMillis AS bucketStartEpochMillis,
            bucketEndEpochMillis AS bucketEndEpochMillis,
            localDate AS localDate,
            timezoneId AS timezoneId,
            value AS value,
            unit AS unit,
            source AS source,
            computedEpochMillis AS computedEpochMillis,
            requestedStartEpochMillis AS requestedStartEpochMillis,
            requestedEndEpochMillis AS requestedEndEpochMillis,
            rawJson AS rawJson
        FROM health_aggregate_summaries
        WHERE recordType = :recordType
        ORDER BY bucketStartEpochMillis ASC, metric ASC
        """
    )
    suspend fun exportRowsForType(recordType: String): List<HealthAggregateCsvRow>

    @Query(
        """
        SELECT
            localId AS localId,
            recordType AS recordType,
            metric AS metric,
            bucketPeriod AS bucketPeriod,
            bucketStartEpochMillis AS bucketStartEpochMillis,
            bucketEndEpochMillis AS bucketEndEpochMillis,
            localDate AS localDate,
            timezoneId AS timezoneId,
            value AS value,
            unit AS unit,
            source AS source,
            computedEpochMillis AS computedEpochMillis,
            requestedStartEpochMillis AS requestedStartEpochMillis,
            requestedEndEpochMillis AS requestedEndEpochMillis,
            rawJson AS rawJson
        FROM health_aggregate_summaries
        WHERE recordType = :recordType
        ORDER BY bucketStartEpochMillis ASC, metric ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun exportRowsForTypePaged(
        recordType: String,
        limit: Int,
        offset: Int
    ): List<HealthAggregateCsvRow>

    companion object {
        const val SOURCE_HEALTH_CONNECT_AGGREGATE = "health_connect_aggregate"
    }
}
