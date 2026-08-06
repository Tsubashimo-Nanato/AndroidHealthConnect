package com.example.healthconnectandroid

internal enum class SecureUploadSettingsState {
    VALUE,
    MISSING,
    CORRUPT,
    UNAVAILABLE
}

internal enum class LegacyUploadMigrationAction {
    USE_SECURE,
    MIGRATE_LEGACY,
    REQUIRE_REENTRY,
    DEFER
}

internal data class LegacyMigrationMarkerState(
    val migrationComplete: Boolean,
    val cleanupPending: Boolean
)

/** Keeps a damaged or previously migrated secure payload from reviving plaintext legacy data. */
internal object LegacyUploadMigrationPolicy {
    fun decide(
        secureState: SecureUploadSettingsState,
        migrationComplete: Boolean,
        hasLegacyConfiguration: Boolean
    ): LegacyUploadMigrationAction =
        when (secureState) {
            SecureUploadSettingsState.VALUE -> LegacyUploadMigrationAction.USE_SECURE
            SecureUploadSettingsState.MISSING -> {
                if (!migrationComplete && hasLegacyConfiguration) {
                    LegacyUploadMigrationAction.MIGRATE_LEGACY
                } else {
                    LegacyUploadMigrationAction.REQUIRE_REENTRY
                }
            }
            SecureUploadSettingsState.CORRUPT -> LegacyUploadMigrationAction.REQUIRE_REENTRY
            SecureUploadSettingsState.UNAVAILABLE -> LegacyUploadMigrationAction.DEFER
        }

    fun markerAfterCleanupAttempt(
        cleanupNeeded: Boolean,
        cleanupSucceeded: Boolean
    ): LegacyMigrationMarkerState =
        LegacyMigrationMarkerState(
            migrationComplete = true,
            cleanupPending = cleanupNeeded && !cleanupSucceeded
        )
}
