package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface HealthChangeTokenDao {
    @Query("SELECT * FROM health_change_tokens WHERE recordType = :recordType LIMIT 1")
    suspend fun tokenForType(recordType: String): HealthChangeTokenEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(token: HealthChangeTokenEntity)

    @Query("DELETE FROM health_change_tokens WHERE recordType = :recordType")
    suspend fun delete(recordType: String)

    @Query("DELETE FROM health_change_tokens")
    suspend fun clearAll()
}
