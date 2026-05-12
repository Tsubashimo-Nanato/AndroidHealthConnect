package com.example.healthconnectandroid.hc.export

import android.content.ContentResolver
import android.net.Uri
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.data.HrRow
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val EXPORT_PAGE_SIZE = 1000

class HealthCsvExporter(
    private val db: AppDb
) {
    private val dao = db.heartRateDao()
    private val healthDao = db.healthRecordDao()
    private val aggregateDao = db.healthAggregateDao()

    suspend fun exportLegacyHeartRateCsv(contentResolver: ContentResolver, dest: Uri): Int = withContext(Dispatchers.IO) {
        var rowCount = 0
        contentResolver.openOutputStream(dest)?.use { os ->
            BufferedWriter(OutputStreamWriter(os, Charsets.UTF_8)).use { writer ->
                writer.appendLine("time_utc_iso,bpm,epoch_second")
                var offset = 0
                while (true) {
                    val rows: List<HrRow> = dao.allAscRowsPaged(EXPORT_PAGE_SIZE, offset)
                    if (rows.isEmpty()) break
                    for (row in rows) {
                        val ts = Instant.ofEpochSecond(row.epochSecond).atOffset(ZoneOffset.UTC).toInstant()
                        writer.append(HealthCsvFormat.CSV_TIME_FMT.format(ts)).append(',')
                            .append(row.bpm.toString()).append(',')
                            .append(row.epochSecond.toString()).appendLine()
                    }
                    rowCount += rows.size
                    if (rows.size < EXPORT_PAGE_SIZE) break
                    offset += rows.size
                }
            }
        } ?: error("Cannot open output stream")
        rowCount
    }

    suspend fun exportAllCsv(
        contentResolver: ContentResolver,
        dest: Uri,
        start: Instant? = null,
        end: Instant? = null
    ): Int = withContext(Dispatchers.IO) {
        val exportedAt = Instant.now()
        val rowCount = contentResolver.openOutputStream(dest)?.use { os ->
            writeNormalizedCsvPaged(os, start, end, recordType = null, exportedAt = exportedAt)
        } ?: error("Cannot open output stream")
        markExported(start, end, exportedAt)
        rowCount
    }

    suspend fun exportTypeCsv(contentResolver: ContentResolver, dest: Uri, recordType: String): Int =
        withContext(Dispatchers.IO) {
        val exportedAt = Instant.now()
        val rowCount = contentResolver.openOutputStream(dest)?.use { os ->
            writeNormalizedCsvPaged(os, start = null, end = null, recordType = recordType, exportedAt = exportedAt)
        } ?: error("Cannot open output stream")
        healthDao.markTypeExported(recordType, exportedAt.toEpochMilli())
        rowCount
    }

    suspend fun exportSummaryCsv(contentResolver: ContentResolver, dest: Uri): Int = withContext(Dispatchers.IO) {
        val exportedAt = Instant.now()
        contentResolver.openOutputStream(dest)?.use { os ->
            writeAggregateCsvPaged(os, recordType = null, exportedAt = exportedAt)
        } ?: error("Cannot open output stream")
    }

    suspend fun exportTypeSummaryCsv(contentResolver: ContentResolver, dest: Uri, recordType: String): Int =
        withContext(Dispatchers.IO) {
        val exportedAt = Instant.now()
        contentResolver.openOutputStream(dest)?.use { os ->
            writeAggregateCsvPaged(os, recordType = recordType, exportedAt = exportedAt)
        } ?: error("Cannot open output stream")
    }

    internal suspend fun writeNormalizedCsvPaged(
        outputStream: OutputStream,
        start: Instant?,
        end: Instant?,
        recordType: String?,
        exportedAt: Instant
    ): Int {
        HealthCsvFormat.writeUtf8Line(outputStream, HealthCsvFormat.normalizedCsvHeaderLine())
        var rowCount = 0
        forEachNormalizedExportPage(start, end, recordType) { page ->
            val rows = page.distinctBy { "${it.localRecordId}:${it.valueKey}" }
            rows.forEach { row ->
                HealthCsvFormat.writeUtf8Line(outputStream, HealthCsvFormat.normalizedCsvLine(row, exportedAt))
            }
            rowCount += rows.size
        }
        return rowCount
    }

    internal suspend fun writeAggregateCsvPaged(
        outputStream: OutputStream,
        recordType: String?,
        exportedAt: Instant
    ): Int {
        HealthCsvFormat.writeUtf8Line(outputStream, HealthCsvFormat.aggregateCsvHeaderLine())
        var rowCount = 0
        var offset = 0
        while (true) {
            val page = if (recordType == null) {
                aggregateDao.exportRowsPaged(EXPORT_PAGE_SIZE, offset)
            } else {
                aggregateDao.exportRowsForTypePaged(recordType, EXPORT_PAGE_SIZE, offset)
            }
            if (page.isEmpty()) break
            page.forEach { row ->
                HealthCsvFormat.writeUtf8Line(outputStream, HealthCsvFormat.aggregateCsvLine(row, exportedAt))
            }
            rowCount += page.size
            if (page.size < EXPORT_PAGE_SIZE) break
            offset += page.size
        }
        return rowCount
    }

    private suspend fun forEachNormalizedExportPage(
        start: Instant?,
        end: Instant?,
        recordType: String?,
        onPage: suspend (List<com.example.healthconnectandroid.data.HealthCsvRow>) -> Unit
    ) {
        require((start == null) == (end == null)) { "CSV export range requires both start and end" }
        var offset = 0
        while (true) {
            val page = when {
                recordType != null && start != null && end != null ->
                    healthDao.exportRowsForTypeRangePaged(recordType, start.toEpochMilli(), end.toEpochMilli(), EXPORT_PAGE_SIZE, offset)
                recordType != null ->
                    healthDao.exportRowsForTypePaged(recordType, EXPORT_PAGE_SIZE, offset)
                start != null && end != null ->
                    healthDao.exportRowsForRangePaged(start.toEpochMilli(), end.toEpochMilli(), EXPORT_PAGE_SIZE, offset)
                else ->
                    healthDao.exportRowsPaged(EXPORT_PAGE_SIZE, offset)
            }
            if (page.isEmpty()) break
            onPage(page)
            if (page.size < EXPORT_PAGE_SIZE) break
            offset += page.size
        }
    }

    internal suspend fun markExported(start: Instant?, end: Instant?, exportedAt: Instant) {
        val exportedAtMillis = exportedAt.toEpochMilli()
        if (start != null && end != null) {
            healthDao.markRangeExported(start.toEpochMilli(), end.toEpochMilli(), exportedAtMillis)
        } else {
            healthDao.markAllExported(exportedAtMillis)
        }
    }
}
