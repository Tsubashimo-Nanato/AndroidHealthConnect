package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadScanPolicyTest {
    private val current = UploadSettings(
        apiKey = "",
        deviceId = "device"
    )

    @Test
    fun productionPairingUpdatesUrlAndApiKey() {
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?u=https://tsubashimonanato.com/health/api/v1/ingest/batches&k=GNEngwPSgt3cvma1POYU-phqvth9ctZn"
        )

        assertTrue(result is UploadScanApplyResult.Success)
        val settings = (result as UploadScanApplyResult.Success).settings
        assertEquals(UploadServerMode.PRODUCTION, settings.serverMode)
        assertEquals("https://tsubashimonanato.com/health/api/v1/", settings.productionBaseUrl)
        assertEquals("GNEngwPSgt3cvma1POYU-phqvth9ctZn", settings.apiKey)
        assertEquals("Paired Production upload endpoint", result.message)
    }

    @Test
    fun productionModePairingAcceptsApiBaseUrl() {
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?m=production&u=https%3A%2F%2Ftsubashimonanato.com%2Fhealth%2Fapi%2Fv1%2F&k=GNEngwPSgt3cvma1POYU-phqvth9ctZn"
        )

        assertTrue(result is UploadScanApplyResult.Success)
        val settings = (result as UploadScanApplyResult.Success).settings
        assertEquals(UploadServerMode.PRODUCTION, settings.serverMode)
        assertEquals("https://tsubashimonanato.com/health/api/v1/", settings.productionBaseUrl)
        assertEquals("GNEngwPSgt3cvma1POYU-phqvth9ctZn", settings.apiKey)
    }

    @Test
    fun pairingDecodesEncodedApiKey() {
        val encodedKey = "a".repeat(UploadEndpointPolicy.MINIMUM_API_KEY_LENGTH)
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?u=https://tsubashimonanato.com/health/api/v1/ingest/batches&k=$encodedKey"
        )

        assertTrue(result is UploadScanApplyResult.Success)
        val settings = (result as UploadScanApplyResult.Success).settings
        assertEquals(encodedKey, settings.apiKey)
    }

    @Test
    fun localPairingUpdatesLocalDebugBaseUrlAndApiKey() {
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?u=http://10.23.45.67:8000/health/api/v1/ingest/batches&k=GNEngwPSgt3cvma1POYU-phqvth9ctZn"
        )

        assertTrue(result is UploadScanApplyResult.Success)
        val settings = (result as UploadScanApplyResult.Success).settings
        assertEquals(UploadServerMode.LOCAL_DEBUG, settings.serverMode)
        assertEquals("http://10.23.45.67:8000/health/api/v1/", settings.localBaseUrl)
        assertEquals("GNEngwPSgt3cvma1POYU-phqvth9ctZn", settings.apiKey)
    }

    @Test
    fun localModePairingAcceptsApiBaseUrl() {
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?m=local&u=http%3A%2F%2F10.23.45.67%3A8000%2Fhealth%2Fapi%2Fv1%2F&k=GNEngwPSgt3cvma1POYU-phqvth9ctZn"
        )

        assertTrue(result is UploadScanApplyResult.Success)
        val settings = (result as UploadScanApplyResult.Success).settings
        assertEquals(UploadServerMode.LOCAL_DEBUG, settings.serverMode)
        assertEquals("http://10.23.45.67:8000/health/api/v1/", settings.localBaseUrl)
        assertEquals("GNEngwPSgt3cvma1POYU-phqvth9ctZn", settings.apiKey)
    }

    @Test
    fun pairingRejectsMissingApiKey() {
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?u=https://tsubashimonanato.com/health/api/v1/ingest/batches"
        )

        assertTrue(result is UploadScanApplyResult.Invalid)
    }

    @Test
    fun pairingRejectsAKeyBelowTheServerMinimum() {
        val shortKey = "k".repeat(UploadEndpointPolicy.MINIMUM_API_KEY_LENGTH - 1)
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?u=https://tsubashimonanato.com/health/api/v1/&k=$shortKey"
        )

        assertTrue(result is UploadScanApplyResult.Invalid)
    }

    @Test
    fun pairingRejectsPlainApiKeyQr() {
        val result = UploadScanPolicy.applyScannedText(
            current,
            "GNEngwPSgt3cvma1POYU-phqvth9ctZn"
        )

        assertTrue(result is UploadScanApplyResult.Invalid)
    }

    @Test
    fun pairingRejectsUnknownMode() {
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?m=staging&u=https://tsubashimonanato.com/health/api/v1/&k=GNEngwPSgt3cvma1POYU-phqvth9ctZn"
        )

        assertTrue(result is UploadScanApplyResult.Invalid)
        assertEquals("QR code pairing mode is not supported", (result as UploadScanApplyResult.Invalid).message)
    }

    @Test
    fun pairingRejectsBadQueryEncoding() {
        val result = UploadScanPolicy.applyScannedText(
            current,
            "nanato-hc://pair?u=https://tsubashimonanato.com/health/api/v1/ingest/batches&k=%zz"
        )

        assertTrue(result is UploadScanApplyResult.Invalid)
    }
}
