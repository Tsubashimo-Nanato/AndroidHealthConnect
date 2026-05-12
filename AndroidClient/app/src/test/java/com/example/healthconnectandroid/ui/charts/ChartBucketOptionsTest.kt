package com.example.healthconnectandroid.ui.charts

import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChartBucketOptionsTest {
    @Test
    fun stepsOptionsExcludeWeekly() {
        val descriptor = HealthDataTypeRegistry.require(HealthDataTypeKeys.STEPS)

        val options = ChartBucketOptions.availableFor(descriptor)

        assertEquals(listOf(ChartBucket.DAILY, ChartBucket.MONTHLY), options)
        assertFalse(options.contains(ChartBucket.WEEKLY))
    }
}
