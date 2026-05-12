package com.example.healthconnectandroid.ui.format

import com.example.healthconnectandroid.UnitSystemPreference
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MetricDisplayFormatterTest {
    @Test
    fun formatsCountsWithSeparators() {
        assertEquals("13,746", MetricDisplayFormatter.formatCount(13746))
        assertEquals("6,206 records", MetricDisplayFormatter.formatRecordCount(6206))
    }

    @Test
    fun formatsHealthMetricValuesForCards() {
        assertEquals("7.85 km", MetricDisplayFormatter.formatDistance(7852.0000000003))
        assertEquals("2,518 kcal", MetricDisplayFormatter.formatEnergy(2518.2851490883018))
        assertEquals("59.9 kg", MetricDisplayFormatter.formatWeight(59.9))
        assertEquals("11.2%", MetricDisplayFormatter.formatPercent(11.234))
        assertEquals("79 bpm", MetricDisplayFormatter.formatBpm(79.4))
        assertEquals("36.6 C", MetricDisplayFormatter.formatMeasurement(36.58, "C"))
        assertEquals("118 mmHg", MetricDisplayFormatter.formatMeasurement(118.4, "mmHg"))
    }

    @Test
    fun formatsCompactDurations() {
        assertEquals("6h 45m", MetricDisplayFormatter.formatDurationCompact(Duration.ofMinutes(405)))
        assertEquals("45m", MetricDisplayFormatter.formatDurationCompact(Duration.ofMinutes(45)))
    }

    @Test
    fun formatsImperialDisplayValues() {
        assertEquals("4.88 mi", MetricDisplayFormatter.formatDistance(7852.0000000003, UnitSystemPreference.IMPERIAL))
        assertEquals("132.1 lb", MetricDisplayFormatter.formatWeight(59.9, UnitSystemPreference.IMPERIAL))
        assertEquals("97.9 F", MetricDisplayFormatter.formatMeasurement(36.6, "C", UnitSystemPreference.IMPERIAL))
    }

    @Test
    fun formatsShortInstantInSelectedTimezone() {
        val instant = Instant.parse("2026-05-10T00:00:00Z")

        assertEquals("5/10 00:00", MetricDisplayFormatter.formatShortInstant(instant, ZoneId.of("UTC")))
        assertEquals("5/10 09:00", MetricDisplayFormatter.formatShortInstant(instant, ZoneId.of("Asia/Tokyo")))
    }
}
