package com.example.healthconnectandroid.hc.retention

import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthRetentionStateEntity
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import java.time.Instant
import java.time.temporal.ChronoUnit

internal object HealthRetentionPolicy {
    private const val PRODUCTION_SERVER_PREFIX = "PRODUCTION:"
    private const val RETENTION_DAYS = 30L

    fun acceptsServerKey(serverKey: String): Boolean = serverKey.startsWith(PRODUCTION_SERVER_PREFIX)

    fun cutoffEpochMillis(now: Instant = Instant.now()): Long =
        now.minus(RETENTION_DAYS, ChronoUnit.DAYS).toEpochMilli()
}

data class HealthRetentionResult(
    val recordsRemoved: Int,
    val valuesRemoved: Int,
    val archivedTypes: Set<String>,
    val hasMore: Boolean
)

class HealthRetentionService(
    private val db: AppDb
) {
    private val retentionDao = db.healthRetentionDao()
    private val snapshotDao = db.healthCatalogSnapshotDao()

    suspend fun archiveUploadedRecords(
        productionServerKey: String,
        cutoffEpochMillis: Long,
        recordLimit: Int = DEFAULT_RECORD_LIMIT
    ): HealthRetentionResult {
        require(HealthRetentionPolicy.acceptsServerKey(productionServerKey)) {
            "Retention requires a Production upload acknowledgement"
        }
        if (recordLimit <= 0) return HealthRetentionResult(0, 0, emptySet(), hasMore = false)

        val candidates = loadCandidates(productionServerKey, cutoffEpochMillis, recordLimit)
        if (candidates.isEmpty()) {
            return HealthRetentionResult(0, 0, emptySet(), hasMore = false)
        }
        val recordIds = candidates.map { it.localId }
        val values = retentionDao.valuesForRecords(recordIds)
        val archivedAt = Instant.now().toEpochMilli()
        val archiveRows = HealthArchiveBuilder.build(candidates, values, archivedAt)
        val archivedTypes = candidates.mapTo(mutableSetOf()) { it.recordType }

        db.withTransaction {
            if (archiveRows.daily.isNotEmpty()) retentionDao.upsertDailyArchive(archiveRows.daily)
            if (archiveRows.sleep.isNotEmpty()) retentionDao.upsertSleepArchive(archiveRows.sleep)
            retentionDao.deleteValueAcks(recordIds)
            retentionDao.deleteRecordAcks(recordIds)
            retentionDao.deleteValues(recordIds)
            retentionDao.deleteRecords(recordIds)
            archivedTypes.forEach { recordType ->
                retentionDao.upsertState(
                    HealthRetentionStateEntity(
                        recordType = recordType,
                        archivedBeforeEpochMillis = cutoffEpochMillis,
                        updatedAtEpochMillis = archivedAt
                    )
                )
            }
            snapshotDao.markDirty(archivedTypes)
        }

        val hasMore = loadCandidates(productionServerKey, cutoffEpochMillis, 1).isNotEmpty()
        return HealthRetentionResult(
            recordsRemoved = candidates.size,
            valuesRemoved = values.size,
            archivedTypes = archivedTypes,
            hasMore = hasMore
        )
    }

    private suspend fun loadCandidates(
        serverKey: String,
        cutoffEpochMillis: Long,
        limit: Int
    ) = buildList {
        for (descriptor in HealthDataTypeRegistry.implementedDescriptors) {
            val remaining = limit - size
            if (remaining <= 0) break
            addAll(
                retentionDao.uploadedRecordCandidates(
                    serverKey = serverKey,
                    recordType = descriptor.key,
                    cutoffEpochMillis = cutoffEpochMillis,
                    limit = remaining
                )
            )
        }
    }

    companion object {
        const val DEFAULT_RECORD_LIMIT = 500
    }
}
