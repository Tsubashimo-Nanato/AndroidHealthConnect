package com.example.healthconnectandroid.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HealthSyncCursorDaoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDb::class.java).build()

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun batchLookupAndProductionAckUseStableLocalRows() = runBlocking {
        val recordDao = db.healthRecordDao()
        val uploadDao = db.healthUploadDao()
        val firstId = recordDao.insertRecord(record("first", start = 1_000L, updated = 2_000L))
        val secondId = recordDao.insertRecord(record("second", start = 3_000L, updated = 4_000L))

        assertEquals(
            setOf(firstId, secondId),
            recordDao.findByDedupeKeys("steps", listOf("first", "second"))
                .mapTo(mutableSetOf()) { it.localId }
        )

        uploadDao.insertAcks(
            listOf(
                HealthUploadAckEntity(
                    serverKey = "LOCAL_DEBUG:http://127.0.0.1/",
                    itemKind = "record",
                    localId = secondId,
                    batchId = "local",
                    uploadedAtEpochMillis = 5_000L
                ),
                HealthUploadAckEntity(
                    serverKey = "PRODUCTION:https://example.test/",
                    itemKind = "record",
                    localId = firstId,
                    batchId = "production",
                    uploadedAtEpochMillis = 5_000L
                )
            )
        )

        assertEquals(1_000L, uploadDao.latestProductionAckSourceEndEpochMillis("steps"))
    }

    private fun record(dedupeKey: String, start: Long, updated: Long) = HealthRecordEntity(
        recordUid = "uid-$dedupeKey",
        dedupeKey = dedupeKey,
        recordType = "steps",
        recordKind = "interval",
        startEpochMillis = start,
        endEpochMillis = null,
        localDate = "1970-01-01",
        startZoneOffsetSeconds = 0,
        endZoneOffsetSeconds = 0,
        sourcePackage = "test",
        metadataJson = null,
        rawJson = null,
        createdEpochMillis = updated,
        updatedEpochMillis = updated,
        lastReadEpochMillis = updated
    )
}
