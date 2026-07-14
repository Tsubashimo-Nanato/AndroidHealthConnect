package com.example.healthconnectandroid.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthCatalogSnapshotDao {
    @Query("SELECT * FROM health_catalog_snapshot ORDER BY recordType ASC")
    fun observeAll(): Flow<List<HealthCatalogSnapshotEntity>>

    @Query("SELECT * FROM health_catalog_snapshot ORDER BY recordType ASC")
    suspend fun all(): List<HealthCatalogSnapshotEntity>

    @Query("SELECT recordType FROM health_catalog_snapshot WHERE dirty = 1 ORDER BY generatedAtEpochMillis ASC")
    suspend fun dirtyRecordTypes(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: HealthCatalogSnapshotEntity)

    @Query("UPDATE health_catalog_snapshot SET dirty = 1 WHERE recordType IN (:recordTypes)")
    suspend fun markDirty(recordTypes: Set<String>)

    @Query("UPDATE health_catalog_snapshot SET dirty = 1")
    suspend fun markAllDirty()

    @Query("DELETE FROM health_catalog_snapshot")
    suspend fun clearAll()
}
