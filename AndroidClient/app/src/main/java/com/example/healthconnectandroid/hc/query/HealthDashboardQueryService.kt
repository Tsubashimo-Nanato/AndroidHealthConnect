package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.LocalHealthStatus
import java.time.Instant

class HealthDashboardQueryService(
    private val db: AppDb
) {
    private val snapshotDao = db.healthCatalogSnapshotDao()
    private val syncDao = db.healthSyncRunDao()

    suspend fun localHealthStatus(): LocalHealthStatus {
        // The catalog has one compact row per type. Reading it keeps navigation independent of raw history size.
        val summary = snapshotDao.dashboardSummary()
        val latestSync = syncDao.latestOverallSummary()
        val latestLocalRead = summary.latestLocalReadEpochMillis
            ?.takeIf { it > 0L }
        val lastSyncEpoch = latestSync?.lastFinishedEpochMillis ?: latestLocalRead
        return LocalHealthStatus(
            localRecordCount = summary.localRecordCount,
            localDataTypeCount = summary.localDataTypeCount,
            lastSync = lastSyncEpoch?.let(Instant::ofEpochMilli),
            lastSyncStatus = latestSync?.lastStatus
        )
    }
}
