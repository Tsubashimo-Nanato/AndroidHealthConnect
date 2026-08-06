package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadEndpointPolicyTest {
    private val validApiKey = "k".repeat(UploadEndpointPolicy.MINIMUM_API_KEY_LENGTH)

    @Test
    fun defaultLocalDebugBaseUsesFastApiPort() {
        assertEquals("http://10.0.2.2:8000/health/api/v1/", UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL)
    }

    @Test
    fun productionBuildsExpectedEndpoints() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                apiKey = validApiKey,
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
    fun productionAcceptsCustomHttpsBaseUrl() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                productionBaseUrl = "https://tsubashimonanato.com/health/api/v1",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals("https://tsubashimonanato.com/health/api/v1/", endpoint.baseUrl)
        assertEquals("https://tsubashimonanato.com/health/api/v1/status", endpoint.statusUrl)
        assertEquals("https://tsubashimonanato.com/health/api/v1/ingest/batches", endpoint.ingestBatchesUrl)
    }

    @Test
    fun productionAcceptsFullIngestEndpointAsInput() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                productionBaseUrl = "https://tsubashimonanato.com/health/api/v1/ingest/batches",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals("https://tsubashimonanato.com/health/api/v1/", endpoint.baseUrl)
        assertEquals("https://tsubashimonanato.com/health/api/v1/ingest/batches", endpoint.ingestBatchesUrl)
    }

    @Test
    fun productionAcceptsStatusEndpointAsInput() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                productionBaseUrl = "https://tsubashimonanato.com/health/api/v1/status",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals("https://tsubashimonanato.com/health/api/v1/", endpoint.baseUrl)
        assertEquals("https://tsubashimonanato.com/health/api/v1/status", endpoint.statusUrl)
        assertEquals("https://tsubashimonanato.com/health/api/v1/ingest/batches", endpoint.ingestBatchesUrl)
    }

    @Test
    fun localDebugAcceptsFullIngestEndpointAsInput() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://10.23.45.67:8000/health/api/v1/ingest/batches",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals("http://10.23.45.67:8000/health/api/v1/", endpoint.baseUrl)
        assertEquals("http://10.23.45.67:8000/health/api/v1/ingest/batches", endpoint.ingestBatchesUrl)
    }

    @Test
    fun productionRejectsCustomCleartextBaseUrl() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                productionBaseUrl = "http://tsubashimonanato.com/health/api/v1/",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Invalid)
    }

    @Test
    fun localDebugAcceptsPrivateHttpHost() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://10.23.45.67/health/api/v1",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals("http://10.23.45.67/health/api/v1/", endpoint.baseUrl)
        assertEquals("http://10.23.45.67/health/api/v1/status", endpoint.statusUrl)
        assertEquals("http://10.23.45.67/health/api/v1/ingest/batches", endpoint.ingestBatchesUrl)
    }

    @Test
    fun localDebugRejectsPhoneLoopbackHost() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://127.0.0.1:8000/health/api/v1",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Invalid)
        assertEquals(
            "Local debug URL must use 10.0.2.2 for the emulator or the PC LAN IP for a physical phone",
            (result as UploadEndpointValidation.Invalid).reason
        )
    }

    @Test
    fun localDebugRejectsLanPlaceholder() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://PC-LAN-IP:8000/health/api/v1",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Invalid)
        assertEquals(
            "Local debug URL must use 10.0.2.2 for the emulator or the PC LAN IP for a physical phone",
            (result as UploadEndpointValidation.Invalid).reason
        )
    }

    @Test
    fun localDebugKeepsEmulatorHost() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://10.0.2.2:8000/health/api/v1",
                apiKey = validApiKey,
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
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

    @Test
    fun authenticatedCallsUseTheSameKeyLengthBoundsAsTheServer() {
        val tooShort = UploadSettings(
            serverMode = UploadServerMode.PRODUCTION,
            apiKey = "k".repeat(UploadEndpointPolicy.MINIMUM_API_KEY_LENGTH - 1),
            deviceId = "device"
        )
        val maximumLength = tooShort.copy(
            apiKey = "k".repeat(UploadEndpointPolicy.MAXIMUM_API_KEY_LENGTH)
        )
        val tooLong = tooShort.copy(
            apiKey = "k".repeat(UploadEndpointPolicy.MAXIMUM_API_KEY_LENGTH + 1)
        )

        assertTrue(UploadEndpointPolicy.validate(tooShort) is UploadEndpointValidation.Invalid)
        assertTrue(UploadEndpointPolicy.validate(maximumLength) is UploadEndpointValidation.Valid)
        assertTrue(UploadEndpointPolicy.validate(tooLong) is UploadEndpointValidation.Invalid)
    }
}
