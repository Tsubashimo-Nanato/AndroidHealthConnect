package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadHttpFailurePolicyTest {
    @Test
    fun authFailureIncludesEndpointAndServerDetail() {
        val failure = UploadHttpFailurePolicy.fromResponse(
            action = UploadHttpAction.INGEST,
            url = "https://tsubashimonanato.com/health/api/v1/ingest/batches",
            code = 401,
            responseBody = """{"detail":"Invalid HealthConnect API key"}"""
        )

        assertFalse(failure.retryable)
        assertEquals(UploadFailureKind.API_KEY_INVALID, failure.failureKind)
        assertTrue(failure.message.contains("Upload authentication failed (401)"))
        assertTrue(failure.message.contains("https://tsubashimonanato.com/health/api/v1/ingest/batches"))
        assertTrue(failure.message.contains("Server response: Invalid HealthConnect API key"))
    }

    @Test
    fun sessionFailurePointsAtWrongEndpoint() {
        val failure = UploadHttpFailurePolicy.fromResponse(
            action = UploadHttpAction.INGEST,
            url = "https://tsubashimonanato.com/admin/healthconnect/",
            code = 401,
            responseBody = """{"detail":"Missing session"}"""
        )

        assertEquals(UploadFailureKind.API_KEY_INVALID, failure.failureKind)
        assertTrue(failure.message.contains("session-protected route"))
    }

    @Test
    fun validationFailureKeepsResponseDetail() {
        val failure = UploadHttpFailurePolicy.fromResponse(
            action = UploadHttpAction.INGEST,
            url = "https://tsubashimonanato.com/health/api/v1/ingest/batches",
            code = 422,
            responseBody = """{"detail":"schemaVersion is required"}"""
        )

        assertFalse(failure.retryable)
        assertEquals(UploadFailureKind.SERVER_VALIDATION, failure.failureKind)
        assertTrue(failure.message.contains("Server response: schemaVersion is required"))
    }

    @Test
    fun expiredProfileTokenRequestsAnotherPairing() {
        val failure = UploadHttpFailurePolicy.fromResponse(
            action = UploadHttpAction.STATUS,
            url = "https://tsubashimonanato.com/health/api/v2/profile",
            code = 401,
            responseBody = """
                {"detail":{"code":"installation_token_expired","message":"Bearer token has expired."}}
            """.trimIndent()
        )

        assertFalse(failure.retryable)
        assertTrue(failure.message.contains("saved pairing expired"))
        assertTrue(failure.message.contains("Server response: Bearer token has expired."))
    }
}
