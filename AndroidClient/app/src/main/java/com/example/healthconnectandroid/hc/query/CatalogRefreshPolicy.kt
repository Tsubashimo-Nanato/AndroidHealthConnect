package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.data.HealthCatalogSnapshotEntity
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import java.time.Duration
import java.time.Instant

internal object CatalogRefreshPolicy {
    val MAX_SNAPSHOT_AGE: Duration = Duration.ofHours(6)

    fun recordTypesToRefresh(
        snapshots: List<HealthCatalogSnapshotEntity>,
        recordTypes: List<String>,
        localDate: String,
        zoneId: String,
        now: Instant
    ): List<String> {
        val snapshotsByType = snapshots.associateBy { it.recordType }
        val oldestAccepted = now.minus(MAX_SNAPSHOT_AGE).toEpochMilli()
        return recordTypes.filter { recordType ->
            val snapshot = snapshotsByType[recordType]
            snapshot == null ||
                snapshot.dirty ||
                snapshot.todayLocalDate != localDate ||
                snapshot.zoneId != zoneId ||
                snapshot.generatedAtEpochMillis < oldestAccepted
        }
    }

    fun changedRecordTypes(results: List<HealthDataTypeSyncResult>): Set<String> =
        results.asSequence()
            .filter { result ->
                result.recordsStored > 0 ||
                    result.valuesStored > 0 ||
                    result.aggregateRowsStored > 0
            }
            .map { it.key }
            .toSet()
}
