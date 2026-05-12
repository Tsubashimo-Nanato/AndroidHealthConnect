package com.example.healthconnectandroid.ui.charts

import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.data.HealthDailyAggregateRow
import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.InspectorChartPoint
import com.example.healthconnectandroid.hc.VisualizationType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

data class ChartLabels(
    val title: String,
    val xAxisLabel: String,
    val yAxisLabel: String
)

fun InspectorChartPoint.toDisplayUnits(
    recordTypeKey: String,
    unitSystem: UnitSystemPreference
): InspectorChartPoint {
    val converted = convertChartValue(recordTypeKey, value, unit, unitSystem)
    val convertedSecond = value2?.let { convertChartValue(recordTypeKey, it, unit, unitSystem).first }
    if (converted.first == value && converted.second == unit && convertedSecond == value2) {
        return this
    }
    return copy(
        value = converted.first,
        value2 = convertedSecond,
        unit = converted.second,
        primaryText = null,
        secondaryText = secondaryText?.takeUnless { value2 != null }
    )
}

fun HealthDailyAggregateRow.toDisplayUnits(
    recordTypeKey: String,
    unitSystem: UnitSystemPreference
): HealthDailyAggregateRow {
    val converted = convertChartValue(recordTypeKey, total, unit, unitSystem)
    return if (converted.first == total && converted.second == unit) {
        this
    } else {
        copy(total = converted.first, unit = converted.second)
    }
}

fun convertChartValue(
    recordTypeKey: String,
    value: Double,
    unit: String?,
    unitSystem: UnitSystemPreference
): Pair<Double, String?> {
    if (unitSystem == UnitSystemPreference.METRIC) return value to unit
    val normalizedUnit = unit?.trim().orEmpty()
    return when {
        recordTypeKey == HealthDataTypeKeys.DISTANCE ||
            normalizedUnit.equals("m", ignoreCase = true) ||
            normalizedUnit.equals("meter", ignoreCase = true) ||
            normalizedUnit.equals("meters", ignoreCase = true) ->
            value / 1609.344 to "mi"
        recordTypeKey == HealthDataTypeKeys.WEIGHT ||
            normalizedUnit.equals("kg", ignoreCase = true) ->
            value * 2.2046226218 to "lb"
        recordTypeKey == HealthDataTypeKeys.BODY_TEMPERATURE ||
            normalizedUnit.equals("c", ignoreCase = true) ||
            normalizedUnit.equals("°c", ignoreCase = true) ||
            normalizedUnit.contains("celsius", ignoreCase = true) ->
            (value * 9.0 / 5.0) + 32.0 to "F"
        else -> value to unit
    }
}



fun chartLabelsFor(
    descriptor: HealthDataTypeDescriptor,
    unit: String?
): ChartLabels = when (descriptor.key) {
    HealthDataTypeKeys.HEART_RATE -> ChartLabels("Heart rate over time", "Time", "BPM")
    HealthDataTypeKeys.WEIGHT -> ChartLabels("Weight trend", "Date", "Weight (kg)")
    HealthDataTypeKeys.BODY_FAT -> ChartLabels("Body fat trend", "Date", "Body fat (%)")
    HealthDataTypeKeys.OXYGEN_SATURATION -> ChartLabels("SpO2 over time", "Time", "SpO2 (%)")
    HealthDataTypeKeys.STEPS -> ChartLabels("Steps per day", "Date", "Steps")
    HealthDataTypeKeys.ACTIVE_CALORIES -> ChartLabels("Active calories per day", "Date", "kcal")
    HealthDataTypeKeys.TOTAL_CALORIES -> ChartLabels("Total calories per day", "Date", "kcal")
    HealthDataTypeKeys.DISTANCE -> ChartLabels("Distance per day", "Date", "Distance (${unit ?: "m"})")
    HealthDataTypeKeys.SLEEP_SESSION -> ChartLabels("Sleep sessions", "Night/session", "Sleep stage")
    HealthDataTypeKeys.BLOOD_PRESSURE -> ChartLabels("Blood pressure", "Date/time", "mmHg")
    HealthDataTypeKeys.BODY_TEMPERATURE -> ChartLabels("Body temperature", "Date/time", "Temperature (C)")
    HealthDataTypeKeys.RESPIRATORY_RATE -> ChartLabels("Respiratory rate", "Date/time", "Breaths/min")
    HealthDataTypeKeys.RESTING_HEART_RATE -> ChartLabels("Resting heart rate", "Date/time", "BPM")
    else -> ChartLabels(descriptor.displayName, "Date/time", unit ?: "Value")
}

fun defaultChartBucketFor(descriptor: HealthDataTypeDescriptor): ChartBucket =
    if (descriptor.visualizationType == VisualizationType.DAILY_AGGREGATE) ChartBucket.DAILY else ChartBucket.RAW

fun chartXAxisLabel(defaultLabel: String, bucket: ChartBucket): String =
    when (bucket) {
        ChartBucket.RAW -> defaultLabel
        ChartBucket.HOURLY -> "Hour"
        ChartBucket.DAILY -> "Date"
        ChartBucket.WEEKLY -> "Week"
        ChartBucket.MONTHLY -> "Month"
    }

fun bucketChartPoints(
    points: List<InspectorChartPoint>,
    bucket: ChartBucket,
    zoneId: ZoneId,
    weekStart: DayOfWeek
): List<InspectorChartPoint> {
    if (bucket == ChartBucket.RAW || points.size <= 1) return points
    return points
        .groupBy { bucketStartEpochMillis(it.epochMillis, bucket, zoneId, weekStart) }
        .toSortedMap()
        .map { (bucketStart, group) ->
            val value = group.map { it.value }.average()
            val secondValues = group.mapNotNull { it.value2 }
            val value2 = secondValues.takeIf { it.isNotEmpty() }?.average()
            val unit = group.mapNotNull { it.unit }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            InspectorChartPoint(
                epochMillis = bucketStart,
                value = value,
                value2 = value2,
                label = bucket.label,
                unit = unit,
                sourceText = "Averaged from ${group.size} local points",
                primaryText = "${bucket.label} average: ${formatNumber(value)} ${unit.orEmpty()}".trim(),
                secondaryText = if (value2 == null) null else "Secondary average: ${formatNumber(value2)} ${unit.orEmpty()}".trim(),
                rawDetailsText = "Bucket: ${bucket.label}; points: ${group.size}"
            )
        }
}

fun bucketDailyTotals(
    rows: List<HealthDailyAggregateRow>,
    bucket: ChartBucket,
    weekStart: DayOfWeek
): List<HealthDailyAggregateRow> {
    if (bucket == ChartBucket.DAILY || rows.size <= 1) return rows
    return rows
        .groupBy { row ->
            val date = runCatching { LocalDate.parse(row.localDate) }.getOrNull()
            when (bucket) {
                ChartBucket.WEEKLY -> date
                    ?.with(TemporalAdjusters.previousOrSame(weekStart))
                    ?.toString()
                ChartBucket.MONTHLY -> date
                    ?.withDayOfMonth(1)
                    ?.toString()
                else -> row.localDate
            } ?: row.localDate
        }
        .toSortedMap()
        .map { (label, group) ->
            HealthDailyAggregateRow(
                localDate = label,
                total = group.sumOf { it.total },
                unit = group.mapNotNull { it.unit }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            )
        }
}

fun bucketStartEpochMillis(
    epochMillis: Long,
    bucket: ChartBucket,
    zoneId: ZoneId,
    weekStart: DayOfWeek
): Long {
    val zoned = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
    return when (bucket) {
        ChartBucket.RAW -> epochMillis
        ChartBucket.HOURLY -> zoned.truncatedTo(ChronoUnit.HOURS).toInstant().toEpochMilli()
        ChartBucket.DAILY -> zoned.toLocalDate().atStartOfDay(zoneId).toInstant().toEpochMilli()
        ChartBucket.WEEKLY -> zoned.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(weekStart))
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
        ChartBucket.MONTHLY -> zoned.toLocalDate()
            .withDayOfMonth(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
