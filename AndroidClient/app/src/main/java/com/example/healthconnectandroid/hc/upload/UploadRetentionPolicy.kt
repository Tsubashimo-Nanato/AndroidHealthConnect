package com.example.healthconnectandroid.hc.upload

internal object UploadRetentionPolicy {
    fun productionServerKey(
        settings: UploadSettings,
        uploadSucceeded: Boolean,
        profileOwnsHealthConnect: Boolean
    ): String? {
        if (!uploadSucceeded || !profileOwnsHealthConnect) return null
        if (settings.serverMode != UploadServerMode.PRODUCTION) return null
        val endpoint = (UploadEndpointPolicy.validate(settings) as? UploadEndpointValidation.Valid)
            ?.endpoint
            ?: return null
        if (endpoint.mode != UploadServerMode.PRODUCTION) return null
        return endpoint.serverKey
    }
}
