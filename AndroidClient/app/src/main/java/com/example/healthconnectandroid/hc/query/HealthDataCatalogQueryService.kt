package com.example.healthconnectandroid.hc.query

import android.content.Context
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthCatalogSnapshotEntity
import com.example.healthconnectandroid.data.HealthDailyAggregateRow
import com.example.healthconnectandroid.hc.HealthDataPermissionStatus
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.InspectorCategorySummary
import com.example.healthconnectandroid.hc.ReadableHealthRecord
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class CatalogSnapshotView(
    val categories: List<InspectorCategorySummary>,
    val refreshing: Boolean,
    val readyTypes: Int,
    val totalTypes: Int
)

class HealthDataCatalogQueryService(
    context: Context,
    private val db: AppDb
) {
    private val appContext = context.applicationContext
    private val snapshotDao = db.healthCatalogSnapshotDao()
    private val refresher = CatalogSnapshotRefresher(db)
    @Volatile private var memorySnapshots: List<HealthCatalogSnapshotEntity> = emptyList()

    fun observeCategories(
        grantedPermissions: Set<String>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Flow<CatalogSnapshotView> =
        snapshotDao.observeAll()
            .map { snapshots ->
                memorySnapshots = snapshots
                snapshotView(snapshots, grantedPermissions, zoneId)
            }
            .distinctUntilChanged()

    fun cachedView(
        grantedPermissions: Set<String>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CatalogSnapshotView? =
        memorySnapshots.takeIf { it.isNotEmpty() }
            ?.let { snapshotView(it, grantedPermissions, zoneId) }

    suspend fun warmCache() {
        memorySnapshots = snapshotDao.all()
    }

    suspend fun prepareCatalog(
        zoneId: ZoneId = ZoneId.systemDefault(),
        forceRefresh: Boolean = false
    ) {
        if (forceRefresh) snapshotDao.markAllDirty()
        var snapshots = snapshotDao.all()
        if (snapshots.isEmpty()) {
            refresher.refresh(PRIORITY_RECORD_TYPES, zoneId)
            snapshots = snapshotDao.all()
        }
        val pendingTypes = CatalogRefreshPolicy.recordTypesToRefresh(
            snapshots = snapshots,
            recordTypes = HealthDataTypeRegistry.descriptors.map { it.key },
            localDate = LocalDate.now(zoneId).toString(),
            zoneId = zoneId.id,
            now = Instant.now()
        )
        if (pendingTypes.isNotEmpty()) CatalogRefreshWorker.enqueue(appContext, zoneId)
    }

    suspend fun invalidate(recordTypes: Set<String>? = null, zoneId: ZoneId = ZoneId.systemDefault()) {
        if (recordTypes != null && recordTypes.isEmpty()) return
        if (recordTypes == null) {
            snapshotDao.markAllDirty()
        } else {
            snapshotDao.markDirty(recordTypes)
        }
        CatalogRefreshWorker.enqueue(appContext, zoneId)
    }

    private fun snapshotView(
        snapshots: List<HealthCatalogSnapshotEntity>,
        grantedPermissions: Set<String>,
        zoneId: ZoneId
    ): CatalogSnapshotView {
        val allTypes = HealthDataTypeRegistry.descriptors.map { it.key }
        val today = LocalDate.now(zoneId).toString()
        val refreshTypes = CatalogRefreshPolicy.recordTypesToRefresh(
            snapshots = snapshots,
            recordTypes = allTypes,
            localDate = today,
            zoneId = zoneId.id,
            now = Instant.now()
        )
        return CatalogSnapshotView(
            categories = snapshots.mapNotNull { snapshot ->
                val descriptor = HealthDataTypeRegistry.descriptors.firstOrNull { it.key == snapshot.recordType }
                    ?: return@mapNotNull null
                val permissionStatus = descriptor.permissionStatus(grantedPermissions)
                InspectorCategorySummary(
                    descriptor = descriptor,
                    permissionStatus = permissionStatus,
                    requiredPermission = descriptor.requiredReadPermission,
                    permissionGranted = permissionStatus == HealthDataPermissionStatus.GRANTED,
                    recordCount = snapshot.recordCount,
                    recentRecordCount = snapshot.recentRecordCount,
                    lastSynced = snapshot.lastSyncedEpochMillis?.let(Instant::ofEpochMilli),
                    lastSyncStatus = snapshot.lastSyncStatus,
                    lastSyncError = snapshot.lastSyncError,
                    latestRecordTime = snapshot.latestRecordEpochMillis?.let(Instant::ofEpochMilli),
                    summaryText = snapshot.summaryText,
                    latestReadable = snapshot.toReadable(descriptor.displayName, descriptor.visualizationType),
                    todayTotal = snapshot.todayTotal(today)
                )
            },
            refreshing = refreshTypes.isNotEmpty(),
            readyTypes = snapshots.size,
            totalTypes = allTypes.size
        )
    }

    private fun HealthCatalogSnapshotEntity.toReadable(
        displayName: String,
        visualizationType: com.example.healthconnectandroid.hc.VisualizationType
    ): ReadableHealthRecord? {
        val metric = latestMetric ?: return null
        return ReadableHealthRecord(
            rowKey = "snapshot:$recordType",
            localRecordId = 0,
            localValueId = 0,
            recordTypeKey = recordType,
            metric = metric,
            displayName = displayName,
            primaryText = latestPrimaryText.orEmpty(),
            secondaryText = latestSecondaryText.orEmpty(),
            value = latestValue,
            value2 = latestSecondaryValue,
            unit = latestUnit,
            startTime = latestStartEpochMillis?.let(Instant::ofEpochMilli),
            endTime = latestEndEpochMillis?.let(Instant::ofEpochMilli),
            localDate = latestLocalDate,
            durationText = latestDurationText,
            sourceText = null,
            metadataText = null,
            rawDetailsText = null,
            detailFields = emptyList(),
            visualizationType = visualizationType
        )
    }

    private fun HealthCatalogSnapshotEntity.todayTotal(today: String): HealthDailyAggregateRow? {
        val total = todayTotal ?: return null
        if (todayLocalDate != today) return null
        return HealthDailyAggregateRow(today, total, todayUnit)
    }

    private companion object {
        val PRIORITY_RECORD_TYPES = listOf(
            HealthDataTypeKeys.HEART_RATE,
            HealthDataTypeKeys.SLEEP_SESSION,
            HealthDataTypeKeys.STEPS,
            HealthDataTypeKeys.WEIGHT,
            HealthDataTypeKeys.OXYGEN_SATURATION
        )
    }
}
