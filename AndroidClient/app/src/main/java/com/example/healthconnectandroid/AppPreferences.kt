package com.example.healthconnectandroid

import android.content.Context
import com.example.healthconnectandroid.hc.upload.UploadEndpointPolicy
import com.example.healthconnectandroid.hc.upload.UploadResultSeverity
import com.example.healthconnectandroid.hc.upload.UploadServerMode
import com.example.healthconnectandroid.hc.upload.UploadSettings
import com.example.healthconnectandroid.hc.upload.UploadStatus
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
    private const val KEY_UPLOAD_LAST_TIME = "upload_last_time"
    private const val KEY_UPLOAD_LAST_RESULT = "upload_last_result"
    private const val KEY_UPLOAD_LAST_SEVERITY = "upload_last_severity"
    private const val KEY_UPLOAD_PENDING_COUNT = "upload_pending_count"
    private const val KEY_UPLOAD_STATUS_SERVER_MODE = "upload_status_server_mode"
    private const val KEY_UPLOAD_CONNECTION_RESULT = "upload_connection_result"
    private const val KEY_MEDICINE_OVERLAY_REMINDER_ENABLED = "medicine_overlay_reminder_enabled"

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

    fun userAge(context: Context): Int? {
        return userProfile(context).age
    }

    fun setUserAge(context: Context, age: Int?) {
        val current = userProfile(context)
        // Age is now derived from DOB. Keep this compatibility method as a no-op unless callers pass null.
        if (age == null) setUserProfile(context, current.copy(dateOfBirthIso = null))
    }

    fun userProfile(context: Context): UserProfile {
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
        prefs(context).edit().apply {
            putString(KEY_USER_SEX, profile.sex.name)

            val normalizedDob = profile.dateOfBirthIso
                ?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
            if (normalizedDob == null) remove(KEY_USER_DOB) else putString(KEY_USER_DOB, normalizedDob)
            remove(KEY_USER_AGE)

            val normalizedWeight = profile.weightKg?.takeIf { it in 20.0..350.0 }
            if (normalizedWeight == null) remove(KEY_USER_WEIGHT_KG) else putFloat(KEY_USER_WEIGHT_KG, normalizedWeight.toFloat())
        }.apply()
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

    fun uploadSettings(context: Context): UploadSettings {
        val prefs = prefs(context)
        val mode = prefs.getString(KEY_UPLOAD_SERVER_MODE, UploadServerMode.PRODUCTION.name)
            ?.let { raw -> UploadServerMode.values().firstOrNull { it.name == raw } }
            ?: UploadServerMode.PRODUCTION
        val localUrl = prefs.getString(KEY_UPLOAD_LOCAL_URL, UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL)
            ?: UploadEndpointPolicy.DEFAULT_LOCAL_BASE_URL
        val productionUrl = prefs.getString(KEY_UPLOAD_PRODUCTION_URL, UploadEndpointPolicy.PRODUCTION_BASE_URL)
            ?: UploadEndpointPolicy.PRODUCTION_BASE_URL
        val apiKey = prefs.getString(KEY_UPLOAD_API_KEY, "") ?: ""
        return UploadSettings(
            serverMode = mode,
            productionBaseUrl = productionUrl,
            localBaseUrl = localUrl,
            apiKey = apiKey,
            deviceId = uploadDeviceId(context),
            autoUploadEnabled = prefs.getBoolean(KEY_UPLOAD_AUTO_ENABLED, false)
        )
    }

    fun setUploadSettings(context: Context, settings: UploadSettings) {
        prefs(context).edit().apply {
            putString(KEY_UPLOAD_SERVER_MODE, settings.serverMode.name)
            putString(KEY_UPLOAD_PRODUCTION_URL, settings.productionBaseUrl.trim())
            putString(KEY_UPLOAD_LOCAL_URL, settings.localBaseUrl.trim())
            putString(KEY_UPLOAD_API_KEY, settings.apiKey.trim())
            putString(KEY_UPLOAD_DEVICE_ID, settings.deviceId)
            putBoolean(KEY_UPLOAD_AUTO_ENABLED, settings.autoUploadEnabled)
        }.apply()
    }

    fun uploadStatus(context: Context): UploadStatus {
        val prefs = prefs(context)
        val severity = prefs.getString(KEY_UPLOAD_LAST_SEVERITY, UploadResultSeverity.IDLE.name)
            ?.let { raw -> UploadResultSeverity.values().firstOrNull { it.name == raw } }
            ?: UploadResultSeverity.IDLE
        val mode = prefs.getString(KEY_UPLOAD_STATUS_SERVER_MODE, UploadServerMode.PRODUCTION.name)
            ?.let { raw -> UploadServerMode.values().firstOrNull { it.name == raw } }
            ?: UploadServerMode.PRODUCTION
        return UploadStatus(
            lastUploadEpochMillis = prefs.getLong(KEY_UPLOAD_LAST_TIME, 0L).takeIf { it > 0L },
            lastResult = prefs.getString(KEY_UPLOAD_LAST_RESULT, "No upload yet") ?: "No upload yet",
            severity = severity,
            pendingCount = prefs.getInt(KEY_UPLOAD_PENDING_COUNT, 0),
            serverMode = mode,
            connectionResult = prefs.getString(KEY_UPLOAD_CONNECTION_RESULT, null)
        )
    }

    fun setUploadStatus(context: Context, status: UploadStatus) {
        prefs(context).edit().apply {
            if (status.lastUploadEpochMillis == null) {
                remove(KEY_UPLOAD_LAST_TIME)
            } else {
                putLong(KEY_UPLOAD_LAST_TIME, status.lastUploadEpochMillis)
            }
            putString(KEY_UPLOAD_LAST_RESULT, status.lastResult)
            putString(KEY_UPLOAD_LAST_SEVERITY, status.severity.name)
            putInt(KEY_UPLOAD_PENDING_COUNT, status.pendingCount)
            putString(KEY_UPLOAD_STATUS_SERVER_MODE, status.serverMode.name)
            if (status.connectionResult == null) {
                remove(KEY_UPLOAD_CONNECTION_RESULT)
            } else {
                putString(KEY_UPLOAD_CONNECTION_RESULT, status.connectionResult)
            }
        }.apply()
    }

    private fun uploadDeviceId(context: Context): String {
        val prefs = prefs(context)
        val existing = prefs.getString(KEY_UPLOAD_DEVICE_ID, null)
            ?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_UPLOAD_DEVICE_ID, generated).apply()
        return generated
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
