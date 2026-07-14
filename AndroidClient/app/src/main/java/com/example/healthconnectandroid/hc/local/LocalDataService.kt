package com.example.healthconnectandroid.hc.local

import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthRetentionStateEntity
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

class LocalDataService(
    private val db: AppDb
) {
    private val dao = db.heartRateDao()
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()
    private val coverageDao = db.healthSyncCoverageDao()
    private val aggregateDao = db.healthAggregateDao()
    private val snapshotDao = db.healthCatalogSnapshotDao()
    private val retentionDao = db.healthRetentionDao()

    suspend fun removeHealthData(
        retention: LocalDataRetention,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        onProgress: suspend (LocalDataRemovalProgress) -> Unit = {}
    ): LocalDataRemovalResult = withContext(Dispatchers.IO) {
        onProgress(LocalDataRemovalProgress(LocalDataRemovalPhase.PREPARING))
        val cutoffEpochMillis = if (retention.removesEverything) {
            Long.MAX_VALUE
        } else {
            retention.cutoffEpochMillis(now, zoneId)
        }
        val retentionBoundaryEpochMillis = if (retention.removesEverything) {
            now.toEpochMilli()
        } else {
            cutoffEpochMillis
        }

        // Publish the boundary first so a concurrent periodic sync cannot refill history between batches.
        db.withTransaction {
            if (retention.removesEverything) retentionDao.clearState()
            updateRetentionBoundaries(retentionBoundaryEpochMillis)
            snapshotDao.clearAll()
        }

        val recordCount = healthDao.countRecords()
        val recordsRemoved = removeHealthRecords(cutoffEpochMillis, recordCount, onProgress)
        val legacyHeartRateRowsRemoved = removeLegacyHeartRate(cutoffEpochMillis, onProgress)

        onProgress(LocalDataRemovalProgress(LocalDataRemovalPhase.RELATED_DATA))
        removeRelatedData(cutoffEpochMillis, zoneId, retention.removesEverything)
        if (retention.removesEverything) removeRemainingHealthAcks()

        // Deleted pages stay reusable. Physical compaction remains constrained background work.
        onProgress(LocalDataRemovalProgress(LocalDataRemovalPhase.RECLAIMING_SPACE))
        db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(PASSIVE)")

        val result = LocalDataRemovalResult(recordsRemoved, legacyHeartRateRowsRemoved)
        onProgress(LocalDataRemovalProgress(LocalDataRemovalPhase.COMPLETE))
        result
    }

    private suspend fun removeHealthRecords(
        cutoffEpochMillis: Long,
        recordCount: Int,
        onProgress: suspend (LocalDataRemovalProgress) -> Unit
    ): Int {
        var recordsRemoved = 0
        var recordsScanned = 0
        var lastScannedLocalId = 0L
        while (true) {
            val page = healthDao.retentionScanPage(lastScannedLocalId, SCAN_PAGE_SIZE)
            if (page.isEmpty()) break
            lastScannedLocalId = page.last().localId
            page.asSequence()
                .filter { it.endsBefore(cutoffEpochMillis) }
                .map { it.localId }
                .chunked(DELETE_BATCH_SIZE)
                .forEach { recordIds ->
                    db.withTransaction {
                        retentionDao.deleteValueAcks(recordIds)
                        retentionDao.deleteRecordAcks(recordIds)
                        retentionDao.deleteValues(recordIds)
                        retentionDao.deleteRecords(recordIds)
                    }
                    recordsRemoved += recordIds.size
                    yield()
                }
            recordsScanned += page.size
            onProgress(
                LocalDataRemovalProgress(
                    phase = LocalDataRemovalPhase.HEALTH_RECORDS,
                    completedItems = recordsScanned,
                    totalItems = recordCount
                )
            )
            yield()
        }
        return recordsRemoved
    }

    private suspend fun removeLegacyHeartRate(
        cutoffEpochMillis: Long,
        onProgress: suspend (LocalDataRemovalProgress) -> Unit
    ): Int {
        val cutoffEpochSecond = cutoffEpochMillis / 1_000L
        val totalRows = dao.countBefore(cutoffEpochSecond)
        var rowsRemoved = 0
        while (true) {
            val removed = dao.deleteBatchBefore(cutoffEpochSecond, DELETE_BATCH_SIZE)
            if (removed == 0) break
            rowsRemoved += removed
            onProgress(
                LocalDataRemovalProgress(
                    phase = LocalDataRemovalPhase.LEGACY_HEART_RATE,
                    completedItems = rowsRemoved,
                    totalItems = totalRows
                )
            )
            yield()
        }
        return rowsRemoved
    }

    private suspend fun removeRelatedData(
        cutoffEpochMillis: Long,
        zoneId: ZoneId,
        removesEverything: Boolean
    ) {
        if (removesEverything) {
            db.withTransaction {
                aggregateDao.clearAll()
                retentionDao.clearDailyArchive()
                retentionDao.clearSleepArchive()
                syncDao.clearAll()
                coverageDao.clearAll()
            }
            return
        }

        val cutoffDate = Instant.ofEpochMilli(cutoffEpochMillis).atZone(zoneId).toLocalDate().toString()
        db.withTransaction {
            retentionDao.deleteAggregateAcksBefore(cutoffEpochMillis)
            retentionDao.deleteAggregatesBefore(cutoffEpochMillis)
            retentionDao.deleteDailyArchiveBefore(cutoffDate)
            retentionDao.deleteSleepArchiveBefore(cutoffEpochMillis)
            syncDao.deleteBefore(cutoffEpochMillis)
            coverageDao.deleteBefore(cutoffEpochMillis)
        }
    }

    private suspend fun removeRemainingHealthAcks() {
        while (retentionDao.deleteHealthAckBatch(SCAN_PAGE_SIZE) > 0) {
            yield()
        }
    }

    private suspend fun updateRetentionBoundaries(cutoffEpochMillis: Long) {
        val updatedAt = Instant.now().toEpochMilli()
        HealthDataTypeRegistry.implementedDescriptors.forEach { descriptor ->
            val currentBoundary = retentionDao.archivedBeforeEpochMillis(descriptor.key)
            retentionDao.upsertState(
                HealthRetentionStateEntity(
                    recordType = descriptor.key,
                    archivedBeforeEpochMillis = maxOf(currentBoundary ?: Long.MIN_VALUE, cutoffEpochMillis),
                    updatedAtEpochMillis = updatedAt
                )
            )
        }
    }

    private companion object {
        const val SCAN_PAGE_SIZE = 2_000
        const val DELETE_BATCH_SIZE = 500
    }
}
