package com.example.healthconnectandroid.hc.query

import android.util.Log
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthCatalogSnapshotEntity
import com.example.healthconnectandroid.data.HealthDailyAggregateRow
import com.example.healthconnectandroid.hc.HealthDataImplementationStatus
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.HealthDisplayFormatter
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class CatalogRefreshResult(
    val refreshedTypes: Set<String>,
    val failedTypes: Set<String>
)

internal class CatalogSnapshotRefresher(
    private val db: AppDb
) {
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()
    private val aggregateDao = db.healthAggregateDao()
    private val snapshotDao = db.healthCatalogSnapshotDao()
    private val retentionDao = db.healthRetentionDao()

    suspend fun refresh(recordTypes: List<String>, zoneId: ZoneId): CatalogRefreshResult {
        val refreshed = mutableSetOf<String>()
        val failed = mutableSetOf<String>()
        recordTypes.distinct().forEach { recordType ->
            coroutineContext.ensureActive()
            val descriptor = HealthDataTypeRegistry.descriptors.firstOrNull { it.key == recordType }
                ?: return@forEach
            try {
                snapshotDao.upsert(buildSnapshot(descriptor, zoneId))
                refreshed += recordType
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Throwable) {
                failed += recordType
                Log.w(TAG, "Catalog refresh failed recordType=$recordType", exception)
            }
        }
        return CatalogRefreshResult(refreshed, failed)
    }

    private suspend fun buildSnapshot(
        descriptor: HealthDataTypeDescriptor,
        zoneId: ZoneId
    ): HealthCatalogSnapshotEntity {
        val now = Instant.now()
        val recentStart = now.minus(RECENT_OVERVIEW_WINDOW)
        val summary = healthDao.catalogSummaryForType(
            recordType = descriptor.key,
            startEpochMillis = recentStart.toEpochMilli(),
            endEpochMillis = now.toEpochMilli()
        )
        val archivedRecordCount = if (descriptor.key == com.example.healthconnectandroid.hc.HealthDataTypeKeys.SLEEP_SESSION) {
            retentionDao.archivedSleepRecordCount()
        } else {
            retentionDao.archivedRecordCount(descriptor.key)
        }
        val totalRecordCount = summary.recordCount + archivedRecordCount
        val sync = syncDao.latestSummaryForType(descriptor.key)
        val today = LocalDate.now(zoneId).toString()
        val latestRow = if (summary.recordCount > 0) {
            healthDao.latestNumericRowFromLatestRecord(descriptor.key)
        } else {
            null
        }
        val latest = latestRow?.let { HealthDisplayFormatter.toReadable(it, descriptor, zoneId = zoneId) }
        val dailyTotal = if (descriptor.aggregationPreferred) {
            aggregateDao.dailyTotalForTypeOnDate(descriptor.key, today)
                ?: healthDao.numericTotalForTypeOnDate(descriptor.key, today)
        } else {
            null
        }

        return HealthCatalogSnapshotEntity(
            recordType = descriptor.key,
            recordCount = totalRecordCount,
            recentRecordCount = summary.recentRecordCount,
            lastSyncedEpochMillis = sync?.lastFinishedEpochMillis ?: summary.lastSyncedEpochMillis,
            lastSyncStatus = sync?.lastStatus,
            lastSyncError = sync?.lastErrorMessage,
            latestRecordEpochMillis = summary.latestRecordEpochMillis,
            summaryText = categorySummaryText(descriptor, latest?.primaryText, dailyTotal, totalRecordCount, summary.recentRecordCount),
            latestMetric = latest?.metric,
            latestPrimaryText = latest?.primaryText,
            latestSecondaryText = latest?.secondaryText,
            latestValue = latest?.value,
            latestSecondaryValue = latest?.value2,
            latestUnit = latest?.unit,
            latestStartEpochMillis = latest?.startTime?.toEpochMilli(),
            latestEndEpochMillis = latest?.endTime?.toEpochMilli(),
            latestLocalDate = latest?.localDate,
            latestDurationText = latest?.durationText,
            todayLocalDate = dailyTotal?.localDate ?: today,
            todayTotal = dailyTotal?.total,
            todayUnit = dailyTotal?.unit,
            zoneId = zoneId.id,
            generatedAtEpochMillis = now.toEpochMilli(),
            dirty = false
        )
    }

    private fun categorySummaryText(
        descriptor: HealthDataTypeDescriptor,
        latestPrimaryText: String?,
        todayTotal: HealthDailyAggregateRow?,
        totalRecordCount: Int,
        recentRecordCount: Int
    ): String = when {
        todayTotal != null -> "Today ${formatDouble(todayTotal.total)} ${todayTotal.unit.orEmpty()}".trim()
        latestPrimaryText != null -> "Latest $latestPrimaryText"
        descriptor.implementationStatus != HealthDataImplementationStatus.IMPLEMENTED -> "Planned - 0 records"
        totalRecordCount > 0 && recentRecordCount == 0 -> "No recent data"
        totalRecordCount > 0 -> "Local data"
        else -> "0 records locally"
    }

    private fun formatDouble(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private companion object {
        private const val TAG = "HCCatalogRefresh"
        val RECENT_OVERVIEW_WINDOW: Duration = Duration.ofDays(3)
    }
}
