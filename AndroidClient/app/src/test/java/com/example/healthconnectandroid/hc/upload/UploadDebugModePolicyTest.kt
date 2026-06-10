package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadDebugModePolicyTest {
    private val productionSettings = UploadSettings(
        serverMode = UploadServerMode.PRODUCTION,
        apiKey = "key",
        deviceId = "device"
    )
    private val productionStatus = UploadStatus(serverMode = UploadServerMode.PRODUCTION)

    @Test
    fun localPairingEnablesDebugMode() {
        val pairedSettings = productionSettings.copy(
            serverMode = UploadServerMode.LOCAL_DEBUG,
            localBaseUrl = "http://10.0.2.2:8000/health/api/v1/"
        )

        val result = UploadDebugModePolicy.applyPairingSuccess(
            currentStatus = productionStatus,
            debugEnabled = false,
            success = UploadPairingApplyResult.Success(
                settings = pairedSettings,
                message = "Paired Local debug upload endpoint"
            )
        )

        assertTrue(result.debugEnabled)
        assertEquals(UploadServerMode.LOCAL_DEBUG, result.settings.serverMode)
        assertEquals(UploadServerMode.LOCAL_DEBUG, result.status.serverMode)
        assertEquals("Paired Local debug upload endpoint. Debug mode enabled.", result.message)
    }

    @Test
    fun productionPairingDoesNotChangeDebugMode() {
        val result = UploadDebugModePolicy.applyPairingSuccess(
            currentStatus = productionStatus,
            debugEnabled = false,
            success = UploadPairingApplyResult.Success(
                settings = productionSettings,
                message = "Paired Production upload endpoint"
            )
        )

        assertFalse(result.debugEnabled)
        assertEquals(UploadServerMode.PRODUCTION, result.settings.serverMode)
        assertEquals("Paired Production upload endpoint", result.message)
    }

    @Test
    fun disablingDebugLeavesLocalDebugUploadMode() {
        val localSettings = productionSettings.copy(
            serverMode = UploadServerMode.LOCAL_DEBUG,
            localBaseUrl = "http://10.0.2.2:8000/health/api/v1/"
        )
        val localStatus = productionStatus.copy(serverMode = UploadServerMode.LOCAL_DEBUG)

        val result = UploadDebugModePolicy.setDebugMode(
            currentSettings = localSettings,
            currentStatus = localStatus,
            enabled = false
        )

        assertFalse(result.debugEnabled)
        assertEquals(UploadServerMode.PRODUCTION, result.settings.serverMode)
        assertEquals(UploadServerMode.PRODUCTION, result.status.serverMode)
        assertEquals("Debug mode disabled", result.message)
    }
}
