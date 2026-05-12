package com.example.healthconnectandroid

import android.content.Context
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.DayOfWeek

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
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

data class UserPreferences(
    val weekStart: WeekStartPreference = WeekStartPreference.SUNDAY,
    val unitSystem: UnitSystemPreference = UnitSystemPreference.METRIC,
    val timeZoneMode: TimeZonePreferenceMode = TimeZonePreferenceMode.SYSTEM,
    val customTimeZoneId: String? = null
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
    private const val KEY_USER_AGE = "user_age"
    private const val KEY_USER_DOB = "user_date_of_birth"
    private const val KEY_USER_SEX = "user_sex"
    private const val KEY_USER_WEIGHT_KG = "user_weight_kg"
    private const val KEY_WEEK_START = "week_start"
    private const val KEY_UNIT_SYSTEM = "unit_system"
    private const val KEY_TIMEZONE_MODE = "timezone_mode"
    private const val KEY_CUSTOM_TIMEZONE = "custom_timezone"

    fun themeMode(context: Context): AppThemeMode {
        val raw = prefs(context).getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return AppThemeMode.values().firstOrNull { it.name == raw } ?: AppThemeMode.SYSTEM
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        prefs(context).edit().putString(KEY_THEME_MODE, mode.name).apply()
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
        return UserPreferences(
            weekStart = weekStart,
            unitSystem = unitSystem,
            timeZoneMode = timeZoneMode,
            customTimeZoneId = customTimeZone
        )
    }

    fun setUserPreferences(context: Context, preferences: UserPreferences) {
        prefs(context).edit().apply {
            putString(KEY_WEEK_START, preferences.weekStart.name)
            putString(KEY_UNIT_SYSTEM, preferences.unitSystem.name)
            putString(KEY_TIMEZONE_MODE, preferences.timeZoneMode.name)
            val normalizedZone = preferences.customTimeZoneId
                ?.takeIf { runCatching { ZoneId.of(it) }.isSuccess }
            if (normalizedZone == null) remove(KEY_CUSTOM_TIMEZONE) else putString(KEY_CUSTOM_TIMEZONE, normalizedZone)
        }.apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
