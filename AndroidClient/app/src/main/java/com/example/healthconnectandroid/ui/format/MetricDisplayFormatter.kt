package com.example.healthconnectandroid.ui.format

import com.example.healthconnectandroid.UnitSystemPreference
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object MetricDisplayFormatter {
    private val integerFormatter: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
    private val oneDecimalFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 1
    }
    private val twoDecimalFormatter: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }
    private val shortDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M/d HH:mm").withZone(ZoneId.systemDefault())

    fun formatCount(value: Int): String = integerFormatter.format(value)

    fun formatRecordCount(value: Int): String =
        if (value == 1) "1 record" else "${formatCount(value)} records"

    fun formatDistance(
        meters: Double,
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
    ): String =
        if (unitSystem == UnitSystemPreference.IMPERIAL) {
            val feet = meters * 3.280839895
            val miles = meters / 1609.344
            if (abs(miles) >= 0.1) {
                "${twoDecimalFormatter.format(miles)} mi"
            } else {
                "${integerFormatter.format(feet.roundToInt())} ft"
            }
        } else if (abs(meters) >= 1000.0) {
            "${twoDecimalFormatter.format(meters / 1000.0)} km"
        } else {
            "${integerFormatter.format(meters.roundToInt())} m"
        }

    fun formatEnergy(kcal: Double): String =
        "${integerFormatter.format(kcal.roundToInt())} kcal"

    fun formatWeight(
        kg: Double,
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
    ): String =
        if (unitSystem == UnitSystemPreference.IMPERIAL) {
            "${oneDecimalFormatter.format(kg * 2.2046226218)} lb"
        } else {
            "${oneDecimalFormatter.format(kg)} kg"
        }

    fun formatPercent(percent: Double): String =
        "${oneDecimalFormatter.format(percent)}%"

    fun formatBpm(bpm: Double): String =
        "${integerFormatter.format(bpm.roundToInt())} bpm"

    fun formatMeasurement(
        value: Double,
        unit: String?,
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
    ): String {
        val normalizedUnit = unit?.trim().orEmpty()
        if (unitSystem == UnitSystemPreference.IMPERIAL) {
            when {
                normalizedUnit.equals("kg", ignoreCase = true) ->
                    return formatWeight(value, unitSystem)
                normalizedUnit.equals("m", ignoreCase = true) ||
                    normalizedUnit.equals("meter", ignoreCase = true) ||
                    normalizedUnit.equals("meters", ignoreCase = true) ->
                    return formatDistance(value, unitSystem)
                normalizedUnit.equals("c", ignoreCase = true) ||
                    normalizedUnit.equals("°c", ignoreCase = true) ||
                    normalizedUnit.contains("celsius", ignoreCase = true) ->
                    return "${oneDecimalFormatter.format((value * 9.0 / 5.0) + 32.0)} F"
            }
        }
        val number = when {
            normalizedUnit.equals("bpm", ignoreCase = true) ->
                integerFormatter.format(value.roundToInt())
            normalizedUnit.equals("kcal", ignoreCase = true) ->
                integerFormatter.format(value.roundToInt())
            normalizedUnit.equals("count", ignoreCase = true) ->
                integerFormatter.format(value.roundToInt())
            normalizedUnit.equals("mmHg", ignoreCase = true) ->
                integerFormatter.format(value.roundToInt())
            normalizedUnit.equals("kg", ignoreCase = true) ->
                oneDecimalFormatter.format(value)
            normalizedUnit.equals("%", ignoreCase = true) ||
                normalizedUnit.equals("percent", ignoreCase = true) ->
                oneDecimalFormatter.format(value)
            normalizedUnit.contains("c", ignoreCase = true) ||
                normalizedUnit.contains("fahrenheit", ignoreCase = true) ->
                oneDecimalFormatter.format(value)
            abs(value % 1.0) < 0.0001 ->
                integerFormatter.format(value.roundToInt())
            else -> twoDecimalFormatter.format(value)
        }
        return if (normalizedUnit.isBlank()) number else "$number $normalizedUnit"
    }

    fun convertMeasurementValue(
        recordTypeKey: String,
        value: Double,
        unit: String?,
        unitSystem: UnitSystemPreference
    ): Pair<Double, String?> {
        if (unitSystem == UnitSystemPreference.METRIC) return value to unit
        val normalizedUnit = unit?.trim().orEmpty()
        return when {
            recordTypeKey.endsWith("distance", ignoreCase = true) ||
                normalizedUnit.equals("m", ignoreCase = true) ||
                normalizedUnit.equals("meter", ignoreCase = true) ||
                normalizedUnit.equals("meters", ignoreCase = true) ->
                value / 1609.344 to "mi"
            recordTypeKey.endsWith("weight", ignoreCase = true) ||
                normalizedUnit.equals("kg", ignoreCase = true) ->
                value * 2.2046226218 to "lb"
            recordTypeKey.endsWith("body_temperature", ignoreCase = true) ||
                normalizedUnit.equals("c", ignoreCase = true) ||
                normalizedUnit.equals("°c", ignoreCase = true) ||
                normalizedUnit.contains("celsius", ignoreCase = true) ->
                (value * 9.0 / 5.0) + 32.0 to "F"
            else -> value to unit
        }
    }

    fun formatMeasurementForRecordType(
        recordTypeKey: String,
        value: Double,
        unit: String?,
        unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC
    ): String {
        val converted = convertMeasurementValue(recordTypeKey, value, unit, unitSystem)
        return formatMeasurement(converted.first, converted.second, UnitSystemPreference.METRIC)
    }

    fun formatDurationCompact(duration: Duration): String {
        val minutes = duration.toMinutes().coerceAtLeast(0)
        val hours = minutes / 60
        val rem = minutes % 60
        return when {
            hours > 0 && rem > 0 -> "${hours}h ${rem}m"
            hours > 0 -> "${hours}h"
            else -> "${rem}m"
        }
    }

    fun formatShortInstant(value: Instant?, zoneId: ZoneId = ZoneId.systemDefault()): String =
        value?.let { shortDateTimeFormatter.withZone(zoneId).format(it) } ?: "Never"
}
