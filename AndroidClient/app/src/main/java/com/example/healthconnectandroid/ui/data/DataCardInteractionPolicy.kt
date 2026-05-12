package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.HealthDataImplementationStatus
import com.example.healthconnectandroid.hc.HealthDataPermissionStatus
import com.example.healthconnectandroid.hc.InspectorCategorySummary

object DataCardInteractionPolicy {
    fun isClickable(summary: InspectorCategorySummary): Boolean =
        summary.descriptor.implementationStatus == HealthDataImplementationStatus.IMPLEMENTED &&
            summary.permissionStatus == HealthDataPermissionStatus.GRANTED

    fun isLowPriority(summary: InspectorCategorySummary): Boolean =
        summary.descriptor.implementationStatus != HealthDataImplementationStatus.IMPLEMENTED ||
            summary.permissionStatus != HealthDataPermissionStatus.GRANTED

    fun disabledReason(summary: InspectorCategorySummary): String? =
        when {
            summary.descriptor.implementationStatus != HealthDataImplementationStatus.IMPLEMENTED -> "Planned"
            summary.permissionStatus == HealthDataPermissionStatus.MISSING -> "Needs access"
            summary.permissionStatus == HealthDataPermissionStatus.UNSUPPORTED -> "Unsupported"
            summary.permissionStatus == HealthDataPermissionStatus.NOT_IMPLEMENTED -> "Not ready"
            else -> null
        }
}
