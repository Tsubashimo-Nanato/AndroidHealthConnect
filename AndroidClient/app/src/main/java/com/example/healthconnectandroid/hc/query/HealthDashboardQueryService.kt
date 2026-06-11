package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.LocalHealthStatus
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HealthDashboardQueryService(
    private val db: AppDb
) {
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()

    suspend fun localHealthStatus(): LocalHealthStatus = withContext(Dispatchers.Default) {
        val summary = healthDao.dashboardSummary()
        val latestSync = syncDao.latestOverallSummary()
        val latestLocalRead = summary.latestLocalReadEpochMillis
            ?.takeIf { it > 0L }
        val lastSyncEpoch = latestSync?.lastFinishedEpochMillis ?: latestLocalRead
        LocalHealthStatus(
            localRecordCount = summary.localRecordCount,
            localDataTypeCount = summary.localDataTypeCount,
            lastSync = lastSyncEpoch?.let(Instant::ofEpochMilli),
            lastSyncStatus = latestSync?.lastStatus
        )
    }
}
