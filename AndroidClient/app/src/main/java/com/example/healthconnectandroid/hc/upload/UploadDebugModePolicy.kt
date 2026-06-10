package com.example.healthconnectandroid.hc.upload

data class UploadDebugModeUpdate(
    val settings: UploadSettings,
    val status: UploadStatus,
    val debugEnabled: Boolean,
    val message: String
)

object UploadDebugModePolicy {
    fun applyPairingSuccess(
        currentStatus: UploadStatus,
        debugEnabled: Boolean,
        success: UploadPairingApplyResult.Success
    ): UploadDebugModeUpdate {
        val enablesLocalDebug = success.settings.serverMode == UploadServerMode.LOCAL_DEBUG && !debugEnabled
        val nextDebugEnabled = debugEnabled || enablesLocalDebug
        val message = if (enablesLocalDebug) {
            "${success.message}. Debug mode enabled."
        } else {
            success.message
        }

        return UploadDebugModeUpdate(
            settings = success.settings,
            status = currentStatus.copy(serverMode = success.settings.serverMode),
            debugEnabled = nextDebugEnabled,
            message = message
        )
    }

    fun setDebugMode(
        currentSettings: UploadSettings,
        currentStatus: UploadStatus,
        enabled: Boolean
    ): UploadDebugModeUpdate {
        val nextSettings = if (!enabled && currentSettings.serverMode == UploadServerMode.LOCAL_DEBUG) {
            currentSettings.copy(serverMode = UploadServerMode.PRODUCTION)
        } else {
            currentSettings
        }
        val nextStatus = if (!enabled && currentStatus.serverMode == UploadServerMode.LOCAL_DEBUG) {
            currentStatus.copy(serverMode = UploadServerMode.PRODUCTION)
        } else {
            currentStatus
        }

        return UploadDebugModeUpdate(
            settings = nextSettings,
            status = nextStatus,
            debugEnabled = enabled,
            message = if (enabled) "Debug mode enabled" else "Debug mode disabled"
        )
    }
}
