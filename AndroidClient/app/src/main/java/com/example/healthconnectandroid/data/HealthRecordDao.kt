package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface HealthRecordDao {
    @Insert
    suspend fun insertRecord(record: HealthRecordEntity): Long

    @Update
    suspend fun updateRecord(record: HealthRecordEntity)

    @Insert
    suspend fun insertValues(values: List<HealthValueEntity>)

    @Query(
        """
        SELECT * FROM health_records
        WHERE recordType = :recordType AND dedupeKey IN (:dedupeKeys)
        """
    )
    suspend fun findByDedupeKeys(
        recordType: String,
        dedupeKeys: List<String>
    ): List<HealthRecordEntity>

    @Query(
        """
        SELECT * FROM health_records
        WHERE recordType = :recordType AND recordUid = :recordUid
        LIMIT 1
        """
    )
    suspend fun findByRecordUid(recordType: String, recordUid: String): HealthRecordEntity?

    @Query(
        """
        SELECT * FROM health_records
        WHERE recordType = :recordType AND recordUid IN (:recordUids)
        """
    )
    suspend fun findByRecordUids(
        recordType: String,
        recordUids: List<String>
    ): List<HealthRecordEntity>

    @Query("DELETE FROM health_values WHERE recordLocalId = :recordLocalId")
    suspend fun deleteValuesForRecord(recordLocalId: Long)

    @Query("DELETE FROM health_records WHERE localId = :recordLocalId")
    suspend fun deleteRecord(recordLocalId: Long)

    @Query(
        """
        DELETE FROM health_values
        WHERE recordLocalId IN (
            SELECT localId FROM health_records
            WHERE recordType = :recordType
              AND startEpochMillis < :endEpochMillis
              AND COALESCE(endEpochMillis, startEpochMillis) >= :startEpochMillis
        )
        """
    )
    suspend fun deleteValuesForTypeRange(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long
    )

    @Query(
        """
        DELETE FROM health_records
        WHERE recordType = :recordType
          AND startEpochMillis < :endEpochMillis
          AND COALESCE(endEpochMillis, startEpochMillis) >= :startEpochMillis
        """
    )
    suspend fun deleteRecordsForTypeRange(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long
    )

    @Query("SELECT COUNT(*) FROM health_records")
    suspend fun countRecords(): Int

    @Query(
        """
        SELECT localId, startEpochMillis, endEpochMillis
        FROM health_records
        WHERE localId > :afterLocalId
        ORDER BY localId
        LIMIT :limit
        """
    )
    suspend fun retentionScanPage(afterLocalId: Long, limit: Int): List<HealthRecordRetentionRow>

    @Query("SELECT * FROM health_values WHERE recordLocalId IN (:recordLocalIds)")
    suspend fun valuesForRecords(recordLocalIds: List<Long>): List<HealthValueEntity>

    @Query("SELECT COUNT(*) FROM health_records WHERE recordType = :recordType")
    suspend fun countRecordsForType(recordType: String): Int

    @Query(
        """
        SELECT
            :recordType AS recordType,
            COUNT(*) AS recordCount,
            COALESCE(
                SUM(
                    CASE
                        WHEN startEpochMillis < :endEpochMillis
                          AND COALESCE(endEpochMillis, startEpochMillis) >= :startEpochMillis
                        THEN 1
                        ELSE 0
                    END
                ),
                0
            ) AS recentRecordCount,
            MAX(lastReadEpochMillis) AS lastSyncedEpochMillis,
            MAX(startEpochMillis) AS latestRecordEpochMillis
        FROM health_records
        WHERE recordType = :recordType
        """
    )
    suspend fun catalogSummaryForType(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): HealthRecordSummaryRow

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        ORDER BY r.recordType ASC, r.startEpochMillis ASC,
            COALESCE(v.sequence, 2147483647) ASC,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) ASC,
            v.metric ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun exportRowsPaged(limit: Int, offset: Int): List<HealthCsvRow>

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.startEpochMillis < :endEpochMillis
          AND COALESCE(r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis
        ORDER BY r.recordType ASC, r.startEpochMillis ASC,
            COALESCE(v.sequence, 2147483647) ASC,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) ASC,
            v.metric ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun exportRowsForRangePaged(
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int,
        offset: Int
    ): List<HealthCsvRow>

    @Query(
        """
        SELECT DISTINCT recordType
        FROM health_records
        ORDER BY recordType ASC
        """
    )
    suspend fun exportRecordTypes(): List<String>

    @Query(
        """
        SELECT DISTINCT recordType
        FROM health_records
        WHERE startEpochMillis < :endEpochMillis
          AND COALESCE(endEpochMillis, startEpochMillis) >= :startEpochMillis
        ORDER BY recordType ASC
        """
    )
    suspend fun exportRecordTypesForRange(
        startEpochMillis: Long,
        endEpochMillis: Long
    ): List<String>

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
        ORDER BY r.startEpochMillis ASC,
            COALESCE(v.sequence, 2147483647) ASC,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) ASC,
            v.metric ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun exportRowsForTypePaged(
        recordType: String,
        limit: Int,
        offset: Int
    ): List<HealthCsvRow>

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
          AND r.startEpochMillis < :endEpochMillis
          AND COALESCE(r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis
        ORDER BY r.startEpochMillis ASC,
            COALESCE(v.sequence, 2147483647) ASC,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) ASC,
            v.metric ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun exportRowsForTypeRangePaged(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int,
        offset: Int
    ): List<HealthCsvRow>

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
          AND COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) < :endEpochMillis
          AND COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis, r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis
        ORDER BY COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) DESC,
            COALESCE(v.sequence, 2147483647) ASC,
            v.metric ASC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun inspectorRowsForTypeRange(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int,
        offset: Int = 0
    ): List<HealthCsvRow>

    @Query(
        """
        SELECT COUNT(*)
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
          AND COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) < :endEpochMillis
          AND COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis, r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis
        """
    )
    suspend fun countInspectorRowsForTypeRange(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): Int

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            r.metadataJson AS metadataJson,
            r.rawJson AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.localId = :localRecordId
        ORDER BY COALESCE(v.sequence, 2147483647) ASC,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) ASC,
            v.metric ASC
        """
    )
    suspend fun inspectorRowsForRecord(localRecordId: Long): List<HealthCsvRow>

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
          AND COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) < :endEpochMillis
          AND COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis, r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis
        ORDER BY COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) DESC,
            COALESCE(v.sequence, 2147483647) ASC,
            v.metric ASC
        LIMIT :limit
        """
    )
    suspend fun inspectorDisplayRowsForTypeRange(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int
    ): List<HealthCsvRow>

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
          AND COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) < :endEpochMillis
          AND COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis, r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis
          AND v.numericValue IS NOT NULL
        ORDER BY COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) DESC,
            COALESCE(v.sequence, 2147483647) ASC,
            v.metric ASC
        LIMIT :limit
        """
    )
    suspend fun inspectorNumericRowsForTypeRange(
        recordType: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int
    ): List<HealthCsvRow>

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_values v
        INNER JOIN health_records r ON r.localId = v.recordLocalId
        WHERE r.recordType = :recordType
          AND v.metric = :metric
          AND v.numericValue IS NOT NULL
          AND v.startEpochMillis IS NOT NULL
          AND v.startEpochMillis >= :startEpochMillis
          AND v.startEpochMillis < :endEpochMillis
          AND COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis, r.endEpochMillis, r.startEpochMillis) >= :startEpochMillis
          AND (
              :afterEpochMillis IS NULL
              OR v.startEpochMillis > :afterEpochMillis
              OR (v.startEpochMillis = :afterEpochMillis AND v.localId > :afterLocalValueId)
          )
        ORDER BY v.startEpochMillis ASC,
            v.localId ASC
        LIMIT :limit
        """
    )
    suspend fun inspectorNumericRowsForMetricEpochRangeAscAfter(
        recordType: String,
        metric: String,
        startEpochMillis: Long,
        endEpochMillis: Long,
        limit: Int,
        afterEpochMillis: Long?,
        afterLocalValueId: Long
    ): List<HealthCsvRow>

    @Query(
        """
        SELECT
            r.localId AS localRecordId,
            v.localId AS localValueId,
            v.valueKey AS valueKey,
            r.recordType AS recordType,
            r.recordKind AS recordKind,
            r.recordUid AS healthConnectUid,
            r.dedupeKey AS dedupeKey,
            r.sourcePackage AS sourcePackage,
            r.startEpochMillis AS recordStartEpochMillis,
            r.endEpochMillis AS recordEndEpochMillis,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis) AS valueStartEpochMillis,
            COALESCE(v.endEpochMillis, v.startEpochMillis, v.sampleEpochMillis) AS valueEndEpochMillis,
            COALESCE(v.localDate, r.localDate) AS localDate,
            r.startZoneOffsetSeconds AS zoneOffsetSeconds,
            v.metric AS metric,
            v.numericValue AS numericValue,
            v.secondaryNumericValue AS secondaryNumericValue,
            v.unit AS unit,
            COALESCE(v.label, v.category) AS categoryOrStage,
            v.label AS label,
            v.valueText AS textValue,
            v.valueJson AS jsonValue,
            NULL AS metadataJson,
            NULL AS rawJson
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
          AND v.numericValue IS NOT NULL
        ORDER BY r.startEpochMillis DESC,
            COALESCE(v.startEpochMillis, v.sampleEpochMillis, r.startEpochMillis) DESC,
            COALESCE(v.sequence, 2147483647) ASC
        LIMIT 1
        """
    )
    suspend fun latestNumericRowFromLatestRecord(recordType: String): HealthCsvRow?

    @Query(
        """
        SELECT
            COALESCE(v.localDate, r.localDate) AS localDate,
            SUM(v.numericValue) AS total,
            MAX(v.unit) AS unit
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
          AND v.numericValue IS NOT NULL
          AND COALESCE(v.localDate, r.localDate) = :localDate
        GROUP BY COALESCE(v.localDate, r.localDate)
        """
    )
    suspend fun numericTotalForTypeOnDate(
        recordType: String,
        localDate: String
    ): HealthDailyAggregateRow?

    @Query(
        """
        SELECT
            COALESCE(v.localDate, r.localDate) AS localDate,
            SUM(v.numericValue) AS total,
            MAX(v.unit) AS unit
        FROM health_records r
        INNER JOIN health_values v ON v.recordLocalId = r.localId
        WHERE r.recordType = :recordType
          AND v.numericValue IS NOT NULL
          AND COALESCE(v.localDate, r.localDate) >= :startDate
          AND COALESCE(v.localDate, r.localDate) <= :endDate
        GROUP BY COALESCE(v.localDate, r.localDate)
        ORDER BY COALESCE(v.localDate, r.localDate) ASC
        """
    )
    suspend fun dailyTotalsForType(
        recordType: String,
        startDate: String,
        endDate: String
    ): List<HealthDailyAggregateRow>

    @Query(
        """
        SELECT
            :localDate AS localDate,
            COUNT(*) AS sampleCount,
            AVG(numericValue) AS averageValue,
            MIN(numericValue) AS minValue,
            MAX(numericValue) AS maxValue,
            MAX(unit) AS unit
        FROM health_values
        WHERE metric = :metric
          AND numericValue IS NOT NULL
          AND startEpochMillis IS NOT NULL
          AND startEpochMillis >= :startEpochMillis
          AND startEpochMillis < :endEpochMillis
        """
    )
    suspend fun numericSummaryForMetricEpochRange(
        metric: String,
        localDate: String,
        startEpochMillis: Long,
        endEpochMillis: Long
    ): HealthDailyNumericSummaryRow

    @Query(
        """
        UPDATE health_records
        SET exportStatus = 'exported',
            exportedEpochMillis = :exportedEpochMillis
        WHERE localId IN (
            SELECT DISTINCT recordLocalId FROM health_values
        )
        """
    )
    suspend fun markAllExported(exportedEpochMillis: Long)

    @Query(
        """
        UPDATE health_records
        SET exportStatus = 'exported',
            exportedEpochMillis = :exportedEpochMillis
        WHERE startEpochMillis < :endEpochMillis
          AND COALESCE(endEpochMillis, startEpochMillis) >= :startEpochMillis
        """
    )
    suspend fun markRangeExported(
        startEpochMillis: Long,
        endEpochMillis: Long,
        exportedEpochMillis: Long
    )

    @Query(
        """
        UPDATE health_records
        SET exportStatus = 'exported',
            exportedEpochMillis = :exportedEpochMillis
        WHERE recordType = :recordType
        """
    )
    suspend fun markTypeExported(
        recordType: String,
        exportedEpochMillis: Long
    )
}
