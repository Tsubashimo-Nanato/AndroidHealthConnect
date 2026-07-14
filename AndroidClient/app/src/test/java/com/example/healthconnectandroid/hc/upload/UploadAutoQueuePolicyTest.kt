package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadAutoQueuePolicyTest {
    @Test
    fun disabledSettingDoesNotQueueUpload() {
        val decision = UploadAutoQueuePolicy.decide(settings(autoUploadEnabled = false))

        assertEquals(UploadAutoQueueDecision.Disabled, decision)
    }

    @Test
    fun enabledProductionSettingQueuesUpload() {
        val decision = UploadAutoQueuePolicy.decide(settings(autoUploadEnabled = true))

        assertTrue(decision is UploadAutoQueueDecision.Queue)
        assertEquals(
            "https://tsubashimonanato.com/health/api/v1/",
            (decision as UploadAutoQueueDecision.Queue).endpoint.baseUrl
        )
    }

    @Test
    fun enabledSettingRequiresApiKey() {
        val decision = UploadAutoQueuePolicy.decide(
            settings(autoUploadEnabled = true, apiKey = "")
        )

        assertEquals(
            UploadAutoQueueDecision.Invalid("API key is required"),
            decision
        )
    }

    private fun settings(
        autoUploadEnabled: Boolean,
        apiKey: String = "test-api-key"
    ): UploadSettings =
        UploadSettings(
            serverMode = UploadServerMode.PRODUCTION,
            productionBaseUrl = "https://tsubashimonanato.com/health/api/v1/ingest/batches",
            localBaseUrl = UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL,
            apiKey = apiKey,
            deviceId = "test-device",
            autoUploadEnabled = autoUploadEnabled
        )
}
