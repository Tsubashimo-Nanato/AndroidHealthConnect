package com.example.healthconnectandroid.hc.upload

import org.junit.Assert.assertFalse
import org.junit.Test

class HealthUploadHttpClientTest {
    @Test
    fun uploadClientNeverFollowsRedirectsThatCouldForwardCustomCredentials() {
        val client = HealthUploadService.defaultClient()

        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
    }
}
