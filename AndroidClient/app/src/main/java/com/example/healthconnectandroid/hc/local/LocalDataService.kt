package com.example.healthconnectandroid.hc.local

import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb

class LocalDataService(
    private val db: AppDb
) {
    private val dao = db.heartRateDao()
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()
    private val coverageDao = db.healthSyncCoverageDao()
    private val aggregateDao = db.healthAggregateDao()
    private val uploadDao = db.healthUploadDao()

    /** Clear local normalized data, aggregates, sync/upload metadata, and older heart-rate rows. */
    suspend fun clearDb(): Int {
        val before = dao.count()
        db.withTransaction {
            uploadDao.clearAll()
            aggregateDao.clearAll()
            healthDao.clearValues()
            healthDao.clearRecords()
            syncDao.clearAll()
            coverageDao.clearAll()
        }
        dao.clearAll()
        db.openHelper.writableDatabase.execSQL("VACUUM")
        db.openHelper.writableDatabase.execSQL("PRAGMA optimize")
        return before
    }
}
