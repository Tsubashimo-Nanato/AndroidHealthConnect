package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReplace(items: List<HeartRateEntity>)

    @Query("SELECT * FROM heart_rate ORDER BY epochSecond DESC LIMIT :limit")
    suspend fun latest(limit: Int): List<HeartRateEntity>

    // Nearest sample but ONLY if it is inside the tolerance window
    @Query("""
        SELECT * FROM heart_rate
        WHERE epochSecond BETWEEN :lo AND :hi
        ORDER BY ABS(epochSecond - :atSec) ASC
        LIMIT 1
    """)
    suspend fun nearestInWindow(atSec: Long, lo: Long, hi: Long): HeartRateEntity?

    // Lightweight projection for CSV/export
    @Query("SELECT epochSecond, bpm FROM heart_rate ORDER BY epochSecond ASC")
    suspend fun allAscRows(): List<HrRow>

    @Query("SELECT epochSecond, bpm FROM heart_rate ORDER BY epochSecond ASC LIMIT :limit OFFSET :offset")
    suspend fun allAscRowsPaged(limit: Int, offset: Int): List<HrRow>

    @Query("DELETE FROM heart_rate")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM heart_rate")
    suspend fun count(): Int
}
