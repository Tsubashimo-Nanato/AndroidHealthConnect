package com.example.healthconnectandroid

import android.content.Context
import android.content.SharedPreferences
import com.example.healthconnectandroid.hc.upload.UploadEndpointPolicy
import com.example.healthconnectandroid.hc.upload.ProfilePairingStore
import com.example.healthconnectandroid.hc.upload.ProfileUploadCredential
import com.example.healthconnectandroid.hc.upload.UploadEndpointValidation
import com.example.healthconnectandroid.hc.upload.UploadResultSeverity
import com.example.healthconnectandroid.hc.upload.UploadServerMode
import com.example.healthconnectandroid.hc.upload.UploadSettings
import com.example.healthconnectandroid.hc.upload.UploadStatus
import com.example.healthconnectandroid.security.SecureUploadSettingsReadResult
import com.example.healthconnectandroid.security.SecureUploadSettingsStore
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.DayOfWeek
import java.util.UUID

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

enum class AppThemePalette(val label: String, val description: String) {
    PAPER("Paper", "Soft paper and teal"),
    RAIN("Rain", "Blue-gray glass"),
    MILK("Milk", "Cream and tea warmth"),
    HOODIE("Hoodie", "Soft gray-brown"),
    SAGE("Sage", "Oatmeal and green")
}

enum class ProfileSex(val label: String) {
    NOT_SET("Not set"),
    FEMALE("Female"),
    MALE("Male"),
    OTHER("Other / Prefer not to say")
}

enum class WeekStartPreference(val label: String, val dayOfWeek: DayOfWeek) {
    SUNDAY("Sunday", DayOfWeek.SUNDAY),
    MONDAY("Monday", DayOfWeek.MONDAY)
}

enum class UnitSystemPreference(val label: String) {
    METRIC("Metric"),
    IMPERIAL("Imperial")
}

enum class TimeZonePreferenceMode(val label: String) {
    SYSTEM("System timezone"),
    CUSTOM("Custom timezone")
}

enum class AppLanguagePreference(val label: String) {
    ENGLISH("English"),
    CHINESE_SIMPLIFIED("简体中文")
}

data class UserPreferences(
    val weekStart: WeekStartPreference = WeekStartPreference.SUNDAY,
    val unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC,
    val timeZoneMode: TimeZonePreferenceMode = TimeZonePreferenceMode.SYSTEM,
    val customTimeZoneId: String? = null,
    val language: AppLanguagePreference = AppLanguagePreference.ENGLISH
) {
    val zoneId: ZoneId
        get() = if (timeZoneMode == TimeZonePreferenceMode.CUSTOM) {
            customTimeZoneId
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: ZoneId.systemDefault()
        } else {
            ZoneId.systemDefault()
        }
}

object AgeCalculator {
    fun ageOn(dateOfBirth: LocalDate?, today: LocalDate = LocalDate.now()): Int? {
        if (dateOfBirth == null) return null
        if (dateOfBirth.isAfter(today)) return null
        return Period.between(dateOfBirth, today).years.takeIf { it in 0..120 }
    }
}

data class UserProfile(
    val sex: ProfileSex = ProfileSex.NOT_SET,
    val dateOfBirthIso: String? = null,
    val weightKg: Double? = null
) {
    val dateOfBirth: LocalDate?
        get() = dateOfBirthIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val age: Int?
        get() = dateOfBirth?.let(AgeCalculator::ageOn)
}

sealed interface UploadSettingsLoadResult {
    val settings: UploadSettings

    data class Available(override val settings: UploadSettings) : UploadSettingsLoadResult
    data class SecureStorageUnavailable(override val settings: UploadSettings) : UploadSettingsLoadResult
    data class ReentryRequired(override val settings: UploadSettings) : UploadSettingsLoadResult
}

object AppPreferences {
    private const val PREFS_NAME = "health_connect_app_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_THEME_PALETTE = "theme_palette"
    private const val KEY_USER_AGE = "user_age"
    private const val KEY_USER_DOB = "user_date_of_birth"
    private const val KEY_USER_SEX = "user_sex"
    private const val KEY_USER_WEIGHT_KG = "user_weight_kg"
    private const val KEY_WEEK_START = "week_start"
    private const val KEY_UNIT_SYSTEM = "unit_system"
    private const val KEY_TIMEZONE_MODE = "timezone_mode"
    private const val KEY_CUSTOM_TIMEZONE = "custom_timezone"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_DEBUG_MODE_ENABLED = "debug_mode_enabled"
    private const val KEY_UPLOAD_SERVER_MODE = "upload_server_mode"
    private const val KEY_UPLOAD_PRODUCTION_URL = "upload_production_url"
    private const val KEY_UPLOAD_LOCAL_URL = "upload_local_url"
    private const val KEY_UPLOAD_API_KEY = "upload_api_key"
    private const val KEY_UPLOAD_DEVICE_ID = "upload_device_id"
    private const val KEY_UPLOAD_AUTO_ENABLED = "upload_auto_enabled"
    private const val KEY_UPLOAD_SECURE_MIGRATION_COMPLETE = "upload_secure_migration_complete"
    private const val KEY_UPLOAD_LEGACY_CLEANUP_PENDING = "upload_legacy_cleanup_pending"
    private const val KEY_UPLOAD_LAST_TIME = "upload_last_time"
    private const val KEY_UPLOAD_LAST_RESULT = "upload_last_result"
    private const val KEY_UPLOAD_LAST_SEVERITY = "upload_last_severity"
    private const val KEY_UPLOAD_PENDING_COUNT = "upload_pending_count"
    private const val KEY_UPLOAD_STATUS_SERVER_MODE = "upload_status_server_mode"
    private const val KEY_UPLOAD_CONNECTION_RESULT = "upload_connection_result"
    private const val KEY_MEDICINE_OVERLAY_REMINDER_ENABLED = "medicine_overlay_reminder_enabled"
    private const val KEY_MEDICINE_SEED_VERSION = "medicine_seed_version"

    fun themeMode(context: Context): AppThemeMode {
        val raw = prefs(context).getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return AppThemeMode.values().firstOrNull { it.name == raw } ?: AppThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun themePalette(context: Context): AppThemePalette {
        val raw = prefs(context).getString(KEY_THEME_PALETTE, AppThemePalette.PAPER.name)
        val normalized = when (raw) {
            "NANATO", "WARM_PAPER", "PAPER_STAR" -> AppThemePalette.PAPER.name
            "RAIN_GLASS" -> AppThemePalette.RAIN.name
            "MILK_TEA" -> AppThemePalette.MILK.name
            "NIGHT_ROOM", "HOODIE_NIGHT" -> AppThemePalette.HOODIE.name
            "CARDIGAN_SAGE" -> AppThemePalette.SAGE.name
            else -> raw
        }
        return AppThemePalette.values().firstOrNull { it.name == normalized } ?: AppThemePalette.PAPER
    }

    fun setThemePalette(context: Context, palette: AppThemePalette) {
        prefs(context).edit().putString(KEY_THEME_PALETTE, palette.name).apply()
    }

    fun debugModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEBUG_MODE_ENABLED, false)

    fun setDebugModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEBUG_MODE_ENABLED, enabled).apply()
    }

    fun medicineOverlayReminderEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEDICINE_OVERLAY_REMINDER_ENABLED, false)

    fun setMedicineOverlayReminderEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_MEDICINE_OVERLAY_REMINDER_ENABLED, enabled).apply()
    }

    fun medicineSeedVersion(context: Context, profileId: String): Int =
        prefs(context).getInt(profilePreferenceKey(KEY_MEDICINE_SEED_VERSION, profileId), 0)

    fun setMedicineSeedVersion(context: Context, profileId: String, version: Int) {
        prefs(context).edit()
            .putInt(profilePreferenceKey(KEY_MEDICINE_SEED_VERSION, profileId), version)
            .apply()
    }

    fun userAge(context: Context): Int? {
        return userProfile(context).age
    }

    fun setUserAge(context: Context, age: Int?) {
        val current = userProfile(context)
        // Age is now derived from DOB. Keep this compatibility method as a no-op unless callers pass null.
        if (age == null) setUserProfile(context, current.copy(dateOfBirthIso = null))
    }

    fun userProfile(context: Context): UserProfile {
        return LocalProfileStore.activeProfile(context).userProfile
    }

    internal fun legacyUserProfile(context: Context): UserProfile {
        val prefs = prefs(context)
        val sex = prefs.getString(KEY_USER_SEX, null)
            ?.let { raw -> ProfileSex.values().firstOrNull { it.name == raw } }
            ?: prefs.getString(KEY_USER_SEX, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { ProfileSex.OTHER }
            ?: ProfileSex.NOT_SET
        val dob = prefs.getString(KEY_USER_DOB, null)
            ?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
        val weight = prefs.getFloat(KEY_USER_WEIGHT_KG, 0f)
            .takeIf { it in 20f..350f }
            ?.toDouble()
        return UserProfile(sex = sex, dateOfBirthIso = dob, weightKg = weight)
    }

    fun setUserProfile(context: Context, profile: UserProfile) {
        LocalProfileStore.updateUserProfile(context, profile)
    }

    fun userPreferences(context: Context): UserPreferences {
        val prefs = prefs(context)
        val weekStart = prefs.getString(KEY_WEEK_START, WeekStartPreference.SUNDAY.name)
            ?.let { raw -> WeekStartPreference.values().firstOrNull { it.name == raw } }
            ?: WeekStartPreference.SUNDAY
        val unitSystem = prefs.getString(KEY_UNIT_SYSTEM, UnitSystemPreference.METRIC.name)
            ?.let { raw -> UnitSystemPreference.values().firstOrNull { it.name == raw } }
            ?: UnitSystemPreference.METRIC
        val timeZoneMode = prefs.getString(KEY_TIMEZONE_MODE, TimeZonePreferenceMode.SYSTEM.name)
            ?.let { raw -> TimeZonePreferenceMode.values().firstOrNull { it.name == raw } }
            ?: TimeZonePreferenceMode.SYSTEM
        val customTimeZone = prefs.getString(KEY_CUSTOM_TIMEZONE, null)
            ?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
        val language = prefs.getString(KEY_LANGUAGE, AppLanguagePreference.ENGLISH.name)
            ?.let { raw -> AppLanguagePreference.values().firstOrNull { it.name == raw } }
            ?: AppLanguagePreference.ENGLISH
        return UserPreferences(
            weekStart = weekStart,
            unitSystem = unitSystem,
            timeZoneMode = timeZoneMode,
            customTimeZoneId = customTimeZone,
            language = language
        )
    }

    fun setUserPreferences(context: Context, preferences: UserPreferences) {
        prefs(context).edit().apply {
            putString(KEY_WEEK_START, preferences.weekStart.name)
            putString(KEY_UNIT_SYSTEM, preferences.unitSystem.name)
            putString(KEY_TIMEZONE_MODE, preferences.timeZoneMode.name)
            putString(KEY_LANGUAGE, preferences.language.name)
            val normalizedZone = preferences.customTimeZoneId
                ?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
            if (normalizedZone == null) remove(KEY_CUSTOM_TIMEZONE) else putString(KEY_CUSTOM_TIMEZONE, normalizedZone)
        }.apply()
    }

    @Synchronized
    fun loadUploadSettings(
        context: Context,
        profileId: String = LocalProfileStore.activeProfile(context).id
    ): UploadSettingsLoadResult {
        val profile = requireProfile(context, profileId)
        val prefs = prefs(context)
        val credential = ProfilePairingStore.load(context, profileId)
        val key = uploadPreferenceKey(profile)
        val secureScope = secureUploadScope(profile)
        val secureResult = SecureUploadSettingsStore.read(context, secureScope)
        val secureState = when (secureResult) {
            is SecureUploadSettingsReadResult.Value -> SecureUploadSettingsState.VALUE
            SecureUploadSettingsReadResult.Missing -> SecureUploadSettingsState.MISSING
            SecureUploadSettingsReadResult.Corrupt -> SecureUploadSettingsState.CORRUPT
            SecureUploadSettingsReadResult.Unavailable -> SecureUploadSettingsState.UNAVAILABLE
        }
        val action = LegacyUploadMigrationPolicy.decide(
            secureState = secureState,
            migrationComplete = prefs.getBoolean(key(KEY_UPLOAD_SECURE_MIGRATION_COMPLETE), false),
            hasLegacyConfiguration = hasLegacyUploadPreferences(prefs, key)
        )
        return when (action) {
            LegacyUploadMigrationAction.USE_SECURE -> {
                recordMigrationCompleteAndCleanup(prefs, key)
                UploadSettingsLoadResult.Available(
                    (secureResult as SecureUploadSettingsReadResult.Value)
                        .settings
                        .copy(profileCredential = credential)
                )
            }
            LegacyUploadMigrationAction.USE_DEFAULTS ->
                UploadSettingsLoadResult.Available(
                    defaultUploadSettings(context, profile, credential)
                )
            LegacyUploadMigrationAction.MIGRATE_LEGACY ->
                migrateLegacyUploadSettings(context, prefs, profile, credential, key, secureScope)
            LegacyUploadMigrationAction.REQUIRE_REENTRY -> {
                // Mark the one-way boundary before cleanup. A damaged or removed secure payload
                // must never make an older plaintext API key authoritative again.
                recordMigrationCompleteAndCleanup(prefs, key)
                UploadSettingsLoadResult.ReentryRequired(
                    defaultUploadSettings(context, profile, credential)
                )
            }
            LegacyUploadMigrationAction.DEFER ->
                UploadSettingsLoadResult.SecureStorageUnavailable(
                    defaultUploadSettings(context, profile, credential)
                )
        }
    }

    @Synchronized
    fun setUploadSettings(
        context: Context,
        settings: UploadSettings,
        profileId: String = LocalProfileStore.activeProfile(context).id,
        replaceCorruptKey: Boolean = false
    ): Boolean {
        val profile = requireProfile(context, profileId)
        val key = uploadPreferenceKey(profile)
        if (UploadEndpointPolicy.validate(settings) is UploadEndpointValidation.Invalid) return false
        if (!SecureUploadSettingsStore.write(
                context = context,
                settings = settings,
                storageScope = secureUploadScope(profile),
                replaceCorruptKey = replaceCorruptKey
            )
        ) return false

        recordMigrationCompleteAndCleanup(prefs(context), key)
        return true
    }

    private fun migrateLegacyUploadSettings(
        context: Context,
        prefs: SharedPreferences,
        profile: LocalProfile,
        credential: ProfileUploadCredential?,
        key: (String) -> String,
        secureScope: String?
    ): UploadSettingsLoadResult {
        val settings = legacyUploadSettings(context, prefs, credential, key)
        if (UploadEndpointPolicy.validate(settings) is UploadEndpointValidation.Invalid) {
            recordMigrationCompleteAndCleanup(prefs, key)
            return UploadSettingsLoadResult.ReentryRequired(
                defaultUploadSettings(context, profile, credential)
            )
        }
        return if (SecureUploadSettingsStore.write(context, settings, secureScope)) {
            recordMigrationCompleteAndCleanup(prefs, key)
            UploadSettingsLoadResult.Available(settings)
        } else {
            UploadSettingsLoadResult.SecureStorageUnavailable(settings)
        }
    }

    private fun legacyUploadSettings(
        context: Context,
        prefs: SharedPreferences,
        credential: ProfileUploadCredential?,
        key: (String) -> String
    ): UploadSettings {
        val defaultMode = credential?.serverMode ?: UploadServerMode.PRODUCTION
        val mode = prefs.getString(key(KEY_UPLOAD_SERVER_MODE), defaultMode.name)
            ?.let { raw -> UploadServerMode.values().firstOrNull { it.name == raw } }
            ?: defaultMode
        val defaultLocalUrl = credential
            ?.takeIf { it.serverMode == UploadServerMode.LOCAL_DEBUG }
            ?.uploadBaseUrl
            ?: UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL
        val defaultProductionUrl = credential
            ?.takeIf { it.serverMode == UploadServerMode.PRODUCTION }
            ?.uploadBaseUrl
            ?: UploadEndpointPolicy.PRODUCTION_BASE_URL
        val localUrl = prefs.getString(key(KEY_UPLOAD_LOCAL_URL), defaultLocalUrl) ?: defaultLocalUrl
        val productionUrl = prefs.getString(key(KEY_UPLOAD_PRODUCTION_URL), defaultProductionUrl)
            ?: defaultProductionUrl
        return UploadSettings(
            serverMode = mode,
            productionBaseUrl = productionUrl,
            localBaseUrl = localUrl,
            apiKey = prefs.getString(key(KEY_UPLOAD_API_KEY), "")?.trim().orEmpty(),
            deviceId = uploadDeviceId(context, key(KEY_UPLOAD_DEVICE_ID)),
            autoUploadEnabled = prefs.getBoolean(key(KEY_UPLOAD_AUTO_ENABLED), false),
            profileCredential = credential
        )
    }

    private fun defaultUploadSettings(
        context: Context,
        profile: LocalProfile,
        credential: ProfileUploadCredential?
    ): UploadSettings {
        val key = uploadPreferenceKey(profile)
        val defaults = UploadSettings(
            deviceId = uploadDeviceId(context, key(KEY_UPLOAD_DEVICE_ID)),
            profileCredential = credential
        )
        if (credential == null) return defaults
        return defaults.copy(
            serverMode = credential.serverMode,
            productionBaseUrl = if (credential.serverMode == UploadServerMode.PRODUCTION) {
                credential.uploadBaseUrl
            } else {
                defaults.productionBaseUrl
            },
            localBaseUrl = if (credential.serverMode == UploadServerMode.LOCAL_DEBUG) {
                credential.uploadBaseUrl
            } else {
                defaults.localBaseUrl
            }
        )
    }

    /** Records the one-way migration boundary, then retries plaintext cleanup until it sticks. */
    private fun recordMigrationCompleteAndCleanup(
        prefs: SharedPreferences,
        key: (String) -> String
    ) {
        val cleanupNeeded = hasLegacyUploadPreferences(prefs, key) ||
            prefs.getBoolean(key(KEY_UPLOAD_LEGACY_CLEANUP_PENDING), false)
        val beforeCleanup = LegacyUploadMigrationPolicy.markerAfterCleanupAttempt(
            cleanupNeeded = cleanupNeeded,
            cleanupSucceeded = !cleanupNeeded
        )
        prefs.edit()
            .putBoolean(key(KEY_UPLOAD_SECURE_MIGRATION_COMPLETE), beforeCleanup.migrationComplete)
            .putBoolean(key(KEY_UPLOAD_LEGACY_CLEANUP_PENDING), beforeCleanup.cleanupPending)
            .commit()
        if (!cleanupNeeded) return

        val cleaned = prefs.edit().apply {
            remove(key(KEY_UPLOAD_SERVER_MODE))
            remove(key(KEY_UPLOAD_PRODUCTION_URL))
            remove(key(KEY_UPLOAD_LOCAL_URL))
            remove(key(KEY_UPLOAD_API_KEY))
            remove(key(KEY_UPLOAD_AUTO_ENABLED))
            putBoolean(key(KEY_UPLOAD_SECURE_MIGRATION_COMPLETE), true)
            putBoolean(key(KEY_UPLOAD_LEGACY_CLEANUP_PENDING), false)
        }.commit()
        if (!cleaned) {
            // Best-effort recording keeps cleanup retryable after a transient preferences failure.
            val retryMarker = LegacyUploadMigrationPolicy.markerAfterCleanupAttempt(
                cleanupNeeded = true,
                cleanupSucceeded = false
            )
            prefs.edit()
                .putBoolean(key(KEY_UPLOAD_SECURE_MIGRATION_COMPLETE), retryMarker.migrationComplete)
                .putBoolean(key(KEY_UPLOAD_LEGACY_CLEANUP_PENDING), retryMarker.cleanupPending)
                .commit()
        }
    }

    private fun hasLegacyUploadPreferences(
        prefs: SharedPreferences,
        key: (String) -> String
    ): Boolean =
        prefs.contains(key(KEY_UPLOAD_SERVER_MODE)) ||
            prefs.contains(key(KEY_UPLOAD_PRODUCTION_URL)) ||
            prefs.contains(key(KEY_UPLOAD_LOCAL_URL)) ||
            prefs.contains(key(KEY_UPLOAD_API_KEY)) ||
            prefs.contains(key(KEY_UPLOAD_AUTO_ENABLED))

    fun uploadStatus(
        context: Context,
        profileId: String = LocalProfileStore.activeProfile(context).id
    ): UploadStatus {
        val prefs = prefs(context)
        val profile = requireProfile(context, profileId)
        val key = uploadPreferenceKey(profile)

        val severity = prefs.getString(key(KEY_UPLOAD_LAST_SEVERITY), UploadResultSeverity.IDLE.name)
            ?.let { raw -> UploadResultSeverity.values().firstOrNull { it.name == raw } }
            ?: UploadResultSeverity.IDLE
        val mode = prefs.getString(key(KEY_UPLOAD_STATUS_SERVER_MODE), UploadServerMode.PRODUCTION.name)
            ?.let { raw -> UploadServerMode.values().firstOrNull { it.name == raw } }
            ?: UploadServerMode.PRODUCTION
        return UploadStatus(
            lastUploadEpochMillis = prefs.getLong(key(KEY_UPLOAD_LAST_TIME), 0L).takeIf { it > 0L },
            lastResult = prefs.getString(key(KEY_UPLOAD_LAST_RESULT), "No upload yet") ?: "No upload yet",
            severity = severity,
            pendingCount = prefs.getInt(key(KEY_UPLOAD_PENDING_COUNT), 0),
            serverMode = mode,
            connectionResult = prefs.getString(key(KEY_UPLOAD_CONNECTION_RESULT), null)
        )
    }

    fun setUploadStatus(
        context: Context,
        status: UploadStatus,
        profileId: String = LocalProfileStore.activeProfile(context).id
    ) {
        val profile = requireProfile(context, profileId)
        val key = uploadPreferenceKey(profile)

        prefs(context).edit().apply {
            if (status.lastUploadEpochMillis == null) {
                remove(key(KEY_UPLOAD_LAST_TIME))
            } else {
                putLong(key(KEY_UPLOAD_LAST_TIME), status.lastUploadEpochMillis)
            }
            putString(key(KEY_UPLOAD_LAST_RESULT), status.lastResult)
            putString(key(KEY_UPLOAD_LAST_SEVERITY), status.severity.name)
            putInt(key(KEY_UPLOAD_PENDING_COUNT), status.pendingCount)
            putString(key(KEY_UPLOAD_STATUS_SERVER_MODE), status.serverMode.name)
            if (status.connectionResult == null) {
                remove(key(KEY_UPLOAD_CONNECTION_RESULT))
            } else {
                putString(key(KEY_UPLOAD_CONNECTION_RESULT), status.connectionResult)
            }
        }.apply()
    }

    private fun uploadDeviceId(context: Context, storageKey: String): String {
        val prefs = prefs(context)
        val existing = prefs.getString(storageKey, null)
            ?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(storageKey, generated).apply()
        return generated
    }

    private fun requireProfile(context: Context, profileId: String): LocalProfile =
        requireNotNull(LocalProfileStore.profile(context, profileId)) {
            "Unknown local profile: $profileId"
        }

    private fun uploadPreferenceKey(profile: LocalProfile): (String) -> String = { baseKey ->
        scopedUploadPreferenceKey(baseKey, profile.id, profile.ownsHealthConnect)
    }

    // The original Health Connect owner keeps the pre-profile alias so existing encrypted
    // settings remain readable. Secondary profiles receive isolated encrypted payloads.
    private fun secureUploadScope(profile: LocalProfile): String? =
        profile.id.takeUnless { profile.ownsHealthConnect }

    internal fun scopedUploadPreferenceKey(
        baseKey: String,
        profileId: String,
        ownsHealthConnect: Boolean
    ): String = if (ownsHealthConnect) baseKey else profilePreferenceKey(baseKey, profileId)

    private fun profilePreferenceKey(key: String, profileId: String): String =
        "$key:$profileId"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
