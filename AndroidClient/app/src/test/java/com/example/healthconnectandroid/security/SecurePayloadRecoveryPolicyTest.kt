package com.example.healthconnectandroid.security

import org.junit.Assert.assertEquals
import org.junit.Test

class SecurePayloadRecoveryPolicyTest {
    @Test
    fun temporaryProviderLockOrIoFailureDefersWithoutChangingStoredMaterial() {
        val plan = SecurePayloadRecoveryPolicy.plan(
            SecurePayloadFailureKind.TEMPORARILY_UNAVAILABLE
        )

        assertEquals(SecureUploadSettingsReadResult.Unavailable, plan.result)
    }

    @Test
    fun permanentKeystoreInvalidationRequiresExplicitCredentialReentry() {
        val plan = SecurePayloadRecoveryPolicy.plan(SecurePayloadFailureKind.CORRUPT)

        assertEquals(SecureUploadSettingsReadResult.Corrupt, plan.result)
    }
}
