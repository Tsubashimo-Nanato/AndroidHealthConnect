package com.example.healthconnectandroid.hc.retention

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.healthconnectandroid.data.AppDb

internal object DatabaseCompactionPolicy {
    const val MIN_RECLAIMABLE_BYTES = 256L * 1024L * 1024L
    const val FREE_SPACE_SAFETY_BYTES = 512L * 1024L * 1024L

    fun shouldCompact(databaseBytes: Long, reclaimableBytes: Long, usableBytes: Long): Boolean =
        reclaimableBytes >= MIN_RECLAIMABLE_BYTES &&
            usableBytes >= databaseBytes + FREE_SPACE_SAFETY_BYTES
}

class HealthDatabaseCompactionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val databaseFile = applicationContext.getDatabasePath(DATABASE_NAME)
        if (!databaseFile.exists()) return Result.success()
        val db = AppDb.get(applicationContext).openHelper.writableDatabase
        val pageSize = db.pragmaLong("page_size")
        val freePages = db.pragmaLong("freelist_count")
        val reclaimableBytes = pageSize * freePages
        if (!DatabaseCompactionPolicy.shouldCompact(databaseFile.length(), reclaimableBytes, databaseFile.parentFile?.usableSpace ?: 0L)) {
            return Result.success()
        }

        return runCatching {
            db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            db.execSQL("VACUUM")
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { throwable ->
                Log.w(TAG, "Database compaction failed reclaimableBytes=$reclaimableBytes", throwable)
                Result.retry()
            }
        )
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.pragmaLong(name: String): Long =
        query("PRAGMA $name").use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }

    companion object {
        const val WORK_NAME = "health_database_idle_compaction"
        private const val DATABASE_NAME = "hc_demo.db"
        private const val TAG = "HCDbCompaction"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<HealthDatabaseCompactionWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresCharging(true)
                        .setRequiresDeviceIdle(true)
                        .setRequiresStorageNotLow(true)
                        .build()
                )
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
