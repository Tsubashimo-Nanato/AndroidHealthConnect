package com.example.healthconnectandroid

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadSettingsWritePolicyTest {
    private val validLengthKey = "k".repeat(32)

    @Test
    fun temporaryUnavailabilityBlocksFallbackConfigurationWrites() {
        assertFalse(
            UploadSettingsWritePolicy.canWrite(
                storageReady = false,
                reentryRequired = false,
                apiKey = validLengthKey,
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
                apiKey = "",
                intent = UploadSettingsWriteIntent.CONFIGURATION
            )
        )
        assertTrue(
            UploadSettingsWritePolicy.canWrite(
                storageReady = false,
                reentryRequired = true,
                apiKey = validLengthKey,
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
                apiKey = validLengthKey,
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
                apiKey = validLengthKey,
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
                apiKey = "short",
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
                apiKey = "k".repeat(257),
                intent = UploadSettingsWriteIntent.CONFIGURATION
            )
        )
    }
}
