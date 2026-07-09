package com.example.healthconnectandroid.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HeartRateEntity::class,
        HealthRecordEntity::class,
        HealthValueEntity::class,
        HealthSyncRunEntity::class,
        HealthSyncCoverageEntity::class,
        HealthAggregateEntity::class,
        HealthUploadAckEntity::class,
        MedicineItemEntity::class,
        MedicineScheduleEntity::class,
        MedicineReminderSettingEntity::class,
        MedicineDoseLogEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao
    abstract fun healthRecordDao(): HealthRecordDao
    abstract fun healthSyncRunDao(): HealthSyncRunDao
    abstract fun healthSyncCoverageDao(): HealthSyncCoverageDao
    abstract fun healthAggregateDao(): HealthAggregateDao
    abstract fun healthUploadDao(): HealthUploadDao
    abstract fun medicineDao(): MedicineDao

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_records` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordUid` TEXT,
                        `recordType` TEXT NOT NULL,
                        `recordKind` TEXT NOT NULL,
                        `startEpochMillis` INTEGER NOT NULL,
                        `endEpochMillis` INTEGER,
                        `startZoneOffsetSeconds` INTEGER,
                        `endZoneOffsetSeconds` INTEGER,
                        `sourcePackage` TEXT,
                        `metadataJson` TEXT,
                        `rawJson` TEXT,
                        `syncStatus` TEXT NOT NULL,
                        `exportStatus` TEXT NOT NULL,
                        `lastReadEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_values` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordLocalId` INTEGER NOT NULL,
                        `metric` TEXT NOT NULL,
                        `unit` TEXT,
                        `valueFloat` REAL,
                        `valueInt` INTEGER,
                        `valueText` TEXT,
                        `valueJson` TEXT,
                        `sampleEpochMillis` INTEGER,
                        `sequence` INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_records_recordType_startEpochMillis` " +
                        "ON `health_records` (`recordType`, `startEpochMillis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_records_recordType_recordUid` " +
                        "ON `health_records` (`recordType`, `recordUid`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_recordLocalId` " +
                        "ON `health_values` (`recordLocalId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_metric_sampleEpochMillis` " +
                        "ON `health_values` (`metric`, `sampleEpochMillis`)"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `health_records` ADD COLUMN `dedupeKey` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `health_records` ADD COLUMN `localDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `health_records` ADD COLUMN `createdEpochMillis` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `health_records` ADD COLUMN `updatedEpochMillis` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `health_records` ADD COLUMN `syncedEpochMillis` INTEGER")
                db.execSQL("ALTER TABLE `health_records` ADD COLUMN `exportedEpochMillis` INTEGER")
                db.execSQL(
                    """
                    UPDATE `health_records`
                    SET
                        `localDate` = date(`startEpochMillis` / 1000, 'unixepoch'),
                        `createdEpochMillis` = `lastReadEpochMillis`,
                        `updatedEpochMillis` = `lastReadEpochMillis`,
                        `dedupeKey` = CASE
                            WHEN `recordUid` IS NOT NULL AND `recordUid` != ''
                                THEN 'uid:' || COALESCE(`sourcePackage`, '') || ':' || `recordUid`
                            ELSE 'legacy:' || `recordType` || '|' || `recordKind` || '|' ||
                                `startEpochMillis` || '|' || COALESCE(`endEpochMillis`, '') || '|' ||
                                COALESCE(`startZoneOffsetSeconds`, '') || '|' ||
                                COALESCE(`endZoneOffsetSeconds`, '') || '|' ||
                                COALESCE(`sourcePackage`, '') || '|' || COALESCE(`rawJson`, '')
                        END
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `health_records`
                    SET `dedupeKey` = `dedupeKey` || '|legacyLocalId=' || `localId`
                    WHERE `dedupeKey` IN (
                        SELECT `dedupeKey`
                        FROM `health_records`
                        GROUP BY `recordType`, `dedupeKey`
                        HAVING COUNT(*) > 1
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_health_records_recordType_dedupeKey` " +
                        "ON `health_records` (`recordType`, `dedupeKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_records_recordType_localDate` " +
                        "ON `health_records` (`recordType`, `localDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_records_syncStatus_updatedEpochMillis` " +
                        "ON `health_records` (`syncStatus`, `updatedEpochMillis`)"
                )

                db.execSQL("ALTER TABLE `health_values` ADD COLUMN `valueKey` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `health_values` ADD COLUMN `label` TEXT")
                db.execSQL("ALTER TABLE `health_values` ADD COLUMN `category` TEXT")
                db.execSQL("ALTER TABLE `health_values` ADD COLUMN `numericValue` REAL")
                db.execSQL("ALTER TABLE `health_values` ADD COLUMN `secondaryNumericValue` REAL")
                db.execSQL("ALTER TABLE `health_values` ADD COLUMN `startEpochMillis` INTEGER")
                db.execSQL("ALTER TABLE `health_values` ADD COLUMN `endEpochMillis` INTEGER")
                db.execSQL("ALTER TABLE `health_values` ADD COLUMN `localDate` TEXT")
                db.execSQL(
                    """
                    UPDATE `health_values`
                    SET
                        `startEpochMillis` = `sampleEpochMillis`,
                        `localDate` = CASE
                            WHEN `sampleEpochMillis` IS NOT NULL
                                THEN date(`sampleEpochMillis` / 1000, 'unixepoch')
                            ELSE NULL
                        END,
                        `numericValue` = COALESCE(`valueFloat`, CAST(`valueInt` AS REAL)),
                        `valueKey` = `metric` || '|' || COALESCE(`sequence`, '') || '|' ||
                            COALESCE(`sampleEpochMillis`, '') || '|' || `localId`
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_recordLocalId_valueKey` " +
                        "ON `health_values` (`recordLocalId`, `valueKey`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_metric_localDate` " +
                        "ON `health_values` (`metric`, `localDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_metric_startEpochMillis` " +
                        "ON `health_values` (`metric`, `startEpochMillis`)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_sync_runs` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordType` TEXT NOT NULL,
                        `requestedStartEpochMillis` INTEGER NOT NULL,
                        `requestedEndEpochMillis` INTEGER NOT NULL,
                        `startedEpochMillis` INTEGER NOT NULL,
                        `finishedEpochMillis` INTEGER,
                        `status` TEXT NOT NULL,
                        `recordsRead` INTEGER NOT NULL,
                        `recordsInserted` INTEGER NOT NULL,
                        `recordsUpdated` INTEGER NOT NULL,
                        `recordsSkippedDuplicate` INTEGER NOT NULL,
                        `valuesStored` INTEGER NOT NULL,
                        `errorMessage` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_sync_runs_recordType_startedEpochMillis` " +
                        "ON `health_sync_runs` (`recordType`, `startedEpochMillis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_sync_runs_recordType_finishedEpochMillis` " +
                        "ON `health_sync_runs` (`recordType`, `finishedEpochMillis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_sync_runs_status` " +
                        "ON `health_sync_runs` (`status`)"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_aggregate_summaries` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordType` TEXT NOT NULL,
                        `metric` TEXT NOT NULL,
                        `bucketPeriod` TEXT NOT NULL,
                        `bucketStartEpochMillis` INTEGER NOT NULL,
                        `bucketEndEpochMillis` INTEGER NOT NULL,
                        `localDate` TEXT NOT NULL,
                        `timezoneId` TEXT,
                        `value` REAL NOT NULL,
                        `unit` TEXT,
                        `source` TEXT NOT NULL,
                        `computedEpochMillis` INTEGER NOT NULL,
                        `requestedStartEpochMillis` INTEGER NOT NULL,
                        `requestedEndEpochMillis` INTEGER NOT NULL,
                        `rawJson` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    `index_health_aggregate_summaries_recordType_metric_bucketPeriod_bucketStartEpochMillis_bucketEndEpochMillis_source`
                    ON `health_aggregate_summaries` (
                        `recordType`,
                        `metric`,
                        `bucketPeriod`,
                        `bucketStartEpochMillis`,
                        `bucketEndEpochMillis`,
                        `source`
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_aggregate_summaries_recordType_localDate` " +
                        "ON `health_aggregate_summaries` (`recordType`, `localDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_aggregate_summaries_source_computedEpochMillis` " +
                        "ON `health_aggregate_summaries` (`source`, `computedEpochMillis`)"
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_upload_ack` (
                        `serverKey` TEXT NOT NULL,
                        `itemKind` TEXT NOT NULL,
                        `localId` INTEGER NOT NULL,
                        `batchId` TEXT NOT NULL,
                        `uploadedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`serverKey`, `itemKind`, `localId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_upload_ack_serverKey_batchId` " +
                        "ON `health_upload_ack` (`serverKey`, `batchId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_upload_ack_serverKey_uploadedAtEpochMillis` " +
                        "ON `health_upload_ack` (`serverKey`, `uploadedAtEpochMillis`)"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `health_sync_coverage` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `recordType` TEXT NOT NULL,
                        `coveredStartEpochMillis` INTEGER NOT NULL,
                        `coveredEndEpochMillis` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `mode` TEXT NOT NULL,
                        `updatedAtEpochMillis` INTEGER NOT NULL,
                        `recordsRead` INTEGER NOT NULL,
                        `recordsInserted` INTEGER NOT NULL,
                        `recordsSkippedDuplicate` INTEGER NOT NULL,
                        `errorMessage` TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_sync_coverage_recordType_coveredStartEpochMillis_coveredEndEpochMillis` " +
                        "ON `health_sync_coverage` (`recordType`, `coveredStartEpochMillis`, `coveredEndEpochMillis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_sync_coverage_recordType_status` " +
                        "ON `health_sync_coverage` (`recordType`, `status`)"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_records_updatedEpochMillis_localId` " +
                        "ON `health_records` (`updatedEpochMillis`, `localId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_recordLocalId_sequence_localId` " +
                        "ON `health_values` (`recordLocalId`, `sequence`, `localId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_localDate` " +
                        "ON `health_values` (`localDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_aggregate_summaries_bucketEndEpochMillis` " +
                        "ON `health_aggregate_summaries` (`bucketEndEpochMillis`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_aggregate_summaries_computedEpochMillis_localId` " +
                        "ON `health_aggregate_summaries` (`computedEpochMillis`, `localId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_sync_runs_finishedEpochMillis` " +
                        "ON `health_sync_runs` (`finishedEpochMillis`)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    UPDATE `health_records`
                    SET `metadataJson` = NULL,
                        `rawJson` = NULL
                    WHERE `metadataJson` IS NOT NULL
                       OR `rawJson` IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    UPDATE `health_aggregate_summaries`
                    SET `rawJson` = NULL
                    WHERE `rawJson` IS NOT NULL
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_metric_localDate_startEpochMillis_localId` " +
                        "ON `health_values` (`metric`, `localDate`, `startEpochMillis`, `localId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_metric_startEpochMillis_localId` " +
                        "ON `health_values` (`metric`, `startEpochMillis`, `localId`)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_health_values_metric_startEpochMillis_localId` " +
                        "ON `health_values` (`metric`, `startEpochMillis`, `localId`)"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medicine_items` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `notes` TEXT,
                        `active` INTEGER NOT NULL,
                        `createdEpochMillis` INTEGER NOT NULL,
                        `updatedEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medicine_schedules` (
                        `medicineLocalId` INTEGER NOT NULL,
                        `slot` TEXT NOT NULL,
                        `createdEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`medicineLocalId`, `slot`),
                        FOREIGN KEY(`medicineLocalId`) REFERENCES `medicine_items`(`localId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medicine_reminder_settings` (
                        `slot` TEXT NOT NULL,
                        `hour` INTEGER NOT NULL,
                        `minute` INTEGER NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `alarmEnabled` INTEGER NOT NULL DEFAULT 0,
                        `updatedEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`slot`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `medicine_dose_logs` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `medicineLocalId` INTEGER,
                        `medicineName` TEXT NOT NULL,
                        `slot` TEXT NOT NULL,
                        `localDate` TEXT NOT NULL,
                        `scheduledEpochMillis` INTEGER,
                        `recordedEpochMillis` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        `note` TEXT,
                        `createdEpochMillis` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_items_active_name` ON `medicine_items` (`active`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_items_updatedEpochMillis` ON `medicine_items` (`updatedEpochMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_schedules_slot` ON `medicine_schedules` (`slot`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_schedules_medicineLocalId` ON `medicine_schedules` (`medicineLocalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_dose_logs_localDate_slot` ON `medicine_dose_logs` (`localDate`, `slot`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_dose_logs_medicineLocalId` ON `medicine_dose_logs` (`medicineLocalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_dose_logs_status` ON `medicine_dose_logs` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medicine_dose_logs_recordedEpochMillis` ON `medicine_dose_logs` (`recordedEpochMillis`)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `medicine_items` ADD COLUMN `doseText` TEXT")
                db.execSQL("ALTER TABLE `medicine_items` ADD COLUMN `summary` TEXT")
                db.execSQL("ALTER TABLE `medicine_items` ADD COLUMN `details` TEXT")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `medicine_reminder_settings` " +
                        "ADD COLUMN `alarmEnabled` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDb::class.java, "hc_demo.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
