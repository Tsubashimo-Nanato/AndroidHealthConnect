package com.example.healthconnectandroid.hc.upload

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.sync.SyncMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoUploadPolicyTest {
    private val validSettings = UploadSettings(
        apiKey = "test-key",
        deviceId = "test-device"
    )

    @Test
    fun disabledNeverQueues() {
        assertFalse(
            AutoUploadPolicy.shouldQueueAfterSync(
                enabled = false,
                mode = SyncMode.SMART,
                results = successResults(),
                settings = validSettings
            )
        )
    }

    @Test
    fun invalidEndpointNeverQueues() {
        assertFalse(
            AutoUploadPolicy.shouldQueueAfterSync(
                enabled = true,
                mode = SyncMode.SMART,
                results = successResults(),
                settings = validSettings.copy(apiKey = "")
            )
        )
    }

    @Test
    fun successSmartSyncQueues() {
        assertTrue(
            AutoUploadPolicy.shouldQueueAfterSync(
                enabled = true,
                mode = SyncMode.SMART,
                results = successResults(),
                settings = validSettings
            )
        )
    }

    @Test
    fun warningPeriodicSyncQueues() {
        assertTrue(
            AutoUploadPolicy.shouldQueueAfterSync(
                enabled = true,
                mode = SyncMode.PERIODIC,
                results = warningResults(),
                settings = validSettings
            )
        )
    }

    @Test
    fun errorSyncDoesNotQueue() {
        assertFalse(
            AutoUploadPolicy.shouldQueueAfterSync(
                enabled = true,
                mode = SyncMode.SMART,
                results = errorResults(),
                settings = validSettings
            )
        )
    }

    @Test
    fun fullResyncDoesNotQueue() {
        assertFalse(
            AutoUploadPolicy.shouldQueueAfterSync(
                enabled = true,
                mode = SyncMode.FULL_HISTORY,
                results = successResults(),
                settings = validSettings
            )
        )
    }

    @Test
    fun selectedTypeSyncDoesNotQueue() {
        assertFalse(
            AutoUploadPolicy.shouldQueueAfterSync(
                enabled = true,
                mode = SyncMode.SELECTED_TYPE,
                results = successResults(),
                settings = validSettings
            )
        )
    }

    private fun successResults(): List<HealthDataTypeSyncResult> =
        listOf(HealthDataTypeSyncResult(key = "heart_rate", recordsInserted = 1))

    private fun warningResults(): List<HealthDataTypeSyncResult> =
        listOf(
            HealthDataTypeSyncResult(key = "heart_rate", recordsInserted = 1),
            HealthDataTypeSyncResult(key = "sleep_session", errorMessage = "missing permission")
        )

    private fun errorResults(): List<HealthDataTypeSyncResult> =
        listOf(HealthDataTypeSyncResult(key = "sleep_session", errorMessage = "missing permission"))
}
