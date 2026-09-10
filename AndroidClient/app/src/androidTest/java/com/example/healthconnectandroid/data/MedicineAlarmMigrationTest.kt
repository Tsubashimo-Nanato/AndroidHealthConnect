package com.example.healthconnectandroid.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicineAlarmMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseNames = mutableListOf<String>()

    @After
    fun cleanUp() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun addsAlarmColumnToReleasedVersion12Schema() {
        withDatabase("medicine-migration-without-alarm.db") { db ->
            createReminderTable(db, includeAlarmColumn = false)

            AppDb.MIGRATION_12_13.migrate(db)

            assertEquals(1, columnCount(db, "alarmEnabled"))
            db.execSQL(
                "INSERT INTO medicine_reminder_settings " +
                    "(slot, hour, minute, enabled, updatedEpochMillis) VALUES ('MORNING', 8, 0, 1, 1)"
            )
            db.query(
                "SELECT alarmEnabled FROM medicine_reminder_settings WHERE slot = 'MORNING'"
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun keepsPreReleaseVersion12SchemaThatAlreadyHasAlarmColumn() {
        withDatabase("medicine-migration-with-alarm.db") { db ->
            createReminderTable(db, includeAlarmColumn = true)

            AppDb.MIGRATION_12_13.migrate(db)

            assertEquals(1, columnCount(db, "alarmEnabled"))
        }
    }

    private fun withDatabase(name: String, block: (SupportSQLiteDatabase) -> Unit) {
        databaseNames += name
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(name)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) = Unit
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    }
                )
                .build()
        )
        try {
            block(helper.writableDatabase)
        } finally {
            helper.close()
        }
    }

    private fun createReminderTable(db: SupportSQLiteDatabase, includeAlarmColumn: Boolean) {
        val alarmColumn = if (includeAlarmColumn) {
            "`alarmEnabled` INTEGER NOT NULL DEFAULT 0,"
        } else {
            ""
        }
        db.execSQL(
            """
            CREATE TABLE `medicine_reminder_settings` (
                `slot` TEXT NOT NULL,
                `hour` INTEGER NOT NULL,
                `minute` INTEGER NOT NULL,
                `enabled` INTEGER NOT NULL,
                $alarmColumn
                `updatedEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`slot`)
            )
            """.trimIndent()
        )
    }

    private fun columnCount(db: SupportSQLiteDatabase, columnName: String): Int =
        db.query("PRAGMA table_info(`medicine_reminder_settings`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            var count = 0
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) count++
            }
            count
        }
}
