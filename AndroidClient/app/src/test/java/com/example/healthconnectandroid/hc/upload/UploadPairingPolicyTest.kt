package com.example.healthconnectandroid.hc.upload

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadPairingPolicyTest {
    private val current = UploadSettings(
        apiKey = "",
        deviceId = "device"
    )

    @Test
    fun productionIngestJsonPairsProductionEndpointAndKey() {
        val result = UploadPairingPolicy.applyPairingText(
            current,
            """
            {
              "type": "healthconnect-upload-pairing",
              "ingestUrl": "https://www.tsubashimonanato.com/health/api/v1/ingest/batches",
              "apiKey": "production-test-key"
            }
            """.trimIndent()
        )

        assertTrue(result is UploadPairingApplyResult.Success)
        val settings = (result as UploadPairingApplyResult.Success).settings
        assertEquals(UploadServerMode.PRODUCTION, settings.serverMode)
        assertEquals("production-test-key", settings.apiKey)
    }

    @Test
    fun localDebugIngestUrlPairsLocalEndpoint() {
        val result = UploadPairingPolicy.applyPairingText(
            current.copy(apiKey = "existing-key"),
            "http://192.168.0.96:8000/health/api/v1/ingest/batches"
        )

        assertTrue(result is UploadPairingApplyResult.Success)
        val settings = (result as UploadPairingApplyResult.Success).settings
        assertEquals(UploadServerMode.LOCAL_DEBUG, settings.serverMode)
        assertEquals("http://192.168.0.96:8000/health/api/v1/", settings.localBaseUrl)
        assertEquals("existing-key", settings.apiKey)
    }

    @Test
    fun queryPayloadPairsEndpointAndKey() {
        val endpoint = URLEncoder.encode(
            "http://10.0.2.2:8000/health/api/v1/status",
            StandardCharsets.UTF_8.name()
        )
        val result = UploadPairingPolicy.applyPairingText(
            current,
            "healthconnect://pair?uploadUrl=$endpoint&api_key=query-test-key"
        )

        assertTrue(result is UploadPairingApplyResult.Success)
        val settings = (result as UploadPairingApplyResult.Success).settings
        assertEquals(UploadServerMode.LOCAL_DEBUG, settings.serverMode)
        assertEquals("http://10.0.2.2:8000/health/api/v1/", settings.localBaseUrl)
        assertEquals("query-test-key", settings.apiKey)
    }

    @Test
    fun escapedJsonPayloadPairsEndpointAndKey() {
        val result = UploadPairingPolicy.applyPairingText(
            current,
            """
            {
              "upload_url": "http:\/\/10.0.2.2:8000\/health\/api\/v1\/status",
              "x_api_key": "escaped-json-key"
            }
            """.trimIndent()
        )

        assertTrue(result is UploadPairingApplyResult.Success)
        val settings = (result as UploadPairingApplyResult.Success).settings
        assertEquals(UploadServerMode.LOCAL_DEBUG, settings.serverMode)
        assertEquals("http://10.0.2.2:8000/health/api/v1/", settings.localBaseUrl)
        assertEquals("escaped-json-key", settings.apiKey)
    }

    @Test
    fun apiKeyOnlyPairsKeyWithoutChangingEndpoint() {
        val result = UploadPairingPolicy.applyPairingText(
            current.copy(serverMode = UploadServerMode.PRODUCTION),
            "key-only-value-12345"
        )

        assertTrue(result is UploadPairingApplyResult.Success)
        val settings = (result as UploadPairingApplyResult.Success).settings
        assertEquals(UploadServerMode.PRODUCTION, settings.serverMode)
        assertEquals("key-only-value-12345", settings.apiKey)
    }

    @Test
    fun invalidTextIsRejected() {
        val result = UploadPairingPolicy.applyPairingText(current, "not a pairing qr")

        assertTrue(result is UploadPairingApplyResult.Invalid)
    }

    @Test
    fun malformedJsonIsRejectedInsteadOfPartiallyParsed() {
        val result = UploadPairingPolicy.applyPairingText(
            current,
            """{"apiKey":"malformed-json-key","uploadUrl":"""
        )

        assertTrue(result is UploadPairingApplyResult.Invalid)
    }
}
