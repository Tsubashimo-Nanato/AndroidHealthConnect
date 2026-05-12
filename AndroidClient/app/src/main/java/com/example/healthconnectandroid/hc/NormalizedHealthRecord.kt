package com.example.healthconnectandroid.hc

import androidx.health.connect.client.records.metadata.Metadata
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

enum class NormalizedRecordKind(val id: String) {
    SCALAR_MEASUREMENT("scalar_measurement"),
    SAMPLED_SERIES("sampled_series"),
    INTERVAL("interval"),
    SESSION("session"),
    MULTI_VALUE("multi_value"),
    AGGREGATE("aggregate")
}

data class NormalizedHealthRecord(
    val uid: String?,
    val typeKey: String,
    val kind: NormalizedRecordKind,
    val startTime: Instant,
    val endTime: Instant?,
    val startZoneOffsetSeconds: Int?,
    val endZoneOffsetSeconds: Int?,
    val sourcePackage: String?,
    val metadataJson: String?,
    val rawJson: String?,
    val values: List<NormalizedHealthValue>
)

data class NormalizedHealthValue(
    val metric: String,
    val unit: String?,
    val label: String? = null,
    val category: String? = null,
    val valueFloat: Double? = null,
    val secondaryValueFloat: Double? = null,
    val valueInt: Long? = null,
    val valueText: String? = null,
    val valueJson: String? = null,
    val sampleTime: Instant? = null,
    val endTime: Instant? = null,
    val sequence: Int? = null
) {
    companion object {
        fun floating(
            metric: String,
            value: Double,
            unit: String,
            label: String? = null,
            category: String? = null,
            sampleTime: Instant? = null,
            endTime: Instant? = null,
            sequence: Int? = null
        ) = NormalizedHealthValue(
            metric = metric,
            unit = unit,
            label = label,
            category = category,
            valueFloat = value,
            sampleTime = sampleTime,
            endTime = endTime,
            sequence = sequence
        )

        fun paired(
            metric: String,
            value: Double,
            secondaryValue: Double,
            unit: String,
            label: String? = null,
            category: String? = null,
            sampleTime: Instant? = null,
            endTime: Instant? = null,
            sequence: Int? = null
        ) = NormalizedHealthValue(
            metric = metric,
            unit = unit,
            label = label,
            category = category,
            valueFloat = value,
            secondaryValueFloat = secondaryValue,
            sampleTime = sampleTime,
            endTime = endTime,
            sequence = sequence
        )

        fun integer(
            metric: String,
            value: Long,
            unit: String,
            label: String? = null,
            category: String? = null,
            sampleTime: Instant? = null,
            endTime: Instant? = null,
            sequence: Int? = null
        ) = NormalizedHealthValue(
            metric = metric,
            unit = unit,
            label = label,
            category = category,
            valueInt = value,
            sampleTime = sampleTime,
            endTime = endTime,
            sequence = sequence
        )

        fun text(
            metric: String,
            value: String,
            label: String? = null,
            category: String? = null,
            sampleTime: Instant? = null,
            endTime: Instant? = null,
            sequence: Int? = null
        ) = NormalizedHealthValue(
            metric = metric,
            unit = null,
            label = label,
            category = category,
            valueText = value,
            sampleTime = sampleTime,
            endTime = endTime,
            sequence = sequence
        )

        fun json(
            metric: String,
            value: String,
            label: String? = null,
            category: String? = null,
            sampleTime: Instant? = null,
            endTime: Instant? = null,
            sequence: Int? = null
        ) = NormalizedHealthValue(
            metric = metric,
            unit = null,
            label = label,
            category = category,
            valueJson = value,
            sampleTime = sampleTime,
            endTime = endTime,
            sequence = sequence
        )
    }
}

internal fun NormalizedHealthRecord.localDateString(): String {
    val offset = startZoneOffsetSeconds?.let(ZoneOffset::ofTotalSeconds) ?: ZoneOffset.UTC
    return LocalDate.ofInstant(startTime, offset).toString()
}

internal fun NormalizedHealthValue.localDateString(record: NormalizedHealthRecord): String {
    val offset = record.startZoneOffsetSeconds?.let(ZoneOffset::ofTotalSeconds) ?: ZoneOffset.UTC
    return LocalDate.ofInstant(sampleTime ?: record.startTime, offset).toString()
}

internal fun NormalizedHealthRecord.dedupeKey(): String {
    val uidPart = uid?.takeIf { it.isNotBlank() }
    if (uidPart != null) {
        return "uid:${sourcePackage.orEmpty()}:$uidPart"
    }

    return "hash:${sha256Hex(canonicalDedupeInput())}"
}

internal fun NormalizedHealthValue.valueKey(): String =
    sha256Hex(
        listOf(
            metric,
            unit.orEmpty(),
            label.orEmpty(),
            category.orEmpty(),
            valueFloat?.toString().orEmpty(),
            secondaryValueFloat?.toString().orEmpty(),
            valueInt?.toString().orEmpty(),
            valueText.orEmpty(),
            valueJson.orEmpty(),
            sampleTime?.toEpochMilli()?.toString().orEmpty(),
            endTime?.toEpochMilli()?.toString().orEmpty(),
            sequence?.toString().orEmpty()
        ).joinToString("|")
    )

private fun NormalizedHealthRecord.canonicalDedupeInput(): String =
    buildString {
        append(typeKey).append('|')
        append(kind.id).append('|')
        append(startTime.toEpochMilli()).append('|')
        append(endTime?.toEpochMilli() ?: "").append('|')
        append(startZoneOffsetSeconds ?: "").append('|')
        append(endZoneOffsetSeconds ?: "").append('|')
        append(sourcePackage.orEmpty()).append('|')
        values.forEach { value ->
            append(value.metric).append(':')
            append(value.unit.orEmpty()).append(':')
            append(value.label.orEmpty()).append(':')
            append(value.category.orEmpty()).append(':')
            append(value.valueFloat?.toString().orEmpty()).append(':')
            append(value.secondaryValueFloat?.toString().orEmpty()).append(':')
            append(value.valueInt?.toString().orEmpty()).append(':')
            append(value.valueText.orEmpty()).append(':')
            append(value.valueJson.orEmpty()).append(':')
            append(value.sampleTime?.toEpochMilli()?.toString().orEmpty()).append(':')
            append(value.endTime?.toEpochMilli()?.toString().orEmpty()).append(':')
            append(value.sequence?.toString().orEmpty()).append('|')
        }
    }

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

internal fun Metadata.healthRecordUid(): String? =
    id.ifBlank { clientRecordId.orEmpty() }.ifBlank { null }

internal fun Metadata.sourcePackageName(): String? =
    dataOrigin.packageName.ifBlank { null }

internal fun Metadata.toMetadataJson(): String = jsonObject(
    "id" to id,
    "dataOriginPackage" to dataOrigin.packageName,
    "lastModifiedTime" to lastModifiedTime.toString(),
    "clientRecordId" to clientRecordId,
    "clientRecordVersion" to clientRecordVersion,
    "recordingMethod" to recordingMethod,
    "deviceType" to device?.type,
    "deviceManufacturer" to device?.manufacturer,
    "deviceModel" to device?.model
)

internal fun ZoneOffset?.totalSecondsOrNull(): Int? = this?.totalSeconds

internal fun jsonObject(vararg fields: Pair<String, Any?>): String =
    fields.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        "\"${escapeJson(key)}\":${jsonValue(value)}"
    }

internal fun jsonArray(items: List<String>): String =
    items.joinToString(prefix = "[", postfix = "]")

private fun jsonValue(value: Any?): String = when (value) {
    null -> "null"
    is Number, is Boolean -> value.toString()
    else -> "\"${escapeJson(value.toString())}\""
}

private fun escapeJson(value: String): String = buildString {
    for (char in value) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
