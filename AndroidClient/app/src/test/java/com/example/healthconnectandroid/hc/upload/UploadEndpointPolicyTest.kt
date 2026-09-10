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
    fun productionAcceptsCustomHttpsBaseUrl() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                productionBaseUrl = "https://tsubashimonanato.com/health/api/v1",
                apiKey = "key",
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
                apiKey = "key",
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
                apiKey = "key",
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
                localBaseUrl = "http://192.168.0.96:8000/health/api/v1/ingest/batches",
                apiKey = "key",
                deviceId = "device"
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals("http://192.168.0.96:8000/health/api/v1/", endpoint.baseUrl)
        assertEquals("http://192.168.0.96:8000/health/api/v1/ingest/batches", endpoint.ingestBatchesUrl)
    }

    @Test
    fun productionRejectsCustomCleartextBaseUrl() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                productionBaseUrl = "http://tsubashimonanato.com/health/api/v1/",
                apiKey = "key",
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
    fun localDebugRejectsPhoneLoopbackHost() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://127.0.0.1:8000/health/api/v1",
                apiKey = "key",
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
                apiKey = "key",
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
                apiKey = "key",
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
            UploadEndpointPolicy.validate(
                settings,
                requireAuthentication = false
            ) is UploadEndpointValidation.Valid
        )
    }

    @Test
    fun activeProfilePairingUsesBearerEndpointsWithoutApiKey() {
        val credential = profileCredential()
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                apiKey = "",
                deviceId = credential.clientDeviceId,
                profileCredential = credential
            )
        )

        assertTrue(result is UploadEndpointValidation.Valid)
        val endpoint = (result as UploadEndpointValidation.Valid).endpoint
        assertEquals("https://tsubashimonanato.com/health/api/v2/profile", endpoint.statusUrl)
        assertEquals(
            "https://tsubashimonanato.com/health/api/v2/ingest/batches",
            endpoint.ingestBatchesUrl
        )
        assertTrue(endpoint.authorization is UploadAuthorization.Bearer)
        assertEquals("server-profile", endpoint.profileIdentity?.profileId)
        assertEquals("installation", endpoint.profileIdentity?.installationId)
    }

    @Test
    fun pairingForAnotherModeDoesNotAuthorizeCurrentEndpoint() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.LOCAL_DEBUG,
                localBaseUrl = "http://10.0.2.2:8000/health/api/v2/",
                apiKey = "",
                deviceId = "device",
                profileCredential = profileCredential()
            )
        )

        assertTrue(result is UploadEndpointValidation.Invalid)
    }

    @Test
    fun expiredPairingRequiresAnotherScan() {
        val result = UploadEndpointPolicy.validate(
            UploadSettings(
                serverMode = UploadServerMode.PRODUCTION,
                apiKey = "",
                deviceId = "device",
                profileCredential = profileCredential().copy(expiresAtEpochMillis = 1L)
            )
        )

        assertTrue(result is UploadEndpointValidation.Invalid)
        assertEquals(
            "Pairing expired. Scan a new Pairing QR",
            (result as UploadEndpointValidation.Invalid).reason
        )
    }

    private fun profileCredential(): ProfileUploadCredential =
        ProfileUploadCredential(
            serverMode = UploadServerMode.PRODUCTION,
            serverProfileId = "server-profile",
            serverProfileName = "My server profile",
            installationId = "installation",
            clientDeviceId = "device",
            accessToken = "token",
            expiresAtEpochMillis = Long.MAX_VALUE,
            uploadBaseUrl = "https://tsubashimonanato.com/health/api/v2/"
        )
}
