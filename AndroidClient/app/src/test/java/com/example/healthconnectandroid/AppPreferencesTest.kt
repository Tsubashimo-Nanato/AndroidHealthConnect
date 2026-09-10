package com.example.healthconnectandroid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppPreferencesTest {
    @Test
    fun healthConnectOwnerKeepsLegacyUploadPreferenceKey() {
        assertEquals(
            "upload_api_key",
            AppPreferences.scopedUploadPreferenceKey(
                baseKey = "upload_api_key",
                profileId = "owner",
                ownsHealthConnect = true
            )
        )
    }

    @Test
    fun secondaryProfilesUseIndependentUploadPreferenceKeys() {
        val first = AppPreferences.scopedUploadPreferenceKey(
            baseKey = "upload_api_key",
            profileId = "profile-a",
            ownsHealthConnect = false
        )
        val second = AppPreferences.scopedUploadPreferenceKey(
            baseKey = "upload_api_key",
            profileId = "profile-b",
            ownsHealthConnect = false
        )

        assertEquals("upload_api_key:profile-a", first)
        assertNotEquals(first, second)
    }
}
