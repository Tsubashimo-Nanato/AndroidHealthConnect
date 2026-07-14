package com.example.healthconnectandroid.hc.upload

import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadCapabilityPolicyTest {
    @Test
    fun missingCapabilityKeepsLegacyBatching() {
        assertEquals(UploadCapabilityPolicy.Legacy, UploadCapabilityPolicy.fromStatusJson("{\"status\":\"ok\"}"))
        assertEquals(UploadCapabilityPolicy.Legacy, UploadCapabilityPolicy.fromStatusJson("not-json"))
    }

    @Test
    fun gzipCapabilityEnablesLargerServerRecommendedBatches() {
        val profile = UploadCapabilityPolicy.fromStatusJson(
            """
            {
              "uploadCapabilities": {
                "requestCompression": ["gzip"],
                "batchLimits": {"records": 3500, "values": 10000, "aggregates": 1500}
              }
            }
            """.trimIndent()
        )

        assertEquals(UploadRequestCompression.GZIP, profile.requestCompression)
        assertEquals(3_500, profile.recordLimit)
        assertEquals(10_000, profile.valueLimit)
        assertEquals(1_500, profile.aggregateLimit)
    }

    @Test
    fun untrustedServerLimitsAreCappedLocally() {
        val profile = UploadCapabilityPolicy.fromStatusJson(
            """
            {
              "uploadCapabilities": {
                "requestCompression": ["gzip"],
                "batchLimits": {"records": 999999, "values": 999999, "aggregates": 999999}
              }
            }
            """.trimIndent()
        )

        assertEquals(5_000, profile.recordLimit)
        assertEquals(20_000, profile.valueLimit)
        assertEquals(5_000, profile.aggregateLimit)
    }

    @Test
    fun unknownCapabilityVersionFallsBackToLegacy() {
        val profile = UploadCapabilityPolicy.fromStatusJson(
            """
            {
              "uploadCapabilities": {
                "protocolVersion": 2,
                "requestCompression": ["gzip"]
              }
            }
            """.trimIndent()
        )

        assertEquals(UploadCapabilityPolicy.Legacy, profile)
    }

    @Test
    fun gzipBodyRoundTripsWithoutChangingJson() {
        val json = "{\"schemaVersion\":1,\"records\":[{\"localId\":42}]}"
        val source = json.toRequestBody("application/json".toMediaType())
        val sink = Buffer()
        val body = GzipRequestBody(source)

        assertTrue(body.contentLength() > 0)
        body.writeTo(sink)
        assertEquals(body.contentLength(), sink.size)

        val restored = GZIPInputStream(ByteArrayInputStream(sink.readByteArray()))
            .bufferedReader()
            .use { it.readText() }
        assertEquals(json, restored)
    }
}
