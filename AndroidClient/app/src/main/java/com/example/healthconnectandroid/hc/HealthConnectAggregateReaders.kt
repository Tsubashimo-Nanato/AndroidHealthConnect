package com.example.healthconnectandroid.hc

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId

data class HealthAggregateSummary(
    val recordType: String,
    val metric: String,
    val bucketPeriod: String,
    val bucketStart: Instant,
    val bucketEnd: Instant,
    val localDate: LocalDate,
    val timezoneId: String,
    val value: Double,
    val unit: String,
    val source: String,
    val rawJson: String?
)

object StepsDailyAggregateReader : HealthConnectAggregateReader {
    override val dataTypeKey: String = HealthDataTypeKeys.STEPS

    override suspend fun readDaily(
        client: HealthConnectClient,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId
    ): List<HealthAggregateSummary> = readDailyAggregate(
        client = client,
        recordType = dataTypeKey,
        metricName = "steps",
        metric = StepsRecord.COUNT_TOTAL,
        startDate = startDate,
        endDate = endDate,
        zoneId = zoneId,
        unit = "count",
        valueOf = { it.toDouble() }
    )
}

object DistanceDailyAggregateReader : HealthConnectAggregateReader {
    override val dataTypeKey: String = HealthDataTypeKeys.DISTANCE

    override suspend fun readDaily(
        client: HealthConnectClient,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId
    ): List<HealthAggregateSummary> = readDailyAggregate(
        client = client,
        recordType = dataTypeKey,
        metricName = "distance",
        metric = DistanceRecord.DISTANCE_TOTAL,
        startDate = startDate,
        endDate = endDate,
        zoneId = zoneId,
        unit = "m",
        valueOf = { it.inMeters }
    )
}

object ActiveCaloriesDailyAggregateReader : HealthConnectAggregateReader {
    override val dataTypeKey: String = HealthDataTypeKeys.ACTIVE_CALORIES

    override suspend fun readDaily(
        client: HealthConnectClient,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId
    ): List<HealthAggregateSummary> = readDailyAggregate(
        client = client,
        recordType = dataTypeKey,
        metricName = "active_calories",
        metric = ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
        startDate = startDate,
        endDate = endDate,
        zoneId = zoneId,
        unit = "kcal",
        valueOf = { it.inKilocalories }
    )
}

object TotalCaloriesDailyAggregateReader : HealthConnectAggregateReader {
    override val dataTypeKey: String = HealthDataTypeKeys.TOTAL_CALORIES

    override suspend fun readDaily(
        client: HealthConnectClient,
        startDate: LocalDate,
        endDate: LocalDate,
        zoneId: ZoneId
    ): List<HealthAggregateSummary> = readDailyAggregate(
        client = client,
        recordType = dataTypeKey,
        metricName = "total_calories",
        metric = TotalCaloriesBurnedRecord.ENERGY_TOTAL,
        startDate = startDate,
        endDate = endDate,
        zoneId = zoneId,
        unit = "kcal",
        valueOf = { it.inKilocalories }
    )
}

private suspend fun <T : Any> readDailyAggregate(
    client: HealthConnectClient,
    recordType: String,
    metricName: String,
    metric: AggregateMetric<T>,
    startDate: LocalDate,
    endDate: LocalDate,
    zoneId: ZoneId,
    unit: String,
    valueOf: (T) -> Double
): List<HealthAggregateSummary> {
    if (endDate.isBefore(startDate)) return emptyList()

    // Period aggregation requires local-time filters so buckets follow calendar days.
    val rows = client.aggregateGroupByPeriod(
        AggregateGroupByPeriodRequest(
            metrics = setOf(metric),
            timeRangeFilter = TimeRangeFilter.between(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay()
            ),
            timeRangeSlicer = Period.ofDays(1)
        )
    )

    return rows.mapNotNull { row ->
        val value = row.result[metric] ?: return@mapNotNull null
        val bucketDate = row.startTime.toLocalDate()
        HealthAggregateSummary(
            recordType = recordType,
            metric = metricName,
            bucketPeriod = "day",
            bucketStart = row.startTime.atZone(zoneId).toInstant(),
            bucketEnd = row.endTime.atZone(zoneId).toInstant(),
            localDate = bucketDate,
            timezoneId = zoneId.id,
            value = valueOf(value),
            unit = unit,
            source = "health_connect_aggregate",
            rawJson = jsonObject(
                "metric" to metricName,
                "bucketStartLocal" to row.startTime.toString(),
                "bucketEndLocal" to row.endTime.toString(),
                "dataOrigins" to row.result.dataOrigins
                    .map { it.packageName }
                    .sorted()
                    .joinToString(",")
            )
        )
    }
}
