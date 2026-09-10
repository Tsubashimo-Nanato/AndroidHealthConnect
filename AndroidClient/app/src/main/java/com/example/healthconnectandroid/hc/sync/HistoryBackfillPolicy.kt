package com.example.healthconnectandroid.hc.sync

import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class HistoryBackfillRange(
    val start: Instant,
    val end: Instant
)

internal data class HistoryBackfillChunk(
    val windows: List<SyncDateWindow>
) {
    init {
        require(windows.isNotEmpty()) { "Backfill chunk must contain at least one day" }
    }

    val start: Instant = windows.first().start
    val end: Instant = windows.last().end
    val firstDate: LocalDate = windows.first().localDate
    val lastDate: LocalDate = windows.last().localDate
    val dayCount: Int = windows.size
}

internal object HistoryBackfillPolicy {
    private const val RETAINED_DAY_COUNT = 30L
    private const val MAX_DAYS_PER_BATCH = 7
    const val MAX_TYPES_PER_RUN = 4

    fun targetRange(now: Instant, zoneId: ZoneId): HistoryBackfillRange {
        val today = now.atZone(zoneId).toLocalDate()
        return HistoryBackfillRange(
            start = today.minusDays(RETAINED_DAY_COUNT - 1).atStartOfDay(zoneId).toInstant(),
            end = today.atStartOfDay(zoneId).toInstant()
        )
    }

    fun latestChunk(
        missingWindows: List<SyncDateWindow>,
        maxDays: Int = MAX_DAYS_PER_BATCH
    ): HistoryBackfillChunk? {
        if (missingWindows.isEmpty()) return null
        require(maxDays > 0) { "maxDays must be positive" }

        val newestFirst = missingWindows.sortedByDescending { it.localDate }
        val selected = mutableListOf<SyncDateWindow>()
        for (window in newestFirst) {
            val newerDate = selected.lastOrNull()?.localDate
            if (newerDate != null && window.localDate.plusDays(1) != newerDate) break
            selected += window
            if (selected.size == maxDays) break
        }
        return HistoryBackfillChunk(selected.asReversed())
    }

    fun maxDaysPerBatch(recordType: String): Int =
        if (recordType == HealthDataTypeKeys.HEART_RATE) 1 else MAX_DAYS_PER_BATCH

    fun hasTypeCapacity(processedTypeCount: Int): Boolean =
        processedTypeCount < MAX_TYPES_PER_RUN

    fun typeIndex(startIndex: Int, offset: Int, typeCount: Int): Int {
        if (typeCount <= 0) return 0
        return Math.floorMod(
            startIndex.toLong() + offset.toLong(),
            typeCount.toLong()
        ).toInt()
    }

    fun hasMore(
        missingWindows: List<SyncDateWindow>,
        selectedChunk: HistoryBackfillChunk?
    ): Boolean = missingWindows.size > (selectedChunk?.dayCount ?: 0)
}
