package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.InspectorTimeRange
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DetailRangeOptionsTest {
    @Test
    fun heartRateUsesDateMatrixInsteadOfPresetRanges() {
        val descriptor = HealthDataTypeRegistry.require(HealthDataTypeKeys.HEART_RATE)

        val options = DetailRangeOptions.optionsFor(descriptor)

        assertEquals(emptyList<InspectorTimeRange>(), options)
        assertFalse(options.contains(InspectorTimeRange.TODAY))
        assertEquals(InspectorTimeRange.LAST_90_DAYS, DetailRangeOptions.defaultRangeFor(descriptor))
    }

    @Test
    fun heartRateDefaultDetailSyncWindowIsNinetyDays() {
        val descriptor = HealthDataTypeRegistry.require(HealthDataTypeKeys.HEART_RATE)
        val end = Instant.parse("2026-05-11T00:00:00Z")
        val range = DetailRangeOptions.defaultRangeFor(descriptor)

        assertEquals(end.minus(Duration.ofDays(90)), range.startBefore(end, ZoneOffset.UTC))
    }

    @Test
    fun sleepOptionsAreWeekAndMonthOnly() {
        val descriptor = HealthDataTypeRegistry.require(HealthDataTypeKeys.SLEEP_SESSION)

        assertEquals(
            listOf(InspectorTimeRange.WEEKLY, InspectorTimeRange.MONTHLY),
            DetailRangeOptions.optionsFor(descriptor)
        )
    }
}
