package com.example.healthconnectandroid.security

import com.example.healthconnectandroid.hc.upload.UploadServerMode
import com.example.healthconnectandroid.hc.upload.UploadSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UploadSettingsPayloadCodecTest {
    @Test
    fun roundTripPreservesTheCompleteAtomicUploadConfiguration() {
        val expected = UploadSettings(
            serverMode = UploadServerMode.LOCAL_DEBUG,
            productionBaseUrl = "https://example.test/health/api/v1/",
            localBaseUrl = "http://10.0.2.2:8000/health/api/v1/",
            apiKey = "test-only-key",
            deviceId = "test-device",
            autoUploadEnabled = true
        )

        val actual = UploadSettingsPayloadCodec.decode(UploadSettingsPayloadCodec.encode(expected))

        assertEquals(expected, actual)
    }

    @Test
    fun endpointModeAndKeyChangeAsOneSelfConsistentSnapshot() {
        val next = UploadSettings(
            serverMode = UploadServerMode.PRODUCTION,
            productionBaseUrl = "https://new.example.test/health/api/v1/",
            localBaseUrl = "http://192.0.2.10:8000/health/api/v1/",
            apiKey = "new-test-only-key",
            deviceId = "test-device",
            autoUploadEnabled = true
        )

        val committedSnapshot = UploadSettingsPayloadCodec.decode(UploadSettingsPayloadCodec.encode(next))

        assertEquals(UploadServerMode.PRODUCTION, committedSnapshot.serverMode)
        assertEquals("https://new.example.test/health/api/v1/", committedSnapshot.productionBaseUrl)
        assertEquals("http://192.0.2.10:8000/health/api/v1/", committedSnapshot.localBaseUrl)
        assertEquals("new-test-only-key", committedSnapshot.apiKey)
    }

    @Test
    fun encoderNormalizesUserEnteredWhitespace() {
        val settings = UploadSettings(
            serverMode = UploadServerMode.PRODUCTION,
            productionBaseUrl = "  https://example.test/health/api/v1/  ",
            localBaseUrl = "  http://10.0.2.2:8000/health/api/v1/  ",
            apiKey = "  test-only-key  ",
            deviceId = "  test-device  ",
            autoUploadEnabled = false
        )

        val actual = UploadSettingsPayloadCodec.decode(UploadSettingsPayloadCodec.encode(settings))

        assertEquals("https://example.test/health/api/v1/", actual.productionBaseUrl)
        assertEquals("http://10.0.2.2:8000/health/api/v1/", actual.localBaseUrl)
        assertEquals("test-only-key", actual.apiKey)
        assertEquals("test-device", actual.deviceId)
    }

    @Test
    fun decoderRejectsAnUnsupportedPayloadVersion() {
        val payload = """{
            "version":2,
            "serverMode":"PRODUCTION",
            "productionBaseUrl":"https://example.test/",
            "localBaseUrl":"http://10.0.2.2/",
            "apiKey":"test-only-key",
            "deviceId":"test-device",
            "autoUploadEnabled":false
        }""".trimIndent().toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            UploadSettingsPayloadCodec.decode(payload)
        }
    }

    @Test
    fun decoderRejectsAnEmptyDeviceIdentifier() {
        val payload = """{
            "version":1,
            "serverMode":"PRODUCTION",
            "productionBaseUrl":"https://example.test/",
            "localBaseUrl":"http://10.0.2.2/",
            "apiKey":"test-only-key",
            "deviceId":"",
            "autoUploadEnabled":false
        }""".trimIndent().toByteArray()

        assertThrows(IllegalArgumentException::class.java) {
            UploadSettingsPayloadCodec.decode(payload)
        }
    }
}
