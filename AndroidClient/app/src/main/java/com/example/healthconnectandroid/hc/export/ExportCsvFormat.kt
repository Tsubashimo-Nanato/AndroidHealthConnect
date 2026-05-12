package com.example.healthconnectandroid.hc.export

import com.example.healthconnectandroid.data.HealthAggregateCsvRow
import com.example.healthconnectandroid.data.HealthCsvRow
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

internal const val NORMALIZED_CSV_SCHEMA_VERSION = "1"
internal const val AGGREGATE_CSV_SCHEMA_VERSION = "1"

internal object HealthCsvFormat {
    val CSV_TIME_FMT: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT
    fun writeNormalizedCsv(
        outputStream: OutputStream,
        rows: List<HealthCsvRow>,
        exportedAt: Instant
    ) {
        writeUtf8Line(outputStream, normalizedCsvHeaderLine())
        for (row in rows) {
            writeUtf8Line(outputStream, normalizedCsvLine(row, exportedAt))
        }
    }

    fun writeAggregateCsv(
        outputStream: OutputStream,
        rows: List<HealthAggregateCsvRow>,
        exportedAt: Instant
    ) {
        writeUtf8Line(outputStream, aggregateCsvHeaderLine())
        for (row in rows) {
            writeUtf8Line(outputStream, aggregateCsvLine(row, exportedAt))
        }
    }

    fun writeUtf8Line(outputStream: OutputStream, line: String) {
        outputStream.write(line.toByteArray(Charsets.UTF_8))
        outputStream.write('\n'.code)
    }

    fun normalizedCsvHeaderLine(): String = csvLine(
        listOf(
            "schema_version",
            "exported_at",
            "local_record_id",
            "local_value_id",
            "value_key",
            "record_type",
            "record_kind",
            "health_connect_uid",
            "dedupe_key",
            "source_package",
            "record_start_time",
            "record_end_time",
            "value_start_time",
            "value_end_time",
            "local_date",
            "timezone_or_zone_offset",
            "value_metric",
            "value",
            "value_2",
            "unit",
            "category_or_stage",
            "label",
            "text_value",
            "json_value",
            "metadata_json",
            "raw_json"
        )
    )

    fun aggregateCsvHeaderLine(): String = csvLine(
        listOf(
            "summary_schema_version",
            "exported_at",
            "local_summary_id",
            "record_type",
            "metric",
            "bucket_period",
            "bucket_start_time",
            "bucket_end_time",
            "local_date",
            "timezone",
            "source",
            "value",
            "unit",
            "computed_at",
            "requested_start_time",
            "requested_end_time",
            "raw_json"
        )
    )

    fun normalizedCsvLine(row: HealthCsvRow, exportedAt: Instant): String = csvLine(
        listOf(
            NORMALIZED_CSV_SCHEMA_VERSION,
            CSV_TIME_FMT.format(exportedAt),
            row.localRecordId.toString(),
            row.localValueId.toString(),
            row.valueKey,
            row.recordType,
            row.recordKind,
            row.healthConnectUid,
            row.dedupeKey,
            row.sourcePackage,
            formatEpochMillis(row.recordStartEpochMillis),
            formatEpochMillis(row.recordEndEpochMillis),
            formatEpochMillis(row.valueStartEpochMillis),
            formatEpochMillis(row.valueEndEpochMillis),
            row.localDate,
            formatZoneOffset(row.zoneOffsetSeconds),
            row.metric,
            formatValue(row.numericValue, row.textValue, row.jsonValue),
            formatDouble(row.secondaryNumericValue),
            row.unit,
            row.categoryOrStage,
            row.label,
            row.textValue,
            row.jsonValue,
            row.metadataJson,
            row.rawJson
        )
    )

    fun aggregateCsvLine(row: HealthAggregateCsvRow, exportedAt: Instant): String = csvLine(
        listOf(
            AGGREGATE_CSV_SCHEMA_VERSION,
            CSV_TIME_FMT.format(exportedAt),
            row.localId.toString(),
            row.recordType,
            row.metric,
            row.bucketPeriod,
            formatEpochMillis(row.bucketStartEpochMillis),
            formatEpochMillis(row.bucketEndEpochMillis),
            row.localDate,
            row.timezoneId,
            row.source,
            formatDouble(row.value),
            row.unit,
            formatEpochMillis(row.computedEpochMillis),
            formatEpochMillis(row.requestedStartEpochMillis),
            formatEpochMillis(row.requestedEndEpochMillis),
            row.rawJson
        )
    )

    fun formatEpochMillis(epochMillis: Long?): String? =
        epochMillis?.let { CSV_TIME_FMT.format(Instant.ofEpochMilli(it)) }

    fun formatZoneOffset(seconds: Int?): String? =
        seconds?.let { ZoneOffset.ofTotalSeconds(it).toString() }

    fun formatValue(
        numericValue: Double?,
        textValue: String?,
        jsonValue: String?
    ): String? = formatDouble(numericValue) ?: textValue ?: jsonValue

    fun formatDouble(value: Double?): String? =
        value?.let {
            if (it % 1.0 == 0.0) it.toLong().toString() else it.toString()
        }

    fun csvLine(values: List<String?>): String =
        values.joinToString(separator = ",") { csvEscape(it.orEmpty()) }

    fun csvEscape(value: String): String {
        val mustQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!mustQuote) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    fun safeFileStem(raw: String): String = buildString {
        for (char in raw.lowercase()) {
            append(
                when {
                    char in 'a'..'z' || char in '0'..'9' -> char
                    char == '-' || char == '_' -> char
                    else -> '_'
                }
            )
        }
    }.ifBlank { "unknown_record_type" }

}
