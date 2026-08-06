package com.example.healthconnectandroid

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyUploadMigrationPolicyTest {
    @Test
    fun plaintextMigrationOnlyRunsForMissingUnmigratedSecureState() {
        assertEquals(
            LegacyUploadMigrationAction.MIGRATE_LEGACY,
            LegacyUploadMigrationPolicy.decide(
                SecureUploadSettingsState.MISSING,
                migrationComplete = false,
                hasLegacyConfiguration = true
            )
        )
    }

    @Test
    fun corruptionNeverRevivesLegacyConfiguration() {
        assertEquals(
            LegacyUploadMigrationAction.REQUIRE_REENTRY,
            LegacyUploadMigrationPolicy.decide(
                SecureUploadSettingsState.CORRUPT,
                migrationComplete = false,
                hasLegacyConfiguration = true
            )
        )
    }

    @Test
    fun migrationMarkerPreventsLegacyResurrectionAfterSecurePayloadDisappears() {
        assertEquals(
            LegacyUploadMigrationAction.REQUIRE_REENTRY,
            LegacyUploadMigrationPolicy.decide(
                SecureUploadSettingsState.MISSING,
                migrationComplete = true,
                hasLegacyConfiguration = true
            )
        )
    }

    @Test
    fun temporaryStorageFailureDefersInsteadOfMigrating() {
        assertEquals(
            LegacyUploadMigrationAction.DEFER,
            LegacyUploadMigrationPolicy.decide(
                SecureUploadSettingsState.UNAVAILABLE,
                migrationComplete = false,
                hasLegacyConfiguration = true
            )
        )
    }

    @Test
    fun failedLegacyCleanupIsRecordedForRetry() {
        assertEquals(
            LegacyMigrationMarkerState(migrationComplete = true, cleanupPending = true),
            LegacyUploadMigrationPolicy.markerAfterCleanupAttempt(
                cleanupNeeded = true,
                cleanupSucceeded = false
            )
        )
        assertEquals(
            LegacyMigrationMarkerState(migrationComplete = true, cleanupPending = false),
            LegacyUploadMigrationPolicy.markerAfterCleanupAttempt(
                cleanupNeeded = true,
                cleanupSucceeded = true
            )
        )
    }
}
