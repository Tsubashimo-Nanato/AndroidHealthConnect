package com.example.healthconnectandroid.hc.local

import java.time.Instant
import java.time.Period
import java.time.ZoneId

enum class LocalDataRetention(
    val label: String,
    private val period: Period?
) {
    NONE("Keep nothing", null),
    ONE_WEEK("Keep one week", Period.ofWeeks(1)),
    ONE_MONTH("Keep one month", Period.ofMonths(1)),
    THREE_MONTHS("Keep three months", Period.ofMonths(3)),
    SIX_MONTHS("Keep six months", Period.ofMonths(6)),
    ONE_YEAR("Keep one year", Period.ofYears(1));

    val removesEverything: Boolean
        get() = period == null

    fun cutoffEpochMillis(now: Instant, zoneId: ZoneId): Long {
        val keepPeriod = requireNotNull(period) { "Keep-nothing retention has no date cutoff" }
        return now.atZone(zoneId)
            .toLocalDate()
            .minus(keepPeriod)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}

data class LocalDataRemovalResult(
    val recordsRemoved: Int,
    val legacyHeartRateRowsRemoved: Int
)

enum class LocalDataRemovalPhase {
    PREPARING,
    HEALTH_RECORDS,
    LEGACY_HEART_RATE,
    RELATED_DATA,
    RECLAIMING_SPACE,
    COMPLETE
}

data class LocalDataRemovalProgress(
    val phase: LocalDataRemovalPhase,
    val completedItems: Int = 0,
    val totalItems: Int? = null
) {
    val fraction: Float?
        get() = totalItems
            ?.takeIf { it > 0 }
            ?.let { completedItems.toFloat().div(it).coerceIn(0f, 1f) }
}
