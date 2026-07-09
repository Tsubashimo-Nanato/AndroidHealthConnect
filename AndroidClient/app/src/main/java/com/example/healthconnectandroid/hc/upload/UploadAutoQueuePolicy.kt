package com.example.healthconnectandroid.hc.upload

sealed interface UploadAutoQueueDecision {
    data object Disabled : UploadAutoQueueDecision
    data class Queue(val endpoint: UploadEndpoint) : UploadAutoQueueDecision
    data class Invalid(val reason: String) : UploadAutoQueueDecision
}

object UploadAutoQueuePolicy {
    fun decide(settings: UploadSettings): UploadAutoQueueDecision {
        if (!settings.autoUploadEnabled) return UploadAutoQueueDecision.Disabled

        return when (val validation = UploadEndpointPolicy.validate(settings)) {
            is UploadEndpointValidation.Valid -> UploadAutoQueueDecision.Queue(validation.endpoint)
            is UploadEndpointValidation.Invalid -> UploadAutoQueueDecision.Invalid(validation.reason)
        }
    }
}
