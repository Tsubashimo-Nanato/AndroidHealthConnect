package com.example.healthconnectandroid

import com.example.healthconnectandroid.hc.upload.ProfileUploadCredential
import com.example.healthconnectandroid.hc.upload.UploadServerMode
import com.example.healthconnectandroid.hc.upload.UploadSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadSettingsWritePolicyTest {
    private val validLengthKey = "k".repeat(32)

    private fun settings(
        apiKey: String = validLengthKey,
        credential: ProfileUploadCredential? = null
    ) = UploadSettings(
        apiKey = apiKey,
        deviceId = "device-id",
        profileCredential = credential
    )

    @Test
    fun temporaryUnavailabilityBlocksFallbackConfigurationWrites() {
        assertFalse(
            UploadSettingsWritePolicy.canWrite(
                storageReady = false,
                reentryRequired = false,
                settings = settings(),
                intent = UploadSettingsWriteIntent.CONFIGURATION
            )
        )
    }

    @Test
    fun confirmedCorruptionRequiresANonBlankReplacementKey() {
        assertFalse(
            UploadSettingsWritePolicy.canWrite(
                storageReady = false,
                reentryRequired = true,
                settings = settings(apiKey = ""),
                intent = UploadSettingsWriteIntent.CONFIGURATION
            )
        )
        assertTrue(
            UploadSettingsWritePolicy.canWrite(
                storageReady = false,
                reentryRequired = true,
                settings = settings(),
                intent = UploadSettingsWriteIntent.CONFIGURATION
            )
        )
    }

    @Test
    fun corruptionStateBlocksActionsThatCouldSilentlyReuseFallbackSettings() {
        assertFalse(
            UploadSettingsWritePolicy.canWrite(
                storageReady = false,
                reentryRequired = true,
                settings = settings(),
                intent = UploadSettingsWriteIntent.REQUIRES_EXISTING_CONFIGURATION
            )
        )
    }

    @Test
    fun loadedConfigurationAllowsNormalUpdates() {
        assertTrue(
            UploadSettingsWritePolicy.canWrite(
                storageReady = true,
                reentryRequired = false,
                settings = settings(),
                intent = UploadSettingsWriteIntent.REQUIRES_EXISTING_CONFIGURATION
            )
        )
    }

    @Test
    fun loadedConfigurationStillRejectsAKeyShorterThanTheServerMinimum() {
        assertFalse(
            UploadSettingsWritePolicy.canWrite(
                storageReady = true,
                reentryRequired = false,
                settings = settings(apiKey = "short"),
                intent = UploadSettingsWriteIntent.CONFIGURATION
            )
        )
    }

    @Test
    fun loadedConfigurationRejectsAKeyAboveTheServerMaximum() {
        assertFalse(
            UploadSettingsWritePolicy.canWrite(
                storageReady = true,
                reentryRequired = false,
                settings = settings(apiKey = "k".repeat(257)),
                intent = UploadSettingsWriteIntent.CONFIGURATION
            )
        )
    }

    @Test
    fun pairedConfigurationDoesNotRequireALegacyApiKey() {
        val credential = ProfileUploadCredential(
            serverMode = UploadServerMode.PRODUCTION,
            serverProfileId = "server-profile",
            serverProfileName = "Profile",
            installationId = "installation",
            clientDeviceId = "client-device",
            accessToken = "token",
            expiresAtEpochMillis = Long.MAX_VALUE,
            uploadBaseUrl = "https://www.tsubashimonanato.com/health/api/v1/"
        )

        assertTrue(
            UploadSettingsWritePolicy.canWrite(
                storageReady = true,
                reentryRequired = false,
                settings = settings(apiKey = "", credential = credential),
                intent = UploadSettingsWriteIntent.REQUIRES_EXISTING_CONFIGURATION
            )
        )
    }
}
