package com.example.healthconnectandroid.ui.charts

import com.example.healthconnectandroid.hc.HealthDataTypeDescriptor
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.VisualizationType

object ChartBucketOptions {
    fun availableFor(descriptor: HealthDataTypeDescriptor): List<ChartBucket> =
        when {
            descriptor.key == HealthDataTypeKeys.HEART_RATE -> listOf(ChartBucket.RAW)
            descriptor.key == HealthDataTypeKeys.STEPS -> listOf(ChartBucket.DAILY, ChartBucket.MONTHLY)
            descriptor.visualizationType == VisualizationType.DAILY_AGGREGATE ->
                listOf(ChartBucket.DAILY, ChartBucket.WEEKLY, ChartBucket.MONTHLY)
            descriptor.visualizationType in setOf(
                VisualizationType.TIME_SERIES,
                VisualizationType.TREND,
                VisualizationType.MEASUREMENT_LIST
            ) -> listOf(
                ChartBucket.RAW,
                ChartBucket.HOURLY,
                ChartBucket.DAILY,
                ChartBucket.WEEKLY,
                ChartBucket.MONTHLY
            )
            else -> listOf(ChartBucket.RAW)
        }
}
