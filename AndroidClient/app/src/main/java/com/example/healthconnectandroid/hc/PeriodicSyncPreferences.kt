package com.example.healthconnectandroid.hc

import android.content.Context
import java.time.Duration
import java.time.Instant

object PeriodicSyncPreferences {
    private const val PREFS_NAME = "periodic_health_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_FINISHED_EPOCH_MILLIS = "last_finished_epoch_millis"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_LAST_SUMMARY = "last_summary"
    private const val KEY_LAST_REQUESTED_EPOCH_MILLIS = "last_requested_epoch_millis"
    private const val KEY_SCHEDULED_INTERVAL_HOURS = "scheduled_interval_hours"
    private const val KEY_HISTORY_BACKFILL_START_INDEX = "history_backfill_start_index"

    fun isEnabled(context: Context): Boolean {
        val prefs = prefs(context)
        return resolveEnabled(
            hasStoredValue = prefs.contains(KEY_ENABLED),
            storedValue = prefs.getBoolean(KEY_ENABLED, true)
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun scheduledIntervalHours(context: Context): Long =
        prefs(context).getLong(KEY_SCHEDULED_INTERVAL_HOURS, 0L)

    fun setScheduledIntervalHours(context: Context, hours: Long) {
        prefs(context).edit().putLong(KEY_SCHEDULED_INTERVAL_HOURS, hours).apply()
    }

    fun historyBackfillStartIndex(context: Context): Int =
        prefs(context).getInt(KEY_HISTORY_BACKFILL_START_INDEX, 0).coerceAtLeast(0)

    fun setHistoryBackfillStartIndex(context: Context, index: Int) {
        prefs(context).edit()
            .putInt(KEY_HISTORY_BACKFILL_START_INDEX, index.coerceAtLeast(0))
            .apply()
    }

    fun lastFinishedAt(context: Context): Instant? {
        val value = prefs(context).getLong(KEY_LAST_FINISHED_EPOCH_MILLIS, 0L)
        return if (value > 0L) Instant.ofEpochMilli(value) else null
    }

    fun lastStatus(context: Context): String? =
        prefs(context).getString(KEY_LAST_STATUS, null)

    fun lastSummary(context: Context): String? =
        prefs(context).getString(KEY_LAST_SUMMARY, null)

    fun markFinished(
        context: Context,
        finishedAt: Instant,
        status: String,
        summary: String
    ) {
        prefs(context).edit()
            .putLong(KEY_LAST_FINISHED_EPOCH_MILLIS, finishedAt.toEpochMilli())
            .putString(KEY_LAST_STATUS, status)
            .putString(KEY_LAST_SUMMARY, summary)
            .apply()
    }

    fun shouldRequestImmediate(
        context: Context,
        now: Instant = Instant.now(),
        staleAfter: Duration = Duration.ofMinutes(15)
    ): Boolean {
        val prefs = prefs(context)
        return isImmediateSyncDue(
            enabled = isEnabled(context),
            lastFinishedEpochMillis = prefs.getLong(KEY_LAST_FINISHED_EPOCH_MILLIS, 0L),
            lastRequestedEpochMillis = prefs.getLong(KEY_LAST_REQUESTED_EPOCH_MILLIS, 0L),
            now = now,
            staleAfter = staleAfter
        )
    }

    fun markRequested(context: Context, requestedAt: Instant = Instant.now()) {
        prefs(context).edit()
            .putLong(KEY_LAST_REQUESTED_EPOCH_MILLIS, requestedAt.toEpochMilli())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    internal fun isImmediateSyncDue(
        enabled: Boolean,
        lastFinishedEpochMillis: Long,
        lastRequestedEpochMillis: Long,
        now: Instant,
        staleAfter: Duration
    ): Boolean {
        if (!enabled) return false
        val latestActivity = maxOf(lastFinishedEpochMillis, lastRequestedEpochMillis)
        return latestActivity <= 0L || latestActivity <= now.minus(staleAfter).toEpochMilli()
    }

    internal fun resolveEnabled(hasStoredValue: Boolean, storedValue: Boolean): Boolean =
        if (hasStoredValue) storedValue else true
}
