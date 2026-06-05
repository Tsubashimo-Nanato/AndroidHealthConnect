package com.example.healthconnectandroid.hc

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.healthconnectandroid.data.HeartRateEntity
import java.time.Instant

private const val DEFAULT_PAGE_SIZE = 500

internal suspend inline fun <reified T : Record> HealthConnectClient.readPagedRecords(
    start: Instant,
    end: Instant,
    pageSize: Int = DEFAULT_PAGE_SIZE
): List<T> {
    var token: String? = null
    val rows = mutableListOf<T>()

    do {
        val response = readRecords(
            ReadRecordsRequest(
                recordType = T::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = true,
                pageSize = pageSize,
                pageToken = token
            )
        )
        rows += response.records
        token = response.pageToken
    } while (token != null)

    return rows
}

internal suspend inline fun <reified T : Record> HealthConnectClient.readRecordPages(
    start: Instant,
    end: Instant,
    pageSize: Int = DEFAULT_PAGE_SIZE,
    crossinline onPage: suspend (List<T>) -> Unit
) {
    var token: String? = null
    do {
        val response = readRecords(
            ReadRecordsRequest(
                recordType = T::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
                ascendingOrder = true,
                pageSize = pageSize,
                pageToken = token
            )
        )
        if (response.records.isNotEmpty()) {
            onPage(response.records)
        }
        token = response.pageToken
    } while (token != null)
}

private suspend inline fun <reified T : Record> HealthConnectClient.readNormalizedPages(
    start: Instant,
    end: Instant,
    crossinline mapper: (T) -> NormalizedHealthRecord,
    crossinline onPage: suspend (List<NormalizedHealthRecord>) -> Unit
) {
    readRecordPages<T>(start, end) { page ->
        val normalized = page.map(mapper)
        if (normalized.isNotEmpty()) onPage(normalized)
    }
}

object WeightRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.WEIGHT

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<WeightRecord>(start, end).map { it.toNormalizedWeight() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<WeightRecord>(start, end, WeightRecord::toNormalizedWeight, onPage)
}

object BodyFatRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.BODY_FAT

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<BodyFatRecord>(start, end).map { it.toNormalizedBodyFat() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<BodyFatRecord>(start, end, BodyFatRecord::toNormalizedBodyFat, onPage)
}

object OxygenSaturationRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.OXYGEN_SATURATION

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<OxygenSaturationRecord>(start, end)
            .map { it.toNormalizedOxygenSaturation() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<OxygenSaturationRecord>(
        start,
        end,
        OxygenSaturationRecord::toNormalizedOxygenSaturation,
        onPage
    )
}

object SleepSessionRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.SLEEP_SESSION

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<SleepSessionRecord>(start, end).map { it.toNormalizedSleepSession() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<SleepSessionRecord>(start, end, SleepSessionRecord::toNormalizedSleepSession, onPage)
}

object StepsRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.STEPS

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<StepsRecord>(start, end).map { it.toNormalizedSteps() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<StepsRecord>(start, end, StepsRecord::toNormalizedSteps, onPage)
}

object ActiveCaloriesRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.ACTIVE_CALORIES

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<ActiveCaloriesBurnedRecord>(start, end)
            .map { it.toNormalizedActiveCalories() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<ActiveCaloriesBurnedRecord>(
        start,
        end,
        ActiveCaloriesBurnedRecord::toNormalizedActiveCalories,
        onPage
    )
}

object TotalCaloriesRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.TOTAL_CALORIES

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<TotalCaloriesBurnedRecord>(start, end)
            .map { it.toNormalizedTotalCalories() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<TotalCaloriesBurnedRecord>(
        start,
        end,
        TotalCaloriesBurnedRecord::toNormalizedTotalCalories,
        onPage
    )
}

object DistanceRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.DISTANCE

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<DistanceRecord>(start, end).map { it.toNormalizedDistance() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<DistanceRecord>(start, end, DistanceRecord::toNormalizedDistance, onPage)
}

object BloodPressureRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.BLOOD_PRESSURE

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<BloodPressureRecord>(start, end).map { it.toNormalizedBloodPressure() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<BloodPressureRecord>(start, end, BloodPressureRecord::toNormalizedBloodPressure, onPage)
}

object BodyTemperatureRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.BODY_TEMPERATURE

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<BodyTemperatureRecord>(start, end)
            .map { it.toNormalizedBodyTemperature() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<BodyTemperatureRecord>(
        start,
        end,
        BodyTemperatureRecord::toNormalizedBodyTemperature,
        onPage
    )
}

object RespiratoryRateRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.RESPIRATORY_RATE

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<RespiratoryRateRecord>(start, end)
            .map { it.toNormalizedRespiratoryRate() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<RespiratoryRateRecord>(
        start,
        end,
        RespiratoryRateRecord::toNormalizedRespiratoryRate,
        onPage
    )
}

object RestingHeartRateRecordReader : HealthConnectRecordReader {
    override val dataTypeKey: String = HealthDataTypeKeys.RESTING_HEART_RATE

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> =
        client.readPagedRecords<RestingHeartRateRecord>(start, end)
            .map { it.toNormalizedRestingHeartRate() }

    override suspend fun readPages(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        onPage: suspend (List<NormalizedHealthRecord>) -> Unit
    ) = client.readNormalizedPages<RestingHeartRateRecord>(
        start,
        end,
        RestingHeartRateRecord::toNormalizedRestingHeartRate,
        onPage
    )
}

internal fun HeartRateRecord.toNormalizedHeartRate(): NormalizedHealthRecord {
    val values = samples.mapIndexed { index, sample ->
        NormalizedHealthValue.integer(
            metric = "heart_rate",
            value = sample.beatsPerMinute,
            unit = "bpm",
            category = "sample",
            sampleTime = sample.time,
            sequence = index
        )
    }
    return normalizedIntervalRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.HEART_RATE,
        kind = NormalizedRecordKind.SAMPLED_SERIES,
        startTime = startTime,
        endTime = endTime,
        startZoneOffsetSeconds = startZoneOffset.totalSecondsOrNull(),
        endZoneOffsetSeconds = endZoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = values
    )
}

internal fun List<HeartRateRecord>.toHeartRateEntities(): List<HeartRateEntity> =
    flatMap { record ->
        record.samples.map { sample ->
            HeartRateEntity(
                epochSecond = sample.time.epochSecond,
                bpm = sample.beatsPerMinute.toFloat()
            )
        }
    }

private fun WeightRecord.toNormalizedWeight(): NormalizedHealthRecord =
    normalizedInstantRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.WEIGHT,
        kind = NormalizedRecordKind.SCALAR_MEASUREMENT,
        time = time,
        zoneOffsetSeconds = zoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.floating(
                metric = "weight",
                value = weight.inKilograms,
                unit = "kg",
                category = "body",
                sampleTime = time
            )
        )
    )

private fun BodyFatRecord.toNormalizedBodyFat(): NormalizedHealthRecord =
    normalizedInstantRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.BODY_FAT,
        kind = NormalizedRecordKind.SCALAR_MEASUREMENT,
        time = time,
        zoneOffsetSeconds = zoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.floating(
                metric = "body_fat",
                value = percentage.value,
                unit = "percent",
                category = "body",
                sampleTime = time
            )
        )
    )

private fun OxygenSaturationRecord.toNormalizedOxygenSaturation(): NormalizedHealthRecord =
    normalizedInstantRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.OXYGEN_SATURATION,
        kind = NormalizedRecordKind.SCALAR_MEASUREMENT,
        time = time,
        zoneOffsetSeconds = zoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.floating(
                metric = "oxygen_saturation",
                value = percentage.value,
                unit = "percent",
                category = "vitals",
                sampleTime = time
            )
        )
    )

private fun SleepSessionRecord.toNormalizedSleepSession(): NormalizedHealthRecord {
    val stageValues = stages.mapIndexed { index, stage ->
        NormalizedHealthValue.json(
            metric = "sleep_stage",
            value = jsonObject(
                "stage" to sleepStageName(stage.stage),
                "stageCode" to stage.stage,
                "startTime" to stage.startTime.toString(),
                "endTime" to stage.endTime.toString()
            ),
            label = sleepStageName(stage.stage),
            category = "sleep_stage",
            sampleTime = stage.startTime,
            endTime = stage.endTime,
            sequence = index
        )
    }
    val summaryValues = listOfNotNull(
        NormalizedHealthValue.floating(
            metric = "duration",
            value = (endTime.toEpochMilli() - startTime.toEpochMilli()) / 60000.0,
            unit = "minutes",
            category = "session"
        ),
        title?.let { NormalizedHealthValue.text("title", it, category = "session") },
        notes?.let { NormalizedHealthValue.text("notes", it, category = "session") }
    )

    return normalizedIntervalRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.SLEEP_SESSION,
        kind = NormalizedRecordKind.SESSION,
        startTime = startTime,
        endTime = endTime,
        startZoneOffsetSeconds = startZoneOffset.totalSecondsOrNull(),
        endZoneOffsetSeconds = endZoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = summaryValues + stageValues
    )
}

private fun StepsRecord.toNormalizedSteps(): NormalizedHealthRecord =
    normalizedIntervalRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.STEPS,
        kind = NormalizedRecordKind.AGGREGATE,
        startTime = startTime,
        endTime = endTime,
        startZoneOffsetSeconds = startZoneOffset.totalSecondsOrNull(),
        endZoneOffsetSeconds = endZoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.integer(
                metric = "steps",
                value = count,
                unit = "count",
                category = "activity"
            )
        )
    )

private fun ActiveCaloriesBurnedRecord.toNormalizedActiveCalories(): NormalizedHealthRecord =
    normalizedIntervalRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.ACTIVE_CALORIES,
        kind = NormalizedRecordKind.AGGREGATE,
        startTime = startTime,
        endTime = endTime,
        startZoneOffsetSeconds = startZoneOffset.totalSecondsOrNull(),
        endZoneOffsetSeconds = endZoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.floating(
                metric = "active_calories",
                value = energy.inKilocalories,
                unit = "kcal",
                category = "activity"
            )
        )
    )

private fun TotalCaloriesBurnedRecord.toNormalizedTotalCalories(): NormalizedHealthRecord =
    normalizedIntervalRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.TOTAL_CALORIES,
        kind = NormalizedRecordKind.AGGREGATE,
        startTime = startTime,
        endTime = endTime,
        startZoneOffsetSeconds = startZoneOffset.totalSecondsOrNull(),
        endZoneOffsetSeconds = endZoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.floating(
                metric = "total_calories",
                value = energy.inKilocalories,
                unit = "kcal",
                category = "activity"
            )
        )
    )

private fun DistanceRecord.toNormalizedDistance(): NormalizedHealthRecord =
    normalizedIntervalRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.DISTANCE,
        kind = NormalizedRecordKind.AGGREGATE,
        startTime = startTime,
        endTime = endTime,
        startZoneOffsetSeconds = startZoneOffset.totalSecondsOrNull(),
        endZoneOffsetSeconds = endZoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.floating(
                metric = "distance",
                value = distance.inMeters,
                unit = "m",
                category = "activity"
            )
        )
    )

private fun BloodPressureRecord.toNormalizedBloodPressure(): NormalizedHealthRecord =
    normalizedInstantRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.BLOOD_PRESSURE,
        kind = NormalizedRecordKind.MULTI_VALUE,
        time = time,
        zoneOffsetSeconds = zoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.paired(
                metric = "blood_pressure",
                value = systolic.inMillimetersOfMercury,
                secondaryValue = diastolic.inMillimetersOfMercury,
                unit = "mmHg",
                label = "systolic_diastolic",
                category = "vitals",
                sampleTime = time,
                sequence = 0
            ),
            NormalizedHealthValue.floating(
                metric = "systolic",
                value = systolic.inMillimetersOfMercury,
                unit = "mmHg",
                category = "vitals",
                sampleTime = time,
                sequence = 1
            ),
            NormalizedHealthValue.floating(
                metric = "diastolic",
                value = diastolic.inMillimetersOfMercury,
                unit = "mmHg",
                category = "vitals",
                sampleTime = time,
                sequence = 2
            ),
            NormalizedHealthValue.integer(
                metric = "body_position",
                value = bodyPosition.toLong(),
                unit = "code",
                category = "vitals",
                sampleTime = time,
                sequence = 3
            ),
            NormalizedHealthValue.integer(
                metric = "measurement_location",
                value = measurementLocation.toLong(),
                unit = "code",
                category = "vitals",
                sampleTime = time,
                sequence = 4
            )
        )
    )

private fun BodyTemperatureRecord.toNormalizedBodyTemperature(): NormalizedHealthRecord =
    normalizedInstantRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.BODY_TEMPERATURE,
        kind = NormalizedRecordKind.SCALAR_MEASUREMENT,
        time = time,
        zoneOffsetSeconds = zoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.floating(
                metric = "body_temperature",
                value = temperature.inCelsius,
                unit = "C",
                category = "vitals",
                sampleTime = time
            ),
            NormalizedHealthValue.integer(
                metric = "measurement_location",
                value = measurementLocation.toLong(),
                unit = "code",
                category = "vitals",
                sampleTime = time
            )
        )
    )

private fun RespiratoryRateRecord.toNormalizedRespiratoryRate(): NormalizedHealthRecord =
    normalizedInstantRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.RESPIRATORY_RATE,
        kind = NormalizedRecordKind.SCALAR_MEASUREMENT,
        time = time,
        zoneOffsetSeconds = zoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.floating(
                metric = "respiratory_rate",
                value = rate,
                unit = "breaths_per_minute",
                category = "vitals",
                sampleTime = time
            )
        )
    )

private fun RestingHeartRateRecord.toNormalizedRestingHeartRate(): NormalizedHealthRecord =
    normalizedInstantRecord(
        uid = metadata.healthRecordUid(),
        typeKey = HealthDataTypeKeys.RESTING_HEART_RATE,
        kind = NormalizedRecordKind.SCALAR_MEASUREMENT,
        time = time,
        zoneOffsetSeconds = zoneOffset.totalSecondsOrNull(),
        sourcePackage = metadata.sourcePackageName(),
        metadataJson = null,
        rawJson = null,
        values = listOf(
            NormalizedHealthValue.integer(
                metric = "resting_heart_rate",
                value = beatsPerMinute,
                unit = "bpm",
                category = "vitals",
                sampleTime = time
            )
        )
    )

private fun normalizedInstantRecord(
    uid: String?,
    typeKey: String,
    kind: NormalizedRecordKind,
    time: Instant,
    zoneOffsetSeconds: Int?,
    sourcePackage: String?,
    metadataJson: String?,
    rawJson: String?,
    values: List<NormalizedHealthValue>
): NormalizedHealthRecord = NormalizedHealthRecord(
    uid = uid,
    typeKey = typeKey,
    kind = kind,
    startTime = time,
    endTime = null,
    startZoneOffsetSeconds = zoneOffsetSeconds,
    endZoneOffsetSeconds = null,
    sourcePackage = sourcePackage,
    metadataJson = metadataJson,
    rawJson = rawJson,
    values = values
)

private fun normalizedIntervalRecord(
    uid: String?,
    typeKey: String,
    kind: NormalizedRecordKind,
    startTime: Instant,
    endTime: Instant?,
    startZoneOffsetSeconds: Int?,
    endZoneOffsetSeconds: Int?,
    sourcePackage: String?,
    metadataJson: String?,
    rawJson: String?,
    values: List<NormalizedHealthValue>
): NormalizedHealthRecord = NormalizedHealthRecord(
    uid = uid,
    typeKey = typeKey,
    kind = kind,
    startTime = startTime,
    endTime = endTime,
    startZoneOffsetSeconds = startZoneOffsetSeconds,
    endZoneOffsetSeconds = endZoneOffsetSeconds,
    sourcePackage = sourcePackage,
    metadataJson = metadataJson,
    rawJson = rawJson,
    values = values
)

private fun sleepStageName(stage: Int): String = when (stage) {
    SleepSessionRecord.STAGE_TYPE_AWAKE -> "awake"
    SleepSessionRecord.STAGE_TYPE_SLEEPING -> "sleeping"
    SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> "out_of_bed"
    SleepSessionRecord.STAGE_TYPE_LIGHT -> "light"
    SleepSessionRecord.STAGE_TYPE_DEEP -> "deep"
    SleepSessionRecord.STAGE_TYPE_REM -> "rem"
    SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED -> "awake_in_bed"
    else -> "unknown"
}
