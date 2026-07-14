package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.data.HealthCatalogSnapshotEntity
import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogRefreshPolicyTest {
    @Test
    fun refreshesMissingDirtyAndContextChangedSnapshots() {
        val now = Instant.parse("2026-07-12T10:00:00Z")
        val snapshots = listOf(
            snapshot("fresh", now),
            snapshot("dirty", now, dirty = true),
            snapshot("old-date", now, localDate = "2026-07-11"),
            snapshot("old-zone", now, zoneId = "UTC")
        )

        val result = CatalogRefreshPolicy.recordTypesToRefresh(
            snapshots = snapshots,
            recordTypes = listOf("fresh", "dirty", "old-date", "old-zone", "missing"),
            localDate = "2026-07-12",
            zoneId = "Asia/Tokyo",
            now = now
        )

        assertEquals(listOf("dirty", "old-date", "old-zone", "missing"), result)
    }

    @Test
    fun refreshesSnapshotAfterMaximumAge() {
        val now = Instant.parse("2026-07-12T10:00:00Z")
        val expired = snapshot(
            recordType = "heart_rate",
            generatedAt = now.minus(CatalogRefreshPolicy.MAX_SNAPSHOT_AGE).minusMillis(1)
        )

        val result = CatalogRefreshPolicy.recordTypesToRefresh(
            snapshots = listOf(expired),
            recordTypes = listOf("heart_rate"),
            localDate = "2026-07-12",
            zoneId = "Asia/Tokyo",
            now = now
        )

        assertEquals(listOf("heart_rate"), result)
    }

    @Test
    fun changedRecordTypesOnlyIncludesStoredSyncData() {
        val results = listOf(
            HealthDataTypeSyncResult(key = "heart_rate", recordsInserted = 2),
            HealthDataTypeSyncResult(key = "sleep_session", aggregateRowsStored = 1),
            HealthDataTypeSyncResult(key = "steps", recordsRead = 10),
            HealthDataTypeSyncResult(key = "weight", errorMessage = "unavailable")
        )

        assertEquals(
            setOf("heart_rate", "sleep_session"),
            CatalogRefreshPolicy.changedRecordTypes(results)
        )
    }

    private fun snapshot(
        recordType: String,
        generatedAt: Instant,
        dirty: Boolean = false,
        localDate: String = "2026-07-12",
        zoneId: String = "Asia/Tokyo"
    ) = HealthCatalogSnapshotEntity(
        recordType = recordType,
        recordCount = 1,
        recentRecordCount = 1,
        lastSyncedEpochMillis = null,
        lastSyncStatus = null,
        lastSyncError = null,
        latestRecordEpochMillis = null,
        summaryText = "Local data",
        latestMetric = null,
        latestPrimaryText = null,
        latestSecondaryText = null,
        latestValue = null,
        latestSecondaryValue = null,
        latestUnit = null,
        latestStartEpochMillis = null,
        latestEndEpochMillis = null,
        latestLocalDate = null,
        latestDurationText = null,
        todayLocalDate = localDate,
        todayTotal = null,
        todayUnit = null,
        zoneId = zoneId,
        generatedAtEpochMillis = generatedAt.toEpochMilli(),
        dirty = dirty
    )
}
