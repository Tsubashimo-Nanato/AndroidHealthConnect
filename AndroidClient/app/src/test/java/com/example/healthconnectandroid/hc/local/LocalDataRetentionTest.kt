package com.example.healthconnectandroid.hc.local

import com.example.healthconnectandroid.data.HealthRecordRetentionRow
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataRetentionTest {
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val now = Instant.parse("2026-07-14T12:30:00Z")

    @Test
    fun keepNothingUsesTheFullRemovalPath() {
        assertTrue(LocalDataRetention.NONE.removesEverything)
    }

    @Test
    fun monthRangesUseCalendarBoundariesInTheUsersTimezone() {
        assertEquals(
            Instant.parse("2026-06-13T15:00:00Z").toEpochMilli(),
            LocalDataRetention.ONE_MONTH.cutoffEpochMillis(now, tokyo)
        )
        assertEquals(
            Instant.parse("2026-04-13T15:00:00Z").toEpochMilli(),
            LocalDataRetention.THREE_MONTHS.cutoffEpochMillis(now, tokyo)
        )
    }

    @Test
    fun oneWeekStartsAtTheLocalDayBoundary() {
        assertEquals(
            Instant.parse("2026-07-06T15:00:00Z").toEpochMilli(),
            LocalDataRetention.ONE_WEEK.cutoffEpochMillis(now, tokyo)
        )
    }

    @Test
    fun recordsEndingAtTheCutoffAreRetained() {
        val cutoff = 1_000L
        assertTrue(HealthRecordRetentionRow(1, 100, 999).endsBefore(cutoff))
        assertTrue(!HealthRecordRetentionRow(2, 100, cutoff).endsBefore(cutoff))
        assertTrue(!HealthRecordRetentionRow(3, cutoff, null).endsBefore(cutoff))
    }

    @Test
    fun removalProgressReportsBoundedFractions() {
        assertEquals(
            0.25f,
            LocalDataRemovalProgress(
                phase = LocalDataRemovalPhase.HEALTH_RECORDS,
                completedItems = 500,
                totalItems = 2_000
            ).fraction
        )
        assertEquals(
            1f,
            LocalDataRemovalProgress(
                phase = LocalDataRemovalPhase.HEALTH_RECORDS,
                completedItems = 2_500,
                totalItems = 2_000
            ).fraction
        )
    }

    @Test
    fun removalProgressStaysIndeterminateWithoutWorkSize() {
        assertNull(LocalDataRemovalProgress(LocalDataRemovalPhase.PREPARING).fraction)
        assertNull(
            LocalDataRemovalProgress(
                phase = LocalDataRemovalPhase.HEALTH_RECORDS,
                totalItems = 0
            ).fraction
        )
    }
}
