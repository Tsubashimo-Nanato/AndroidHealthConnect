package com.example.healthconnectandroid

import com.example.healthconnectandroid.hc.upload.UploadEndpointPolicy
import com.example.healthconnectandroid.hc.upload.UploadEndpointValidation
import com.example.healthconnectandroid.hc.upload.UploadSettings

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
        settings: UploadSettings,
        intent: UploadSettingsWriteIntent
    ): Boolean {
        if (UploadEndpointPolicy.validate(settings) is UploadEndpointValidation.Invalid) return false
        if (storageReady) return true
        if (!reentryRequired) return false
        return intent == UploadSettingsWriteIntent.CONFIGURATION
    }
}
