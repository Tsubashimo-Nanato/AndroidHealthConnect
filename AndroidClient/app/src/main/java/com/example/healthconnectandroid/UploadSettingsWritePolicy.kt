package com.example.healthconnectandroid

import com.example.healthconnectandroid.hc.upload.UploadEndpointPolicy

internal enum class UploadSettingsWriteIntent {
    CONFIGURATION,
    REQUIRES_EXISTING_CONFIGURATION
}

internal object UploadSettingsWritePolicy {
    fun canEdit(storageReady: Boolean, reentryRequired: Boolean): Boolean =
        storageReady || reentryRequired

    fun canWrite(
        storageReady: Boolean,
        reentryRequired: Boolean,
        apiKey: String,
        intent: UploadSettingsWriteIntent
    ): Boolean {
        val keyLength = apiKey.trim().length
        if (keyLength !in UploadEndpointPolicy.MINIMUM_API_KEY_LENGTH..
            UploadEndpointPolicy.MAXIMUM_API_KEY_LENGTH
        ) return false
        if (storageReady) return true
        if (!reentryRequired) return false
        return intent == UploadSettingsWriteIntent.CONFIGURATION
    }
}
