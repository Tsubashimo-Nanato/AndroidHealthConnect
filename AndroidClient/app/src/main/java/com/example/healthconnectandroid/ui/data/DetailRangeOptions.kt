package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.InspectorTimeRange

object DetailRangeOptions {
    fun defaultRangeFor(descriptor: HealthDataTypeDescriptor): InspectorTimeRange =
        when (descriptor.key) {
            HealthDataTypeKeys.HEART_RATE -> InspectorTimeRange.LAST_90_DAYS
            HealthDataTypeKeys.STEPS,
            HealthDataTypeKeys.ACTIVE_CALORIES,
            HealthDataTypeKeys.TOTAL_CALORIES,
            HealthDataTypeKeys.DISTANCE -> InspectorTimeRange.LAST_7_DAYS
            HealthDataTypeKeys.SLEEP_SESSION -> InspectorTimeRange.WEEKLY
            HealthDataTypeKeys.OXYGEN_SATURATION,
            HealthDataTypeKeys.BLOOD_PRESSURE,
            HealthDataTypeKeys.BODY_TEMPERATURE,
            HealthDataTypeKeys.RESPIRATORY_RATE,
            HealthDataTypeKeys.RESTING_HEART_RATE -> InspectorTimeRange.LAST_30_DAYS
            HealthDataTypeKeys.WEIGHT,
            HealthDataTypeKeys.BODY_FAT -> InspectorTimeRange.LAST_90_DAYS
            else -> {
                val days = descriptor.defaultTimeRange.duration.toDays()
                when {
                    descriptor.defaultTimeRange.duration.toHours() <= 24 -> InspectorTimeRange.LAST_24_HOURS
                    days <= 7 -> InspectorTimeRange.LAST_7_DAYS
                    days <= 30 -> InspectorTimeRange.LAST_30_DAYS
                    else -> InspectorTimeRange.LAST_90_DAYS
                }
            }
        }

    fun optionsFor(descriptor: HealthDataTypeDescriptor): List<InspectorTimeRange> =
        when (descriptor.key) {
            HealthDataTypeKeys.HEART_RATE -> emptyList()
            HealthDataTypeKeys.SLEEP_SESSION -> listOf(
                InspectorTimeRange.WEEKLY,
                InspectorTimeRange.MONTHLY
            )
            else -> listOf(
                InspectorTimeRange.TODAY,
                InspectorTimeRange.LAST_24_HOURS,
                InspectorTimeRange.LAST_7_DAYS,
                InspectorTimeRange.LAST_30_DAYS,
                InspectorTimeRange.LAST_90_DAYS
            )
        }
}
