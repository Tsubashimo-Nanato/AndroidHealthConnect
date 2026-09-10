package com.example.healthconnectandroid

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

data class LocalProfile(
    val id: String,
    val displayName: String,
    val databaseName: String,
    val ownsHealthConnect: Boolean,
    val userProfile: UserProfile,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long
)

sealed interface CreateProfileResult {
    data class Created(val profile: LocalProfile) : CreateProfileResult
    data class Rejected(val message: String) : CreateProfileResult
}

object LocalProfileStore {
    private const val PREFS_NAME = "health_connect_local_profiles"
    private const val KEY_INITIALIZED = "initialized"
    private const val KEY_ACTIVE_ID = "active_profile_id"
    private const val KEY_PROFILE_IDS = "profile_ids"
    private const val LEGACY_DATABASE_NAME = "hc_demo.db"
    private const val DEFAULT_DISPLAY_NAME = "My profile"
    private const val MAX_DISPLAY_NAME_LENGTH = 40
    private val lock = Any()

    fun profiles(context: Context): List<LocalProfile> = synchronized(lock) {
        val prefs = initializedPrefs(context)
        profileIds(prefs)
            .mapNotNull { readProfile(prefs, it) }
            .sortedBy(LocalProfile::createdAtEpochMillis)
    }

    fun activeProfile(context: Context): LocalProfile = synchronized(lock) {
        val prefs = initializedPrefs(context)
        val profiles = profileIds(prefs).mapNotNull { readProfile(prefs, it) }
        check(profiles.isNotEmpty()) { "Local profile registry contains no profiles" }
        val activeId = prefs.getString(KEY_ACTIVE_ID, null)
        profiles.firstOrNull { it.id == activeId } ?: profiles.minBy(LocalProfile::createdAtEpochMillis)
    }

    fun profile(context: Context, profileId: String): LocalProfile? = synchronized(lock) {
        readProfile(initializedPrefs(context), profileId)
    }

    fun healthConnectProfile(context: Context): LocalProfile = synchronized(lock) {
        val owners = profiles(context).filter(LocalProfile::ownsHealthConnect)
        check(owners.size == 1) {
            "Local profile registry must contain exactly one Health Connect owner"
        }
        owners.single()
    }

    fun create(context: Context, rawDisplayName: String): CreateProfileResult = synchronized(lock) {
        val displayName = normalizeDisplayName(rawDisplayName)
            ?: return CreateProfileResult.Rejected("Enter a profile name.")
        val prefs = initializedPrefs(context)
        if (profileIds(prefs).mapNotNull { readProfile(prefs, it) }
                .any { it.displayName.equals(displayName, ignoreCase = true) }
        ) {
            return CreateProfileResult.Rejected("A profile with this name already exists.")
        }

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val profile = LocalProfile(
            id = id,
            displayName = displayName,
            databaseName = "hc_profile_$id.db",
            ownsHealthConnect = false,
            userProfile = UserProfile(),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val ids = profileIds(prefs) + id
        val saved = prefs.edit()
            .putStringSet(KEY_PROFILE_IDS, ids)
            .writeProfile(profile)
            .commit()
        check(saved) { "Failed to persist local profile ${profile.id}" }
        CreateProfileResult.Created(profile)
    }

    fun setActive(context: Context, profileId: String): Boolean = synchronized(lock) {
        val prefs = initializedPrefs(context)
        if (readProfile(prefs, profileId) == null) return false
        prefs.edit().putString(KEY_ACTIVE_ID, profileId).commit()
    }

    fun updateUserProfile(context: Context, userProfile: UserProfile): LocalProfile = synchronized(lock) {
        val prefs = initializedPrefs(context)
        val active = activeProfile(context)
        val updated = active.copy(
            userProfile = userProfile.normalized(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        val saved = prefs.edit().writeProfile(updated).commit()
        check(saved) { "Failed to persist local profile ${active.id}" }
        updated
    }

    fun databaseName(context: Context, profileId: String): String =
        profile(context, profileId)?.databaseName
            ?: throw IllegalArgumentException("Unknown local profile: $profileId")

    private fun initializedPrefs(context: Context): SharedPreferences {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_INITIALIZED, false)) return prefs

        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val defaultProfile = LocalProfile(
            id = id,
            displayName = DEFAULT_DISPLAY_NAME,
            databaseName = LEGACY_DATABASE_NAME,
            ownsHealthConnect = true,
            userProfile = AppPreferences.legacyUserProfile(context),
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now
        )
        val saved = prefs.edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putString(KEY_ACTIVE_ID, id)
            .putStringSet(KEY_PROFILE_IDS, setOf(id))
            .writeProfile(defaultProfile)
            .commit()
        check(saved) { "Failed to initialize local profile registry" }
        return prefs
    }

    private fun profileIds(prefs: SharedPreferences): Set<String> =
        prefs.getStringSet(KEY_PROFILE_IDS, emptySet()).orEmpty()
            .filterTo(linkedSetOf()) { runCatching { UUID.fromString(it) }.isSuccess }

    private fun readProfile(prefs: SharedPreferences, id: String): LocalProfile? {
        if (id !in profileIds(prefs)) return null
        val databaseName = prefs.getString(key(id, "database"), null)
            ?.takeIf(::isValidDatabaseName)
            ?: return null
        val displayName = normalizeDisplayName(prefs.getString(key(id, "name"), null).orEmpty())
            ?: return null
        val sex = prefs.getString(key(id, "sex"), null)
            ?.let { raw -> ProfileSex.values().firstOrNull { it.name == raw } }
            ?: ProfileSex.NOT_SET
        val dob = prefs.getString(key(id, "dob"), null)
            ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess }
        val weight = prefs.getFloat(key(id, "weight"), 0f)
            .takeIf { it in 20f..350f }
            ?.toDouble()
        return LocalProfile(
            id = id,
            displayName = displayName,
            databaseName = databaseName,
            ownsHealthConnect = prefs.getBoolean(key(id, "health_connect_owner"), false),
            userProfile = UserProfile(sex, dob, weight),
            createdAtEpochMillis = prefs.getLong(key(id, "created"), 0L),
            updatedAtEpochMillis = prefs.getLong(key(id, "updated"), 0L)
        )
    }

    private fun SharedPreferences.Editor.writeProfile(profile: LocalProfile): SharedPreferences.Editor {
        putString(key(profile.id, "name"), profile.displayName)
        putString(key(profile.id, "database"), profile.databaseName)
        putBoolean(key(profile.id, "health_connect_owner"), profile.ownsHealthConnect)
        putString(key(profile.id, "sex"), profile.userProfile.sex.name)
        profile.userProfile.dateOfBirthIso?.let {
            putString(key(profile.id, "dob"), it)
        } ?: remove(key(profile.id, "dob"))
        profile.userProfile.weightKg?.let {
            putFloat(key(profile.id, "weight"), it.toFloat())
        } ?: remove(key(profile.id, "weight"))
        putLong(key(profile.id, "created"), profile.createdAtEpochMillis)
        putLong(key(profile.id, "updated"), profile.updatedAtEpochMillis)
        return this
    }

    private fun UserProfile.normalized(): UserProfile =
        UserProfile(
            sex = sex,
            dateOfBirthIso = dateOfBirthIso
                ?.takeIf { runCatching { java.time.LocalDate.parse(it) }.isSuccess },
            weightKg = weightKg?.takeIf { it in 20.0..350.0 }
        )

    private fun normalizeDisplayName(raw: String): String? =
        raw.trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_DISPLAY_NAME_LENGTH)
            .takeIf { it.isNotBlank() }

    private fun isValidDatabaseName(name: String): Boolean =
        name == LEGACY_DATABASE_NAME ||
            Regex("""hc_profile_[0-9a-fA-F-]{36}\.db""").matches(name)

    private fun key(profileId: String, field: String): String = "profile.$profileId.$field"
}
