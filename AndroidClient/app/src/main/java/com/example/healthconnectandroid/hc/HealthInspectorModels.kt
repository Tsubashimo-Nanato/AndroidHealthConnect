package com.example.healthconnectandroid.hc

import com.example.healthconnectandroid.data.HealthCsvRow
import com.example.healthconnectandroid.data.HealthDailyAggregateRow
import com.example.healthconnectandroid.data.HealthDailyNumericSummaryRow
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class InspectorTimeRange(
    val label: String,
    val duration: Duration
) {
    TODAY("Today", Duration.ofDays(1)),
    LAST_24_HOURS("24h", Duration.ofHours(24)),
    LAST_7_DAYS("7d", Duration.ofDays(7)),
    WEEKLY("Week", Duration.ofDays(7)),
    LAST_30_DAYS("30d", Duration.ofDays(30)),
    MONTHLY("Month", Duration.ofDays(30)),
    LAST_90_DAYS("90d", Duration.ofDays(90));

    fun startBefore(end: Instant, zoneId: ZoneId = ZoneId.systemDefault()): Instant =
        if (this == TODAY) {
            LocalDate.now(zoneId).atStartOfDay(zoneId).toInstant()
        } else if (this == WEEKLY) {
            end.minus(duration)
        } else if (this == MONTHLY) {
            end.atZone(zoneId)
                .toLocalDate()
                .withDayOfMonth(1)
                .atStartOfDay(zoneId)
                .toInstant()
        } else {
            end.minus(duration)
        }

    fun startLocalDate(today: LocalDate = LocalDate.now()): LocalDate =
        when (this) {
            TODAY -> today
            LAST_24_HOURS -> today.minusDays(1)
            LAST_7_DAYS -> today.minusDays(6)
            WEEKLY -> today.minusDays(6)
            LAST_30_DAYS -> today.minusDays(29)
            MONTHLY -> today.withDayOfMonth(1)
            LAST_90_DAYS -> today.minusDays(89)
        }
}

data class InspectorCategorySummary(
    val descriptor: HealthDataTypeDescriptor,
    val permissionStatus: HealthDataPermissionStatus,
    val requiredPermission: String?,
    val permissionGranted: Boolean,
    val recordCount: Int,
    val recentRecordCount: Int = 0,
    val lastSynced: Instant?,
    val lastSyncStatus: String?,
    val lastSyncError: String?,
    val latestRecordTime: Instant?,
    val summaryText: String,
    val latestReadable: ReadableHealthRecord? = null,
    val todayTotal: HealthDailyAggregateRow? = null
)

data class InspectorDetailData(
    val descriptor: HealthDataTypeDescriptor,
    val range: InspectorTimeRange,
    val start: Instant,
    val end: Instant,
    val totalLocalRecordsForType: Int,
    val lastSynced: Instant?,
    val lastSyncStatus: String?,
    val lastSyncError: String?,
    val rows: List<HealthCsvRow>,
    val chartPoints: List<InspectorChartPoint>,
    val dailyTotals: List<HealthDailyAggregateRow>,
    val dailyNumericSummaries: List<HealthDailyNumericSummaryRow> = emptyList(),
    val dailyTotalsSource: String?,
    val weeklyTotals: List<InspectorPeriodTotal>,
    val readableRows: List<ReadableHealthRecord>,
    val recordListTotalCount: Int,
    val tableRows: List<HealthCsvRow>,
    val tableRowsLimited: Boolean,
    val chartPointsLimited: Boolean,
    val restingHeartRateEstimate: RestingHeartRateEstimate? = null
)

data class RecordListItem(
    val rowKey: String,
    val localRecordId: Long,
    val recordType: String,
    val primaryText: String,
    val secondaryText: String,
    val timestamp: Instant?,
    val compactValue: String,
    val hasDetails: Boolean
)

data class RecordListPage(
    val items: List<RecordListItem>,
    val totalCount: Int,
    val nextOffset: Int?,
    val limit: Int
)

data class RecordFullDetails(
    val localRecordId: Long,
    val recordType: String,
    val readableFields: List<ReadableDetailField>,
    val valueRows: List<ReadableHealthRecord>,
    val source: String?,
    val metadata: String?,
    val rawDetails: String?
)

data class LocalHealthStatus(
    val localRecordCount: Int,
    val localDataTypeCount: Int,
    val lastSync: Instant?,
    val lastSyncStatus: String?
)

data class InspectorChartPoint(
    val epochMillis: Long,
    val value: Double,
    val value2: Double?,
    val label: String?,
    val unit: String?,
    val sourceText: String? = null,
    val primaryText: String? = null,
    val secondaryText: String? = null,
    val rawDetailsText: String? = null
)

data class InspectorPeriodTotal(
    val label: String,
    val total: Double,
    val unit: String?
)
