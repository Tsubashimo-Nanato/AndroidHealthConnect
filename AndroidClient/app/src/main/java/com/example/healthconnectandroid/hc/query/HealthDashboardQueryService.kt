package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.hc.DemoStatus
import java.time.Instant

class HealthDashboardQueryService(
    private val db: AppDb
) {
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()

    suspend fun demoStatus(): DemoStatus {
        val summaries = healthDao.summaryRows()
        val latestSync = syncDao.latestSummaries()
            .maxByOrNull { it.lastFinishedEpochMillis ?: 0L }
        val latestLocalRead = summaries.maxOfOrNull { it.lastSyncedEpochMillis ?: 0L }
            ?.takeIf { it > 0L }
        val lastSyncEpoch = latestSync?.lastFinishedEpochMillis ?: latestLocalRead
        return DemoStatus(
            localRecordCount = summaries.sumOf { it.recordCount },
            localDataTypeCount = summaries.size,
            lastSync = lastSyncEpoch?.let(Instant::ofEpochMilli),
            lastSyncStatus = latestSync?.lastStatus
        )
    }


}
