package com.example.healthconnectandroid.hc.local

import androidx.room.withTransaction
import com.example.healthconnectandroid.data.AppDb

class LocalDataService(
    private val db: AppDb
) {
    private val dao = db.heartRateDao()
    private val healthDao = db.healthRecordDao()
    private val syncDao = db.healthSyncRunDao()
    private val aggregateDao = db.healthAggregateDao()

    /** Clear local normalized data, aggregates, sync metadata, and legacy heart-rate rows. */
    suspend fun clearDb(): Int {
        val before = dao.count()
        db.withTransaction {
            aggregateDao.clearAll()
            healthDao.clearValues()
            healthDao.clearRecords()
            syncDao.clearAll()
        }
        dao.clearAll()
        return before
    }


}
