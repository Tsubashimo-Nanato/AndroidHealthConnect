package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadEndpointPolicyTest {
    @Test
    fun defaultLocalDebugBaseUsesFastApiPort() {
        assertEquals("http://10.0.2.2:8000/health/api/v1/", UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL)
    }

    @Test
    fun productionBuildsExpectedEndpoints() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                apiKey = "key",
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals(UploadEndpointPolicy.PRODUCTION_BASE_URL, endpoint.baseUrl)
        assertEquals("${UploadEndpointPolicy.PRODUCTION_BASE_URL}status", endpoint.statusUrl)
        assertEquals("${UploadEndpointPolicy.PRODUCTION_BASE_URL}ingest/batches", endpoint.ingestBatchesUrl)
    }

    @Test
    fun localDebugAcceptsPrivateHttpHost() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://192.168.0.96/health/api/v1",
                apiKey = "key",
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals("http://192.168.0.96/health/api/v1/", endpoint.baseUrl)
        assertEquals("http://192.168.0.96/health/api/v1/status", endpoint.statusUrl)
        assertEquals("http://192.168.0.96/health/api/v1/ingest/batches", endpoint.ingestBatchesUrl)
    }

    @Test
    fun localDebugRejectsPublicCleartextHost() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://example.com/health/api/v1/",
                apiKey = "key",
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Invalid)
    }

    @Test
    fun apiKeyIsRequiredForAuthenticatedCallsButNotPendingCounts() {
        val settings = UploadSettings(
            serverMode = UploadServerMode.PRODUCTION,
            apiKey = "",
            deviceId = "device"
        )

        assertTrue(UploadEndpointPolicy.validate(settings) is UploadEndpointValidation.Invalid)
        assertTrue(
            UploadEndpointPolicy.validate(settings, requireApiKey = false) is UploadEndpointValidation.Valid
        )
    }
}
