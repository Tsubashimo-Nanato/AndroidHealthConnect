package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadPairingServiceTest {
    private val pairing = UploadPairingCode(
        serverMode = UploadServerMode.PRODUCTION,
        apiBaseUrl = "https://tsubashimonanato.com/health/api/v2/",
        code = "one-time-code-1234"
    )

    @Test
    fun validResponseCreatesProfileCredential() {
        val result = parseCredentialResponse(
            rawJson = responseJson(
                uploadBaseUrl = "https://tsubashimonanato.com/health/api/v2/",
                expiresAtEpochMillis = Long.MAX_VALUE
            ),
            pairing = pairing,
            clientDeviceId = "device-1"
        )

        assertTrue(result is UploadPairingResult.Success)
        val credential = (result as UploadPairingResult.Success).credential
        assertEquals("0f30b70e-47b3-4e39-acf0-bd44e58c8c10", credential.serverProfileId)
        assertEquals("My profile", credential.serverProfileName)
        assertEquals("device-1", credential.clientDeviceId)
        assertEquals("https://tsubashimonanato.com/health/api/v2/", credential.uploadBaseUrl)
    }

    @Test
    fun responseCannotMoveCredentialToAnotherOrigin() {
        val result = parseCredentialResponse(
            rawJson = responseJson(
                uploadBaseUrl = "https://example.com/health/api/v2/",
                expiresAtEpochMillis = Long.MAX_VALUE
            ),
            pairing = pairing,
            clientDeviceId = "device-1"
        )

        assertTrue(result is UploadPairingResult.Failure)
        assertEquals(
            "Pairing response changed the server origin",
            (result as UploadPairingResult.Failure).message
        )
    }

    @Test
    fun responseCannotMoveCredentialToAnotherApiPath() {
        val result = parseCredentialResponse(
            rawJson = responseJson(
                uploadBaseUrl = "https://tsubashimonanato.com/health/api/v2-other/",
                expiresAtEpochMillis = Long.MAX_VALUE
            ),
            pairing = pairing,
            clientDeviceId = "device-1"
        )

        assertTrue(result is UploadPairingResult.Failure)
        assertEquals(
            "Pairing response changed the server endpoint",
            (result as UploadPairingResult.Failure).message
        )
    }

    @Test
    fun expiredCredentialIsRejectedAtPairingBoundary() {
        val result = parseCredentialResponse(
            rawJson = responseJson(
                uploadBaseUrl = "https://tsubashimonanato.com/health/api/v2/",
                expiresAtEpochMillis = 1L
            ),
            pairing = pairing,
            clientDeviceId = "device-1"
        )

        assertTrue(result is UploadPairingResult.Failure)
        assertEquals(
            "Pairing response token is already expired",
            (result as UploadPairingResult.Failure).message
        )
    }

    private fun responseJson(uploadBaseUrl: String, expiresAtEpochMillis: Long): String =
        """
        {
          "profile": {
            "profileId": "0f30b70e-47b3-4e39-acf0-bd44e58c8c10",
            "displayName": "My profile"
          },
          "installation": {
            "installationId": "b1b46065-35d7-433f-bb5c-e0c521117de1"
          },
          "accessToken": "abcdefghijklmnopqrstuvwxyz-1234567890",
          "expiresAtEpochMillis": $expiresAtEpochMillis,
          "uploadBaseUrl": "$uploadBaseUrl"
        }
        """.trimIndent()
}
