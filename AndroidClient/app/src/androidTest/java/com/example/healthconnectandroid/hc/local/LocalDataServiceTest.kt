package com.example.healthconnectandroid.hc.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HealthRecordEntity
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDataServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).build()

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun removeEverythingCompletesCheckpointAndReportsCompletion() = runBlocking {
        db.healthRecordDao().insertRecord(record())
        val phases = mutableListOf<LocalDataRemovalPhase>()

        val result = LocalDataService(db).removeHealthData(
            retention = LocalDataRetention.NONE,
            now = Instant.parse("2026-09-11T00:00:00Z"),
            zoneId = ZoneOffset.UTC,
            onProgress = { phases += it.phase }
        )

        assertEquals(1, result.recordsRemoved)
        assertEquals(0, db.healthRecordDao().countRecords())
        assertTrue(phases.contains(LocalDataRemovalPhase.RECLAIMING_SPACE))
        assertEquals(LocalDataRemovalPhase.COMPLETE, phases.last())
    }

    private fun record() = HealthRecordEntity(
        recordUid = "local-removal-record",
        dedupeKey = "local-removal-record",
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
}
