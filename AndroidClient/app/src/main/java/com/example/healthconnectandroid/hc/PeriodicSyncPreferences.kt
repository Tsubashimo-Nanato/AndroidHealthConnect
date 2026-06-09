package com.example.healthconnectandroid.hc

import android.content.Context
import java.time.Instant

object PeriodicSyncPreferences {
    private const val PREFS_NAME = "periodic_health_sync"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_FINISHED_EPOCH_MILLIS = "last_finished_epoch_millis"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_LAST_SUMMARY = "last_summary"
    private const val KEY_AUTO_UPLOAD_AFTER_SYNC = "auto_upload_after_sync"

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun autoUploadAfterSync(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPLOAD_AFTER_SYNC, false)

    fun setAutoUploadAfterSync(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPLOAD_AFTER_SYNC, enabled).apply()
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

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
