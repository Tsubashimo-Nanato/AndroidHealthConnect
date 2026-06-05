package com.example.healthconnectandroid.hc.query

import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthDetailQueryServicePolicyTest {
    @Test
    fun heartRateUsesBoundedChartWindowForResponsiveVisualization() {
        assertTrue(inspectorChartRowLimit(HealthDataTypeKeys.HEART_RATE) > inspectorChartRowLimit(HealthDataTypeKeys.WEIGHT))
        assertTrue(inspectorChartRowLimit(HealthDataTypeKeys.HEART_RATE) <= 12_000)
    }

    @Test
    fun nonHeartRateChartWindowStaysSmall() {
        assertEquals(1_200, inspectorChartRowLimit(HealthDataTypeKeys.WEIGHT))
    }
}
