package com.example.healthconnectandroid.hc

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeriodicSyncPreferencesTest {
    private val now = Instant.parse("2026-07-15T00:00:00Z")
    private val staleAfter = Duration.ofMinutes(15)

    @Test
    fun newInstallEnablesPeriodicSyncByDefault() {
        assertTrue(
            PeriodicSyncPreferences.resolveEnabled(
                hasStoredValue = false,
                storedValue = false
            )
        )
    }

    @Test
    fun newInstallRequestsImmediateSync() {
        assertTrue(
            PeriodicSyncPreferences.isImmediateSyncDue(
                enabled = true,
                lastFinishedEpochMillis = 0,
                lastRequestedEpochMillis = 0,
                now = now,
                staleAfter = staleAfter
            )
        )
    }

    @Test
    fun explicitDisabledSettingIsPreserved() {
        assertFalse(
            PeriodicSyncPreferences.resolveEnabled(
                hasStoredValue = true,
                storedValue = false
            )
        )
    }

    @Test
    fun disabledPeriodicSyncNeverRequestsForegroundWork() {
        assertFalse(
            PeriodicSyncPreferences.isImmediateSyncDue(
                enabled = false,
                lastFinishedEpochMillis = 0,
                lastRequestedEpochMillis = 0,
                now = now,
                staleAfter = staleAfter
            )
        )
    }

    @Test
    fun recentRequestSuppressesDuplicateForegroundWork() {
        assertFalse(
            PeriodicSyncPreferences.isImmediateSyncDue(
                enabled = true,
                lastFinishedEpochMillis = now.minus(Duration.ofHours(1)).toEpochMilli(),
                lastRequestedEpochMillis = now.minus(Duration.ofMinutes(5)).toEpochMilli(),
                now = now,
                staleAfter = staleAfter
            )
        )
    }

    @Test
    fun staleFinishedWorkRequestsIncrementalSync() {
        assertTrue(
            PeriodicSyncPreferences.isImmediateSyncDue(
                enabled = true,
                lastFinishedEpochMillis = now.minus(staleAfter).toEpochMilli(),
                lastRequestedEpochMillis = 0,
                now = now,
                staleAfter = staleAfter
            )
        )
    }
}
