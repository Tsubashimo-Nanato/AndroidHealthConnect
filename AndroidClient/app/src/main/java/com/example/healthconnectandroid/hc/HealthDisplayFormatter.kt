package com.example.healthconnectandroid.hc

import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.data.HealthCsvRow
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject

data class ReadableDetailField(
    val label: String,
    val value: String
)

data class ReadableHealthRecord(
    val rowKey: String,
    val localRecordId: Long,
    val localValueId: Long,
    val recordTypeKey: String,
    val metric: String,
    val displayName: String,
    val primaryText: String,
    val secondaryText: String,
    val value: Double?,
    val value2: Double?,
    val unit: String?,
    val startTime: Instant?,
    val endTime: Instant?,
    val localDate: String?,
    val durationText: String?,
    val sourceText: String?,
    val metadataText: String?,
    val rawDetailsText: String?,
    val detailFields: List<ReadableDetailField>,
    val visualizationType: VisualizationType
)

object HealthDisplayFormatter {
    private val localDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")

    fun toReadable(
        row: HealthCsvRow,
        descriptor: HealthDataTypeDescriptor,
        includeRawDetails: Boolean = false,
        zoneId: ZoneId = ZoneId.systemDefault(),
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
    ): ReadableHealthRecord {
        val stageInfo = sleepStageInfo(row)
        val startTime = stageInfo?.startTime
            ?: row.valueStartEpochMillis?.let(Instant::ofEpochMilli)
            ?: Instant.ofEpochMilli(row.recordStartEpochMillis)
        val endTime = stageInfo?.endTime
            ?: row.valueEndEpochMillis?.let(Instant::ofEpochMilli)
            ?: row.recordEndEpochMillis?.let(Instant::ofEpochMilli)
        val value = row.numericValue
        val value2 = row.secondaryNumericValue
        val unit = displayUnit(row.recordType, row.unit)
        val durationText = durationText(row, startTime, endTime)
        val sourceText = sourceText(row.sourcePackage)
        val primaryText = primaryText(row, value, value2, unit, durationText, stageInfo, unitSystem)
        val secondaryText = listOfNotNull(
            timeText(startTime, endTime, durationText, zoneId),
            startTime?.atZone(zoneId)?.toLocalDate()?.let { "Local date $it" },
            sourceText
        ).joinToString(" | ")
        val detailFields = if (includeRawDetails) {
            detailFields(
                row = row,
                startTime = startTime,
                endTime = endTime,
                durationText = durationText,
                sourceText = sourceText,
                stageInfo = stageInfo,
                zoneId = zoneId,
                unitSystem = unitSystem
            )
        } else {
            emptyList()
        }

        return ReadableHealthRecord(
            rowKey = "${row.localRecordId}:${row.localValueId}:${row.valueKey}",
            localRecordId = row.localRecordId,
            localValueId = row.localValueId,
            recordTypeKey = row.recordType,
            metric = row.metric,
            displayName = descriptor.displayName,
            primaryText = primaryText,
            secondaryText = secondaryText,
            value = value,
            value2 = value2,
            unit = unit,
            startTime = startTime,
            endTime = endTime,
            localDate = row.localDate,
            durationText = durationText,
            sourceText = sourceText,
            metadataText = if (includeRawDetails) metadataText(row) else null,
            rawDetailsText = if (includeRawDetails) rawDetailsText(row) else null,
            detailFields = detailFields,
            visualizationType = descriptor.visualizationType
        )
    }

    fun formatInstantForUi(value: Instant?, zoneId: ZoneId = ZoneId.systemDefault()): String =
        value?.let { localDateTimeFormatter.withZone(zoneId).format(it) }.orEmpty()

    fun formatDurationForUi(duration: Duration): String = formatDuration(duration)

    fun sleepStageDisplayName(rawStage: String?, stageCode: Int? = null): String {
        val normalized = rawStage
            ?.trim()
            ?.lowercase(Locale.US)
            ?.replace('-', '_')
            ?.replace(' ', '_')
        return when {
            normalized == "awake" || stageCode == 1 -> "Awake"
            normalized == "sleeping" || stageCode == 2 -> "Sleeping"
            normalized == "out_of_bed" || stageCode == 3 -> "Out of bed"
            normalized == "light" || normalized == "light_sleep" || stageCode == 4 -> "Light sleep"
            normalized == "deep" || normalized == "deep_sleep" || stageCode == 5 -> "Deep sleep"
            normalized == "rem" || normalized == "rem_sleep" || stageCode == 6 -> "REM"
            normalized == "awake_in_bed" || stageCode == 7 -> "Awake in bed"
            normalized.isNullOrBlank() -> "Unknown sleep stage"
            else -> "Unmapped sleep stage (${rawStage.orEmpty()})"
        }
    }

    private fun primaryText(
        row: HealthCsvRow,
        value: Double?,
        value2: Double?,
        unit: String?,
        durationText: String?,
        stageInfo: SleepStageInfo?,
        unitSystem: UnitSystemPreference
    ): String {
        val number = value?.let(::formatNumber)
        return when (row.recordType) {
            HealthDataTypeKeys.HEART_RATE -> number?.let { "$it bpm" } ?: fallbackValue(row)
            HealthDataTypeKeys.WEIGHT -> value?.let { formatWeight(it, unitSystem) } ?: fallbackValue(row, unitSystem)
            HealthDataTypeKeys.BODY_FAT -> number?.let { "$it%" } ?: fallbackValue(row)
            HealthDataTypeKeys.OXYGEN_SATURATION -> number?.let { "SpO2 $it%" } ?: fallbackValue(row)
            HealthDataTypeKeys.STEPS -> value?.let { "${formatIntegerish(it)} steps" } ?: fallbackValue(row)
            HealthDataTypeKeys.DISTANCE -> value?.let { formatDistance(it, unitSystem) } ?: fallbackValue(row, unitSystem)
            HealthDataTypeKeys.ACTIVE_CALORIES,
            HealthDataTypeKeys.TOTAL_CALORIES -> number?.let { "$it kcal" } ?: fallbackValue(row)
            HealthDataTypeKeys.BLOOD_PRESSURE -> bloodPressureText(row, value, value2, unit)
            HealthDataTypeKeys.BODY_TEMPERATURE -> bodyTemperatureText(row, value, unitSystem)
            HealthDataTypeKeys.RESPIRATORY_RATE -> number?.let { "$it breaths/min" } ?: fallbackValue(row)
            HealthDataTypeKeys.RESTING_HEART_RATE -> number?.let { "$it bpm" } ?: fallbackValue(row)
            HealthDataTypeKeys.SLEEP_SESSION -> sleepPrimaryText(row, durationText, stageInfo)
            else -> fallbackValue(row, unitSystem)
        }
    }

    private fun bloodPressureText(
        row: HealthCsvRow,
        value: Double?,
        value2: Double?,
        unit: String?
    ): String = when (row.metric) {
        "blood_pressure" -> {
            if (value != null && value2 != null) {
                "${formatNumber(value)}/${formatNumber(value2)} ${unit.orEmpty()}".trim()
            } else {
                fallbackValue(row)
            }
        }
        "systolic" -> value?.let { "Systolic ${formatNumber(it)} mmHg" } ?: fallbackValue(row)
        "diastolic" -> value?.let { "Diastolic ${formatNumber(it)} mmHg" } ?: fallbackValue(row)
        "body_position" -> value?.let { "Body position code ${formatIntegerish(it)}" } ?: fallbackValue(row)
        "measurement_location" -> value?.let { "Measurement location code ${formatIntegerish(it)}" }
            ?: fallbackValue(row)
        else -> fallbackValue(row)
    }

    private fun bodyTemperatureText(
        row: HealthCsvRow,
        value: Double?,
        unitSystem: UnitSystemPreference
    ): String = when (row.metric) {
        "body_temperature" -> value?.let { formatTemperatureCelsius(it, unitSystem) } ?: fallbackValue(row, unitSystem)
        "measurement_location" -> row.numericValue?.let {
            "Measurement location code ${formatIntegerish(it)}"
        } ?: fallbackValue(row)
        else -> fallbackValue(row)
    }

    private fun sleepPrimaryText(
        row: HealthCsvRow,
        durationText: String?,
        stageInfo: SleepStageInfo?
    ): String =
        when (row.metric) {
            "duration" -> {
                val stageCount = sleepStageCount(row)
                buildList {
                    add("Sleep session")
                    durationText?.let { add(it) }
                    stageCount?.let { add("$it stages") }
                }.joinToString(" - ")
            }
            "sleep_stage" -> {
                val stageName = stageInfo?.label
                    ?: sleepStageDisplayName(row.categoryOrStage ?: row.label)
                listOfNotNull(stageName, durationText).joinToString(" - ")
            }
            "title" -> row.textValue?.let { "Sleep title: $it" } ?: fallbackValue(row)
            "notes" -> row.textValue?.let { "Sleep notes: $it" } ?: fallbackValue(row)
            else -> formatValueWithUnit(row)
        }

    private fun fallbackValue(
        row: HealthCsvRow,
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
    ): String =
        formatValueWithUnit(row, unitSystem).ifBlank { row.metric.ifBlank { row.recordType } }

    private fun formatValueWithUnit(
        row: HealthCsvRow,
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
    ): String {
        val unit = displayUnit(row.recordType, row.unit)
        val value = row.numericValue?.let { formatDisplayNumber(row.recordType, it, unitSystem) }
            ?: row.textValue
            ?: readableJsonSummary(row.jsonValue)
            ?: row.valueKey
        val secondary = row.secondaryNumericValue?.let { "/${formatNumber(it)}" }.orEmpty()
        val displayUnit = displayUnitFor(row.recordType, unit, unitSystem).orEmpty()
        return "$value$secondary $displayUnit".trim()
    }

    private fun displayUnit(recordType: String, rawUnit: String?): String? =
        when {
            recordType == HealthDataTypeKeys.BODY_FAT -> "%"
            recordType == HealthDataTypeKeys.OXYGEN_SATURATION -> "%"
            rawUnit == "percent" -> "%"
            rawUnit == "count" -> null
            rawUnit == "breaths_per_minute" -> "breaths/min"
            rawUnit.isNullOrBlank() -> null
            else -> rawUnit
        }

    private fun formatDistance(meters: Double, unitSystem: UnitSystemPreference): String =
        if (unitSystem == UnitSystemPreference.IMPERIAL) {
            val miles = meters / 1609.344
            val feet = meters * 3.280839895
            if (abs(miles) >= 0.1) {
                "${formatNumber(miles)} mi"
            } else {
                "${formatNumber(feet)} ft"
            }
        } else if (abs(meters) >= 1000.0) {
            "${formatNumber(meters / 1000.0)} km"
        } else {
            "${formatNumber(meters)} m"
        }

    private fun formatWeight(kg: Double, unitSystem: UnitSystemPreference): String =
        if (unitSystem == UnitSystemPreference.IMPERIAL) {
            "${formatNumber(kg * 2.2046226218)} lb"
        } else {
            "${formatNumber(kg)} kg"
        }

    private fun formatTemperatureCelsius(celsius: Double, unitSystem: UnitSystemPreference): String =
        if (unitSystem == UnitSystemPreference.IMPERIAL) {
            "${formatNumber((celsius * 9.0 / 5.0) + 32.0)} F"
        } else {
            "${formatNumber(celsius)} C"
        }

    private fun formatDisplayNumber(
        recordType: String,
        value: Double,
        unitSystem: UnitSystemPreference
    ): String =
        when {
            recordType == HealthDataTypeKeys.DISTANCE -> {
                val converted = if (unitSystem == UnitSystemPreference.IMPERIAL) value / 1609.344 else value
                formatNumber(converted)
            }
            recordType == HealthDataTypeKeys.WEIGHT -> {
                val converted = if (unitSystem == UnitSystemPreference.IMPERIAL) value * 2.2046226218 else value
                formatNumber(converted)
            }
            recordType == HealthDataTypeKeys.BODY_TEMPERATURE -> {
                val converted = if (unitSystem == UnitSystemPreference.IMPERIAL) (value * 9.0 / 5.0) + 32.0 else value
                formatNumber(converted)
            }
            else -> formatNumber(value)
        }

    private fun displayUnitFor(recordType: String, unit: String?, unitSystem: UnitSystemPreference): String? =
        if (unitSystem == UnitSystemPreference.IMPERIAL) {
            when (recordType) {
                HealthDataTypeKeys.DISTANCE -> "mi"
                HealthDataTypeKeys.WEIGHT -> "lb"
                HealthDataTypeKeys.BODY_TEMPERATURE -> "F"
                else -> unit
            }
        } else {
            unit
        }

    private fun timeText(
        startTime: Instant?,
        endTime: Instant?,
        durationText: String?,
        zoneId: ZoneId
    ): String? {
        val formatter = localDateTimeFormatter.withZone(zoneId)
        val start = startTime?.let(formatter::format) ?: return null
        val end = endTime?.let(formatter::format)
        return when {
            end != null && end != start && durationText != null -> "$start to $end ($durationText)"
            end != null && end != start -> "$start to $end"
            else -> start
        }
    }

    private fun durationText(row: HealthCsvRow, startTime: Instant?, endTime: Instant?): String? {
        if (row.metric == "duration" && row.numericValue != null && row.unit == "minutes") {
            return formatDuration(Duration.ofMinutes(row.numericValue.toLong()))
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) return null
        return formatDuration(Duration.between(startTime, endTime))
    }

    private fun formatDuration(duration: Duration): String {
        val minutes = duration.toMinutes().coerceAtLeast(0)
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return when {
            hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
            hours > 0 -> "${hours}h"
            else -> "${remainingMinutes}m"
        }
    }

    private fun sourceText(sourcePackage: String?): String? =
        sourcePackage?.takeIf { it.isNotBlank() }?.let { packageName ->
            val appName = when (packageName) {
                "com.fitbit.FitbitMobile" -> "Fitbit"
                "com.oceanwing.smarthome" -> "Oceanwing Smart Home"
                "android" -> "Android"
                else -> packageName.substringAfterLast('.')
                    .replaceFirstChar { it.uppercase() }
            }
            "Source: $appName"
        }

    private fun metadataText(row: HealthCsvRow): String? =
        row.metadataJson
            ?.takeIf { it.isNotBlank() }
            ?.let { prettyKeyValueText(it, maxFields = 8) }
            ?: row.healthConnectUid?.takeIf { it.isNotBlank() }?.let { "Health Connect ID: $it" }

    private fun rawDetailsText(row: HealthCsvRow): String? =
        listOfNotNull(
            "Record type: ${row.recordType}",
            "Metric: ${row.metric}",
            row.categoryOrStage?.takeIf { it.isNotBlank() }?.let { "Category/stage: ${readableLabel(it)}" },
            row.label?.takeIf { it.isNotBlank() }?.let { "Label: ${readableLabel(it)}" },
            row.textValue?.takeIf { it.isNotBlank() }?.let { "Text value: $it" },
            row.jsonValue?.takeIf { it.isNotBlank() }?.let { "Value details:\n${prettyJsonOrRaw(it)}" },
            row.metadataJson?.takeIf { it.isNotBlank() }?.let { "Metadata:\n${prettyJsonOrRaw(it)}" },
            row.rawJson?.takeIf { it.isNotBlank() }?.let { "Raw record:\n${prettyJsonOrRaw(it)}" }
        ).joinToString("\n\n").ifBlank { null }

    private fun detailFields(
        row: HealthCsvRow,
        startTime: Instant?,
        endTime: Instant?,
        durationText: String?,
        sourceText: String?,
        stageInfo: SleepStageInfo?,
        zoneId: ZoneId,
        unitSystem: UnitSystemPreference
    ): List<ReadableDetailField> = buildList {
        val formatter = localDateTimeFormatter.withZone(zoneId)
        add(ReadableDetailField("Display type", row.recordType))
        add(ReadableDetailField("Metric", row.metric))
        row.healthConnectUid?.takeIf { it.isNotBlank() }
            ?.let { add(ReadableDetailField("Health Connect ID", it)) }
        sourceText?.let { add(ReadableDetailField("Source", it.removePrefix("Source: "))) }
        startTime?.let { add(ReadableDetailField("Start", formatter.format(it))) }
        endTime?.let { add(ReadableDetailField("End", formatter.format(it))) }
        durationText?.let { add(ReadableDetailField("Duration", it)) }
        startTime?.atZone(zoneId)?.toLocalDate()?.let { add(ReadableDetailField("Local date", it.toString())) }
        row.zoneOffsetSeconds?.let { add(ReadableDetailField("Zone offset seconds", it.toString())) }
        row.numericValue?.let {
            add(
                ReadableDetailField(
                    "Display value",
                    formatValueWithUnit(row.copy(numericValue = it), unitSystem)
                )
            )
            add(ReadableDetailField("Stored value", "${formatNumber(it)} ${displayUnit(row.recordType, row.unit).orEmpty()}".trim()))
        }
        row.secondaryNumericValue?.let { add(ReadableDetailField("Secondary value", formatNumber(it))) }
        displayUnit(row.recordType, row.unit)?.let { add(ReadableDetailField("Unit", it)) }
        stageInfo?.let {
            add(ReadableDetailField("Sleep stage", it.label))
            it.rawStage?.let { raw -> add(ReadableDetailField("Raw stage", raw)) }
            it.stageCode?.let { code -> add(ReadableDetailField("Stage code", code.toString())) }
        }
        if (row.metric == "duration") {
            sleepStageCount(row)?.let { add(ReadableDetailField("Stage count", it.toString())) }
        }
        row.textValue?.takeIf { it.isNotBlank() }
            ?.let { add(ReadableDetailField("Text", it)) }
        addAll(jsonFields("Value", row.jsonValue, maxFields = 12))
        addAll(jsonFields("Metadata", row.metadataJson, maxFields = 12))
    }

    private data class SleepStageInfo(
        val label: String,
        val rawStage: String?,
        val stageCode: Int?,
        val startTime: Instant?,
        val endTime: Instant?
    )

    private fun sleepStageInfo(row: HealthCsvRow): SleepStageInfo? {
        if (row.recordType != HealthDataTypeKeys.SLEEP_SESSION || row.metric != "sleep_stage") {
            return null
        }
        val obj = parseObject(row.jsonValue)
        val rawStage = obj?.optStringOrNull("stage") ?: row.categoryOrStage ?: row.label
        val stageCode = obj?.optIntOrNull("stageCode") ?: rawStage?.toIntOrNull()
        val start = obj?.optStringOrNull("startTime")?.parseInstantOrNull()
            ?: row.valueStartEpochMillis?.let(Instant::ofEpochMilli)
        val end = obj?.optStringOrNull("endTime")?.parseInstantOrNull()
            ?: row.valueEndEpochMillis?.let(Instant::ofEpochMilli)
        return SleepStageInfo(
            label = sleepStageDisplayName(rawStage, stageCode),
            rawStage = rawStage,
            stageCode = stageCode,
            startTime = start,
            endTime = end
        )
    }

    private fun sleepStageCount(row: HealthCsvRow): Int? =
        parseObject(row.rawJson)?.optIntOrNull("stageCount")

    private fun readableJsonSummary(raw: String?): String? {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val obj = parseObject(trimmed)
        if (obj != null) {
            obj.optStringOrNull("stage")?.let { stage ->
                return sleepStageDisplayName(stage, obj.optIntOrNull("stageCode"))
            }
            return "${obj.length()} fields"
        }
        val arr = parseArray(trimmed)
        if (arr != null) return "${arr.length()} items"
        return trimmed
    }

    private fun prettyKeyValueText(raw: String, maxFields: Int): String? {
        val fields = jsonFields(prefix = null, raw = raw, maxFields = maxFields)
        return fields.takeIf { it.isNotEmpty() }
            ?.joinToString("\n") { "${it.label}: ${it.value}" }
    }

    private fun jsonFields(
        prefix: String?,
        raw: String?,
        maxFields: Int
    ): List<ReadableDetailField> {
        val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val obj = parseObject(trimmed)
        if (obj != null) return flattenJsonObject(prefix, obj, maxFields)
        val arr = parseArray(trimmed)
        if (arr != null) {
            return listOf(ReadableDetailField(label(prefix ?: "JSON"), "${arr.length()} items"))
        }
        return emptyList()
    }

    private fun flattenJsonObject(
        prefix: String?,
        obj: JSONObject,
        maxFields: Int
    ): List<ReadableDetailField> {
        val fields = mutableListOf<ReadableDetailField>()
        val keys = obj.keys()
        while (keys.hasNext() && fields.size < maxFields) {
            val key = keys.next()
            val value = obj.opt(key)
            val label = listOfNotNull(prefix, label(key)).joinToString(" ")
            when (value) {
                is JSONObject -> {
                    fields += flattenJsonObject(label, value, maxFields - fields.size)
                }
                is JSONArray -> {
                    fields += ReadableDetailField(label, "${value.length()} items")
                }
                is String -> {
                    val nestedArray = parseArray(value)
                    val nestedObject = parseObject(value)
                    fields += when {
                        nestedArray != null -> ReadableDetailField(label, "${nestedArray.length()} items")
                        nestedObject != null -> ReadableDetailField(label, "${nestedObject.length()} fields")
                        else -> ReadableDetailField(label, value)
                    }
                }
                JSONObject.NULL -> fields += ReadableDetailField(label, "None")
                else -> fields += ReadableDetailField(label, value?.toString().orEmpty())
            }
        }
        return fields
    }

    private fun prettyJsonOrRaw(raw: String): String {
        val trimmed = raw.trim()
        parseObject(trimmed)?.let { return it.toString(2) }
        parseArray(trimmed)?.let { return it.toString(2) }
        return trimmed
    }

    private fun parseObject(raw: String?): JSONObject? {
        val trimmed = raw?.trim() ?: return null
        if (!trimmed.startsWith("{")) return null
        return runCatching { JSONObject(trimmed) }.getOrNull()
    }

    private fun parseArray(raw: String?): JSONArray? {
        val trimmed = raw?.trim() ?: return null
        if (!trimmed.startsWith("[")) return null
        return runCatching { JSONArray(trimmed) }.getOrNull()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) runCatching { getInt(key) }.getOrNull() else null

    private fun String.parseInstantOrNull(): Instant? =
        runCatching { Instant.parse(this) }.getOrNull()

    private fun readableLabel(value: String): String =
        when {
            value.equals("rem", ignoreCase = true) -> "REM"
            value.contains('_') -> value.split('_')
                .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
            else -> value.replaceFirstChar { it.uppercase() }
        }

    private fun label(value: String): String =
        value.replace('_', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }

    private fun formatIntegerish(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else formatNumber(value)

    private fun formatNumber(value: Double): String =
        when {
            value % 1.0 == 0.0 -> value.toLong().toString()
            abs(value) >= 100.0 -> String.format(Locale.US, "%.1f", value)
            else -> String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
        }
}
