package com.example.healthconnectandroid.hc.export

import android.content.ContentResolver
import android.net.Uri
import com.example.healthconnectandroid.data.AppDb
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HealthZipExporter(
    private val db: AppDb,
    private val csvExporter: HealthCsvExporter = HealthCsvExporter(db)
) {
    private val healthDao = db.healthRecordDao()
    private val aggregateDao = db.healthAggregateDao()

    suspend fun exportAllCsvZip(
        contentResolver: ContentResolver,
        dest: Uri,
        start: Instant? = null,
        end: Instant? = null
    ): Int = withContext(Dispatchers.IO) {
        val exportedAt = Instant.now()
        val recordTypes = if (start != null && end != null) {
            healthDao.exportRecordTypesForRange(start.toEpochMilli(), end.toEpochMilli())
        } else {
            healthDao.exportRecordTypes()
        }
        val summaryTypes = aggregateDao.exportRecordTypes()
        var rowCount = 0
        contentResolver.openOutputStream(dest)?.use { os ->
            ZipOutputStream(os).use { zip ->
                zip.putNextEntry(ZipEntry("health_connect_all.csv"))
                rowCount = csvExporter.writeNormalizedCsvPaged(
                    outputStream = zip,
                    start = start,
                    end = end,
                    recordType = null,
                    exportedAt = exportedAt
                )
                zip.closeEntry()

                recordTypes.forEach { recordType ->
                    zip.putNextEntry(ZipEntry("${HealthCsvFormat.safeFileStem(recordType)}.csv"))
                    csvExporter.writeNormalizedCsvPaged(
                        outputStream = zip,
                        start = start,
                        end = end,
                        recordType = recordType,
                        exportedAt = exportedAt
                    )
                    zip.closeEntry()
                }

                if (summaryTypes.isNotEmpty()) {
                    zip.putNextEntry(ZipEntry("health_connect_daily_summaries.csv"))
                    csvExporter.writeAggregateCsvPaged(
                        outputStream = zip,
                        recordType = null,
                        exportedAt = exportedAt
                    )
                    zip.closeEntry()

                    summaryTypes.forEach { recordType ->
                        zip.putNextEntry(ZipEntry("${HealthCsvFormat.safeFileStem(recordType)}_daily_summary.csv"))
                        csvExporter.writeAggregateCsvPaged(
                            outputStream = zip,
                            recordType = recordType,
                            exportedAt = exportedAt
                        )
                        zip.closeEntry()
                    }
                }
            }
        } ?: error("Cannot open output stream")
        csvExporter.markExported(start, end, exportedAt)
        rowCount
    }
}
