package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthCsvRow
import com.example.healthconnectandroid.hc.HealthDataImplementationStatus
import com.example.healthconnectandroid.hc.HealthDataPermissionStatus
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.HealthDisplayFormatter
import com.example.healthconnectandroid.hc.InspectorCategorySummary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HealthDataCatalogQueryService(
    private val db: AppDb
) {
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()
    private val aggregateDao = db.healthAggregateDao()

    suspend fun inspectorCategories(
        grantedPermissions: Set<String>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<InspectorCategorySummary> = withContext(Dispatchers.Default) {
        val overviewEnd = Instant.now()
        val overviewStart = overviewEnd.minus(RECENT_OVERVIEW_WINDOW)
        val allSummariesByType = healthDao
            .summaryRows()
            .associateBy { it.recordType }
        val recentSummariesByType = healthDao
            .summaryRowsForRange(
                startEpochMillis = overviewStart.toEpochMilli(),
                endEpochMillis = overviewEnd.toEpochMilli()
            )
            .associateBy { it.recordType }
        val syncSummariesByType = syncDao.latestSummaries().associateBy { it.recordType }
        val today = LocalDate.now(zoneId).toString()
        val latestValuesByType = healthDao
            .latestNumericRows()
            .distinctBy { it.recordType }
            .associateBy { it.recordType }
        val aggregateTotalsByType = aggregateDao
            .dailyTotalsForTypesOnDate(today)
            .associateBy { it.recordType }
        val localTotalsByType = healthDao
            .numericTotalsForTypesOnDate(today)
            .associateBy { it.recordType }
        HealthDataTypeRegistry.descriptors.map { descriptor ->
            val summary = allSummariesByType[descriptor.key]
            val recentSummary = recentSummariesByType[descriptor.key]
            val syncSummary = syncSummariesByType[descriptor.key]
            val latestValue = latestValuesByType[descriptor.key]
            val latestReadable = latestValue?.let { HealthDisplayFormatter.toReadable(it, descriptor) }
            val dailyTotal = if (descriptor.aggregationPreferred) {
                aggregateTotalsByType[descriptor.key]?.toDailyAggregateRow()
                    ?: localTotalsByType[descriptor.key]?.toDailyAggregateRow()
            } else {
                null
            }
            val permissionStatus = descriptor.permissionStatus(grantedPermissions)
            InspectorCategorySummary(
                descriptor = descriptor,
                permissionStatus = permissionStatus,
                requiredPermission = descriptor.requiredReadPermission,
                permissionGranted = permissionStatus == HealthDataPermissionStatus.GRANTED,
                recordCount = summary?.recordCount ?: 0,
                recentRecordCount = recentSummary?.recordCount ?: 0,
                lastSynced = syncSummary?.lastFinishedEpochMillis?.let(Instant::ofEpochMilli)
                    ?: summary?.lastSyncedEpochMillis?.let(Instant::ofEpochMilli),
                lastSyncStatus = syncSummary?.lastStatus,
                lastSyncError = syncSummary?.lastErrorMessage,
                latestRecordTime = summary?.latestRecordEpochMillis?.let(Instant::ofEpochMilli),
                summaryText = categorySummaryText(
                    descriptor = descriptor,
                    latestValue = latestValue,
                    todayTotal = dailyTotal,
                    totalRecordCount = summary?.recordCount ?: 0,
                    recentRecordCount = recentSummary?.recordCount ?: 0
                ),
                latestReadable = latestReadable,
                todayTotal = dailyTotal
            )
        }
    }


    private fun categorySummaryText(
        descriptor: HealthDataTypeDescriptor,
        latestValue: HealthCsvRow?,
        todayTotal: com.example.healthconnectandroid.data.HealthDailyAggregateRow?,
        totalRecordCount: Int,
        recentRecordCount: Int
    ): String {
        if (todayTotal != null) {
            return "Today ${formatDouble(todayTotal.total).orEmpty()} ${todayTotal.unit.orEmpty()}".trim()
        }
        if (latestValue != null) {
            return "Latest ${HealthDisplayFormatter.toReadable(latestValue, descriptor).primaryText}"
        }
        return when {
            descriptor.implementationStatus != HealthDataImplementationStatus.IMPLEMENTED -> "Planned - 0 records"
            totalRecordCount > 0 && recentRecordCount == 0 -> "No recent data"
            totalRecordCount > 0 -> "Local data"
            else -> "0 records locally"
        }
    }

    private fun formatDouble(value: Double?): String? =
        value?.let {
            if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
        }

    private fun com.example.healthconnectandroid.data.HealthDailyAggregateByTypeRow.toDailyAggregateRow() =
        com.example.healthconnectandroid.data.HealthDailyAggregateRow(
            localDate = localDate,
            total = total,
            unit = unit
        )

    private companion object {
        val RECENT_OVERVIEW_WINDOW: Duration = Duration.ofDays(3)
    }
}
