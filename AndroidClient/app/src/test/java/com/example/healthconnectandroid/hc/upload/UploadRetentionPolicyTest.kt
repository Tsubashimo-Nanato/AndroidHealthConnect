package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadRetentionPolicyTest {
    @Test
    fun completedProductionUploadAllowsRetention() {
        val serverKey = UploadRetentionPolicy.productionServerKey(
            settings = settings(UploadServerMode.PRODUCTION),
            uploadSucceeded = true,
            profileOwnsHealthConnect = true
        )

        assertTrue(serverKey?.startsWith("PRODUCTION:") == true)
    }

    @Test
    fun localDebugUploadNeverAllowsRetention() {
        val serverKey = UploadRetentionPolicy.productionServerKey(
            settings = settings(UploadServerMode.LOCAL_DEBUG),
            uploadSucceeded = true,
            profileOwnsHealthConnect = true
        )

        assertNull(serverKey)
    }

    @Test
    fun failedUploadNeverAllowsRetention() {
        val serverKey = UploadRetentionPolicy.productionServerKey(
            settings = settings(UploadServerMode.PRODUCTION),
            uploadSucceeded = false,
            profileOwnsHealthConnect = true
        )

        assertNull(serverKey)
    }

    private fun settings(mode: UploadServerMode) = UploadSettings(
        serverMode = mode,
        productionBaseUrl = UploadEndpointPolicy.PRODUCTION_BASE_URL,
        localBaseUrl = UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL,
        apiKey = "k".repeat(UploadEndpointPolicy.MINIMUM_API_KEY_LENGTH),
        deviceId = "test-device"
    )
}
