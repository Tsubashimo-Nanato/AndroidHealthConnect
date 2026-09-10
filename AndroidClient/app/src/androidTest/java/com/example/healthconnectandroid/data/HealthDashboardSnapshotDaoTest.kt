package com.example.healthconnectandroid.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthconnectandroid.hc.query.HealthDashboardQueryService
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthDashboardSnapshotDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).build()

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun dashboardUsesCompactCatalogInsteadOfRawHealthHistory() = runBlocking {
        db.healthRecordDao().insertRecord(rawRecord())
        db.healthCatalogSnapshotDao().upsert(snapshot("heart_rate", 120, 4_000L))
        db.healthCatalogSnapshotDao().upsert(snapshot("sleep_session", 8, 5_000L))

        val status = HealthDashboardQueryService(db).localHealthStatus()

        assertEquals(128, status.localRecordCount)
        assertEquals(2, status.localDataTypeCount)
        assertEquals(Instant.ofEpochMilli(5_000L), status.lastSync)
    }

    @Test
    fun emptyCatalogDoesNotFallBackToRawTableScan() = runBlocking {
        db.healthRecordDao().insertRecord(rawRecord())

        val status = HealthDashboardQueryService(db).localHealthStatus()

        assertEquals(0, status.localRecordCount)
        assertEquals(0, status.localDataTypeCount)
        assertNull(status.lastSync)
    }

    private fun rawRecord() = HealthRecordEntity(
        recordUid = "dashboard-raw-record",
        dedupeKey = "dashboard-raw-record",
        recordType = "steps",
        recordKind = "interval",
        startEpochMillis = 1_000L,
        endEpochMillis = 2_000L,
        localDate = "1970-01-01",
        startZoneOffsetSeconds = 0,
        endZoneOffsetSeconds = 0,
        sourcePackage = "test",
        metadataJson = null,
        rawJson = null,
        createdEpochMillis = 2_000L,
        updatedEpochMillis = 2_000L,
        lastReadEpochMillis = 2_000L
    )

    private fun snapshot(recordType: String, recordCount: Int, lastSynced: Long) =
        HealthCatalogSnapshotEntity(
            recordType = recordType,
            recordCount = recordCount,
            recentRecordCount = recordCount,
            lastSyncedEpochMillis = lastSynced,
            lastSyncStatus = "success",
            lastSyncError = null,
            latestRecordEpochMillis = lastSynced,
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
            todayLocalDate = "1970-01-01",
            todayTotal = null,
            todayUnit = null,
            zoneId = "UTC",
            generatedAtEpochMillis = lastSynced,
            dirty = false
        )
}
