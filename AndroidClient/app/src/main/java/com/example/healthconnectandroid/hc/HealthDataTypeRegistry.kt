package com.example.healthconnectandroid.hc

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import com.example.healthconnectandroid.data.HeartRateEntity
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KClass

object HealthDataTypeKeys {
    const val HEART_RATE = "heart_rate"
    const val OXYGEN_SATURATION = "oxygen_saturation"
    const val WEIGHT = "weight"
    const val BODY_FAT = "body_fat"
    const val SLEEP_SESSION = "sleep_session"
    const val STEPS = "steps"
    const val DISTANCE = "distance"
    const val ACTIVE_CALORIES = "active_calories"
    const val TOTAL_CALORIES = "total_calories"
    const val EXERCISE_SESSION = "exercise_session"
    const val BLOOD_PRESSURE = "blood_pressure"
    const val BODY_TEMPERATURE = "body_temperature"
    const val RESPIRATORY_RATE = "respiratory_rate"
    const val RESTING_HEART_RATE = "resting_heart_rate"
    const val HEART_RATE_VARIABILITY = "heart_rate_variability"
    const val BASAL_METABOLIC_RATE = "basal_metabolic_rate"
    const val NUTRITION = "nutrition"
    const val HYDRATION = "hydration"
}

enum class HealthDataCategory {
    VITALS,
    BODY,
    ACTIVITY,
    SLEEP,
    EXERCISE,
    NUTRITION
}

enum class VisualizationType(val id: String) {
    TIME_SERIES("time_series"),
    TREND("trend"),
    DAILY_AGGREGATE("daily_aggregate"),
    SESSION_TIMELINE("session_timeline"),
    MEASUREMENT_LIST("measurement_list"),
    RAW_TABLE("raw_table")
}

enum class CsvExportHint(val id: String) {
    HEART_RATE_SERIES("heart_rate_series"),
    INSTANT_MEASUREMENTS("instant_measurements"),
    DAILY_AGGREGATES("daily_aggregates"),
    SESSION_RECORDS("session_records"),
    COMPOUND_MEASUREMENTS("compound_measurements"),
    RAW_RECORDS("raw_records")
}

enum class PreferredChartSource(val id: String) {
    RAW("raw"),
    LOCAL("local"),
    AGGREGATE("aggregate")
}

enum class HealthDataImplementationStatus {
    IMPLEMENTED,
    PLANNED
}

enum class HealthDataPermissionStatus(val label: String) {
    GRANTED("Ready"),
    MISSING("Missing permission"),
    UNSUPPORTED("Unsupported"),
    NOT_IMPLEMENTED("Not implemented")
}

data class DefaultTimeRange(
    val label: String,
    val duration: Duration
) {
    fun startBefore(end: Instant): Instant = end.minus(duration)

    companion object {
        val LAST_24_HOURS = DefaultTimeRange("Last 24 hours", Duration.ofHours(24))
        val LAST_14_DAYS = DefaultTimeRange("Last 14 days", Duration.ofDays(14))
        val LAST_30_DAYS = DefaultTimeRange("Last 30 days", Duration.ofDays(30))
        val LAST_90_DAYS = DefaultTimeRange("Last 90 days", Duration.ofDays(90))
        val LAST_180_DAYS = DefaultTimeRange("Last 180 days", Duration.ofDays(180))
    }
}

interface HealthConnectRecordReader {
    val dataTypeKey: String
    suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord>
}

interface HealthConnectAggregateReader {
    val dataTypeKey: String
    suspend fun readDaily(
        client: HealthConnectClient,
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate,
        zoneId: java.time.ZoneId
    ): List<HealthAggregateSummary>
}

data class HealthDataTypeDescriptor(
    val key: String,
    val displayName: String,
    val category: HealthDataCategory,
    val recordClass: KClass<out Record>,
    val defaultTimeRange: DefaultTimeRange,
    val visualizationType: VisualizationType,
    val csvExportHint: CsvExportHint,
    val aggregationPreferred: Boolean,
    val rawRecordReadingPreferred: Boolean,
    val implementationStatus: HealthDataImplementationStatus,
    val reader: HealthConnectRecordReader? = null,
    val aggregateReader: HealthConnectAggregateReader? = null,
    val preferredChartSource: PreferredChartSource = PreferredChartSource.RAW,
    val requiredReadPermission: String? = healthReadPermissionOrNull(recordClass)
) {
    val rawReaderAvailable: Boolean
        get() = reader != null

    val aggregateReaderAvailable: Boolean
        get() = aggregateReader != null

    fun permissionStatus(grantedPermissions: Set<String>): HealthDataPermissionStatus = when {
        implementationStatus != HealthDataImplementationStatus.IMPLEMENTED ->
            HealthDataPermissionStatus.NOT_IMPLEMENTED
        requiredReadPermission == null ->
            HealthDataPermissionStatus.UNSUPPORTED
        requiredReadPermission in grantedPermissions ->
            HealthDataPermissionStatus.GRANTED
        else ->
            HealthDataPermissionStatus.MISSING
    }
}

private fun healthReadPermissionOrNull(recordClass: KClass<out Record>): String? =
    runCatching { HealthPermission.getReadPermission(recordClass) }.getOrNull()

object HeartRateRecordReader : HealthConnectRecordReader {
    private const val DEFAULT_PAGE_SIZE = 500

    override val dataTypeKey: String = HealthDataTypeKeys.HEART_RATE

    override suspend fun read(
        client: HealthConnectClient,
        start: Instant,
        end: Instant
    ): List<NormalizedHealthRecord> = readRecords(client, start, end).map { it.toNormalizedHeartRate() }

    suspend fun readRecords(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<HeartRateRecord> = client.readPagedRecords<HeartRateRecord>(start, end, pageSize)

    suspend fun readEntities(
        client: HealthConnectClient,
        start: Instant,
        end: Instant,
        pageSize: Int = DEFAULT_PAGE_SIZE
    ): List<HeartRateEntity> = readRecords(client, start, end, pageSize).toHeartRateEntities()
}

object HealthDataTypeRegistry {
    val descriptors: List<HealthDataTypeDescriptor> = listOf(
        implemented(
            key = HealthDataTypeKeys.HEART_RATE,
            displayName = "Heart rate",
            category = HealthDataCategory.VITALS,
            recordClass = HeartRateRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_90_DAYS,
            visualizationType = VisualizationType.TIME_SERIES,
            csvExportHint = CsvExportHint.HEART_RATE_SERIES,
            aggregationPreferred = false,
            rawRecordReadingPreferred = false,
            reader = HeartRateRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.OXYGEN_SATURATION,
            displayName = "Oxygen saturation",
            category = HealthDataCategory.VITALS,
            recordClass = OxygenSaturationRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.MEASUREMENT_LIST,
            csvExportHint = CsvExportHint.INSTANT_MEASUREMENTS,
            aggregationPreferred = false,
            rawRecordReadingPreferred = false,
            reader = OxygenSaturationRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.WEIGHT,
            displayName = "Weight",
            category = HealthDataCategory.BODY,
            recordClass = WeightRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_90_DAYS,
            visualizationType = VisualizationType.TREND,
            csvExportHint = CsvExportHint.INSTANT_MEASUREMENTS,
            aggregationPreferred = false,
            rawRecordReadingPreferred = false,
            reader = WeightRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.BODY_FAT,
            displayName = "Body fat",
            category = HealthDataCategory.BODY,
            recordClass = BodyFatRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_90_DAYS,
            visualizationType = VisualizationType.TREND,
            csvExportHint = CsvExportHint.INSTANT_MEASUREMENTS,
            aggregationPreferred = false,
            rawRecordReadingPreferred = false,
            reader = BodyFatRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.SLEEP_SESSION,
            displayName = "Sleep session",
            category = HealthDataCategory.SLEEP,
            recordClass = SleepSessionRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_14_DAYS,
            visualizationType = VisualizationType.SESSION_TIMELINE,
            csvExportHint = CsvExportHint.SESSION_RECORDS,
            aggregationPreferred = false,
            rawRecordReadingPreferred = true,
            reader = SleepSessionRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.STEPS,
            displayName = "Steps",
            category = HealthDataCategory.ACTIVITY,
            recordClass = StepsRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.DAILY_AGGREGATE,
            csvExportHint = CsvExportHint.DAILY_AGGREGATES,
            aggregationPreferred = true,
            rawRecordReadingPreferred = false,
            aggregateReader = StepsDailyAggregateReader,
            preferredChartSource = PreferredChartSource.AGGREGATE,
            reader = StepsRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.DISTANCE,
            displayName = "Distance",
            category = HealthDataCategory.ACTIVITY,
            recordClass = DistanceRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.DAILY_AGGREGATE,
            csvExportHint = CsvExportHint.DAILY_AGGREGATES,
            aggregationPreferred = true,
            rawRecordReadingPreferred = false,
            aggregateReader = DistanceDailyAggregateReader,
            preferredChartSource = PreferredChartSource.AGGREGATE,
            reader = DistanceRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.ACTIVE_CALORIES,
            displayName = "Active calories",
            category = HealthDataCategory.ACTIVITY,
            recordClass = ActiveCaloriesBurnedRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.DAILY_AGGREGATE,
            csvExportHint = CsvExportHint.DAILY_AGGREGATES,
            aggregationPreferred = true,
            rawRecordReadingPreferred = false,
            aggregateReader = ActiveCaloriesDailyAggregateReader,
            preferredChartSource = PreferredChartSource.AGGREGATE,
            reader = ActiveCaloriesRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.TOTAL_CALORIES,
            displayName = "Total calories",
            category = HealthDataCategory.ACTIVITY,
            recordClass = TotalCaloriesBurnedRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.DAILY_AGGREGATE,
            csvExportHint = CsvExportHint.DAILY_AGGREGATES,
            aggregationPreferred = true,
            rawRecordReadingPreferred = false,
            aggregateReader = TotalCaloriesDailyAggregateReader,
            preferredChartSource = PreferredChartSource.AGGREGATE,
            reader = TotalCaloriesRecordReader
        ),
        planned(
            key = HealthDataTypeKeys.EXERCISE_SESSION,
            displayName = "Exercise session",
            category = HealthDataCategory.EXERCISE,
            recordClass = ExerciseSessionRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.SESSION_TIMELINE,
            csvExportHint = CsvExportHint.SESSION_RECORDS,
            rawRecordReadingPreferred = true
        ),
        implemented(
            key = HealthDataTypeKeys.BLOOD_PRESSURE,
            displayName = "Blood pressure",
            category = HealthDataCategory.VITALS,
            recordClass = BloodPressureRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.MEASUREMENT_LIST,
            csvExportHint = CsvExportHint.COMPOUND_MEASUREMENTS,
            aggregationPreferred = false,
            rawRecordReadingPreferred = false,
            reader = BloodPressureRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.BODY_TEMPERATURE,
            displayName = "Body temperature",
            category = HealthDataCategory.VITALS,
            recordClass = BodyTemperatureRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.MEASUREMENT_LIST,
            csvExportHint = CsvExportHint.INSTANT_MEASUREMENTS,
            aggregationPreferred = false,
            rawRecordReadingPreferred = false,
            reader = BodyTemperatureRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.RESPIRATORY_RATE,
            displayName = "Respiratory rate",
            category = HealthDataCategory.VITALS,
            recordClass = RespiratoryRateRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.TIME_SERIES,
            csvExportHint = CsvExportHint.INSTANT_MEASUREMENTS,
            aggregationPreferred = false,
            rawRecordReadingPreferred = false,
            reader = RespiratoryRateRecordReader
        ),
        implemented(
            key = HealthDataTypeKeys.RESTING_HEART_RATE,
            displayName = "Resting heart rate",
            category = HealthDataCategory.VITALS,
            recordClass = RestingHeartRateRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.TREND,
            csvExportHint = CsvExportHint.INSTANT_MEASUREMENTS,
            aggregationPreferred = false,
            rawRecordReadingPreferred = false,
            reader = RestingHeartRateRecordReader
        ),
        planned(
            key = HealthDataTypeKeys.HEART_RATE_VARIABILITY,
            displayName = "Heart rate variability",
            category = HealthDataCategory.VITALS,
            recordClass = HeartRateVariabilityRmssdRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.TREND,
            csvExportHint = CsvExportHint.INSTANT_MEASUREMENTS
        ),
        planned(
            key = HealthDataTypeKeys.BASAL_METABOLIC_RATE,
            displayName = "Basal metabolic rate",
            category = HealthDataCategory.BODY,
            recordClass = BasalMetabolicRateRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_90_DAYS,
            visualizationType = VisualizationType.TREND,
            csvExportHint = CsvExportHint.INSTANT_MEASUREMENTS
        ),
        planned(
            key = HealthDataTypeKeys.NUTRITION,
            displayName = "Nutrition",
            category = HealthDataCategory.NUTRITION,
            recordClass = NutritionRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.RAW_TABLE,
            csvExportHint = CsvExportHint.RAW_RECORDS,
            rawRecordReadingPreferred = true
        ),
        planned(
            key = HealthDataTypeKeys.HYDRATION,
            displayName = "Hydration",
            category = HealthDataCategory.NUTRITION,
            recordClass = HydrationRecord::class,
            defaultTimeRange = DefaultTimeRange.LAST_30_DAYS,
            visualizationType = VisualizationType.DAILY_AGGREGATE,
            csvExportHint = CsvExportHint.DAILY_AGGREGATES,
            aggregationPreferred = true
        )
    )

    private val byKey: Map<String, HealthDataTypeDescriptor> = descriptors.associateBy { it.key }

    val heartRate: HealthDataTypeDescriptor = require(HealthDataTypeKeys.HEART_RATE)

    val implementedDescriptors: List<HealthDataTypeDescriptor>
        get() = descriptors.filter {
            it.implementationStatus == HealthDataImplementationStatus.IMPLEMENTED
        }

    val implementedReadPermissions: Set<String>
        get() = implementedDescriptors.mapNotNullTo(mutableSetOf()) { it.requiredReadPermission }

    val backgroundReadPermission: String =
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND

    val periodicReadPermissions: Set<String>
        get() = implementedReadPermissions + backgroundReadPermission

    fun missingImplementedReadPermissions(grantedPermissions: Set<String>): Set<String> =
        implementedReadPermissions - grantedPermissions

    fun require(key: String): HealthDataTypeDescriptor =
        byKey[key] ?: error("Unknown Health Connect data type: $key")

    private fun implemented(
        key: String,
        displayName: String,
        category: HealthDataCategory,
        recordClass: KClass<out Record>,
        defaultTimeRange: DefaultTimeRange,
        visualizationType: VisualizationType,
        csvExportHint: CsvExportHint,
        aggregationPreferred: Boolean,
        rawRecordReadingPreferred: Boolean,
        reader: HealthConnectRecordReader,
        aggregateReader: HealthConnectAggregateReader? = null,
        preferredChartSource: PreferredChartSource = PreferredChartSource.RAW
    ): HealthDataTypeDescriptor = HealthDataTypeDescriptor(
        key = key,
        displayName = displayName,
        category = category,
        recordClass = recordClass,
        defaultTimeRange = defaultTimeRange,
        visualizationType = visualizationType,
        csvExportHint = csvExportHint,
        aggregationPreferred = aggregationPreferred,
        rawRecordReadingPreferred = rawRecordReadingPreferred,
        implementationStatus = HealthDataImplementationStatus.IMPLEMENTED,
        reader = reader,
        aggregateReader = aggregateReader,
        preferredChartSource = preferredChartSource
    )

    private fun planned(
        key: String,
        displayName: String,
        category: HealthDataCategory,
        recordClass: KClass<out Record>,
        defaultTimeRange: DefaultTimeRange,
        visualizationType: VisualizationType,
        csvExportHint: CsvExportHint,
        aggregationPreferred: Boolean = false,
        rawRecordReadingPreferred: Boolean = false
    ): HealthDataTypeDescriptor = HealthDataTypeDescriptor(
        key = key,
        displayName = displayName,
        category = category,
        recordClass = recordClass,
        defaultTimeRange = defaultTimeRange,
        visualizationType = visualizationType,
        csvExportHint = csvExportHint,
        aggregationPreferred = aggregationPreferred,
        rawRecordReadingPreferred = rawRecordReadingPreferred,
        implementationStatus = HealthDataImplementationStatus.PLANNED
    )
}
