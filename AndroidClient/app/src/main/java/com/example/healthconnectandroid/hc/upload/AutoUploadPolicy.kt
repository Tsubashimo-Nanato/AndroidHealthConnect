package com.example.healthconnectandroid.hc.upload

import com.example.healthconnectandroid.hc.HealthDataTypeSyncResult
import com.example.healthconnectandroid.hc.sync.SyncMode
import com.example.healthconnectandroid.hc.sync.SyncResultSeverity
import com.example.healthconnectandroid.hc.sync.SyncResultSeverityPolicy

object AutoUploadPolicy {
    fun shouldQueueAfterSync(
        enabled: Boolean,
        mode: SyncMode,
        results: List<HealthDataTypeSyncResult>,
        settings: UploadSettings
    ): Boolean {
        if (!enabled) return false
        if (mode != SyncMode.SMART && mode != SyncMode.PERIODIC) return false
        if (UploadEndpointPolicy.validate(settings) !is UploadEndpointValidation.Valid) return false
        return SyncResultSeverityPolicy.fromResults(results) in setOf(
            SyncResultSeverity.SUCCESS,
            SyncResultSeverity.WARNING
        )
    }
}
