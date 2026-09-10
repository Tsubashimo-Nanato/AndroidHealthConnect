package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryBackfillPolicyTest {
    private val zoneId = ZoneId.of("Asia/Tokyo")

    @Test
    fun targetCoversCompletedDaysBeforeTodayWithinRetentionWindow() {
        val range = HistoryBackfillPolicy.targetRange(
            now = Instant.parse("2026-08-12T06:30:00Z"),
            zoneId = zoneId
        )

        assertEquals(
            LocalDate.parse("2026-07-14").atStartOfDay(zoneId).toInstant(),
            range.start
        )
        assertEquals(
            LocalDate.parse("2026-08-12").atStartOfDay(zoneId).toInstant(),
            range.end
        )
    }

    @Test
    fun latestChunkSelectsAtMostSevenContiguousMissingDays() {
        val windows = (1L..10L).map { day -> window(LocalDate.parse("2026-08-01").plusDays(day - 1)) }

        val chunk = HistoryBackfillPolicy.latestChunk(windows)

        assertEquals(LocalDate.parse("2026-08-04"), chunk?.firstDate)
        assertEquals(LocalDate.parse("2026-08-10"), chunk?.lastDate)
        assertEquals(7, chunk?.dayCount)
        assertTrue(HistoryBackfillPolicy.hasMore(windows, chunk))
    }

    @Test
    fun latestChunkStopsAtCoverageGap() {
        val windows = listOf(
            window(LocalDate.parse("2026-08-01")),
            window(LocalDate.parse("2026-08-02")),
            window(LocalDate.parse("2026-08-05")),
            window(LocalDate.parse("2026-08-06"))
        )

        val chunk = HistoryBackfillPolicy.latestChunk(windows)

        assertEquals(LocalDate.parse("2026-08-05"), chunk?.firstDate)
        assertEquals(LocalDate.parse("2026-08-06"), chunk?.lastDate)
        assertEquals(2, chunk?.dayCount)
        assertTrue(HistoryBackfillPolicy.hasMore(windows, chunk))
    }

    @Test
    fun heartRateBackfillUsesOneDayChunks() {
        val windows = (1L..3L).map { day -> window(LocalDate.parse("2026-08-01").plusDays(day - 1)) }

        val chunk = HistoryBackfillPolicy.latestChunk(
            missingWindows = windows,
            maxDays = HistoryBackfillPolicy.maxDaysPerBatch(HealthDataTypeKeys.HEART_RATE)
        )

        assertEquals(LocalDate.parse("2026-08-03"), chunk?.firstDate)
        assertEquals(1, chunk?.dayCount)
    }

    @Test
    fun emptyHistoryHasNoChunkOrRemainingWork() {
        val chunk = HistoryBackfillPolicy.latestChunk(emptyList())

        assertEquals(null, chunk)
        assertFalse(HistoryBackfillPolicy.hasMore(emptyList(), chunk))
    }

    @Test
    fun backgroundRunCapsHealthConnectReadsByType() {
        assertTrue(HistoryBackfillPolicy.hasTypeCapacity(0))
        assertTrue(
            HistoryBackfillPolicy.hasTypeCapacity(
                HistoryBackfillPolicy.MAX_TYPES_PER_RUN - 1
            )
        )
        assertFalse(HistoryBackfillPolicy.hasTypeCapacity(HistoryBackfillPolicy.MAX_TYPES_PER_RUN))
    }

    @Test
    fun typeCursorWrapsWithoutStarvingLaterTypes() {
        assertEquals(3, HistoryBackfillPolicy.typeIndex(startIndex = 3, offset = 0, typeCount = 5))
        assertEquals(4, HistoryBackfillPolicy.typeIndex(startIndex = 3, offset = 1, typeCount = 5))
        assertEquals(0, HistoryBackfillPolicy.typeIndex(startIndex = 3, offset = 2, typeCount = 5))
        assertEquals(4, HistoryBackfillPolicy.typeIndex(startIndex = -1, offset = 0, typeCount = 5))
    }

    @Test
    fun typeCursorHandlesLargeValuesWithoutOverflow() {
        assertEquals(
            2,
            HistoryBackfillPolicy.typeIndex(
                startIndex = Int.MAX_VALUE,
                offset = Int.MAX_VALUE,
                typeCount = 9
            )
        )
    }

    private fun window(date: LocalDate): SyncDateWindow = SyncDateWindow(
        localDate = date,
        start = date.atStartOfDay(zoneId).toInstant(),
        end = date.plusDays(1).atStartOfDay(zoneId).toInstant()
    )
}
