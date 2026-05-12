package com.example.healthconnectandroid.ui.data

import com.example.healthconnectandroid.hc.HealthDataPermissionStatus
import com.example.healthconnectandroid.hc.HealthDataTypeKeys
import com.example.healthconnectandroid.hc.HealthDataTypeRegistry
import com.example.healthconnectandroid.hc.InspectorCategorySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataCardInteractionPolicyTest {
    @Test
    fun implementedGrantedCardsAreClickableEvenWithZeroRows() {
        val summary = summary(
            key = HealthDataTypeKeys.HEART_RATE,
            permissionStatus = HealthDataPermissionStatus.GRANTED,
            recordCount = 0
        )

        assertTrue(DataCardInteractionPolicy.isClickable(summary))
    }

    @Test
    fun implementedGrantedCardsWithOlderLocalRowsAreActiveEvenWithoutRecentRows() {
        val summary = summary(
            key = HealthDataTypeKeys.HEART_RATE,
            permissionStatus = HealthDataPermissionStatus.GRANTED,
            recordCount = 12,
            recentRecordCount = 0
        )

        assertTrue(DataCardInteractionPolicy.isClickable(summary))
        assertFalse(DataCardInteractionPolicy.isLowPriority(summary))
        assertEquals(null, DataCardInteractionPolicy.disabledReason(summary))
    }

    @Test
    fun missingPermissionCardsAreDisabledAndLowerPriority() {
        val summary = summary(
            key = HealthDataTypeKeys.HEART_RATE,
            permissionStatus = HealthDataPermissionStatus.MISSING,
            recordCount = 12
        )

        assertFalse(DataCardInteractionPolicy.isClickable(summary))
        assertTrue(DataCardInteractionPolicy.isLowPriority(summary))
        assertEquals("Needs access", DataCardInteractionPolicy.disabledReason(summary))
    }

    @Test
    fun plannedCardsAreDisabled() {
        val summary = summary(
            key = HealthDataTypeKeys.NUTRITION,
            permissionStatus = HealthDataPermissionStatus.NOT_IMPLEMENTED,
            recordCount = 0
        )

        assertFalse(DataCardInteractionPolicy.isClickable(summary))
        assertTrue(DataCardInteractionPolicy.isLowPriority(summary))
        assertEquals("Planned", DataCardInteractionPolicy.disabledReason(summary))
    }

    private fun summary(
        key: String,
        permissionStatus: HealthDataPermissionStatus,
        recordCount: Int,
        recentRecordCount: Int = recordCount
    ): InspectorCategorySummary {
        val descriptor = HealthDataTypeRegistry.require(key)
        return InspectorCategorySummary(
            descriptor = descriptor,
            permissionStatus = permissionStatus,
            requiredPermission = descriptor.requiredReadPermission,
            permissionGranted = permissionStatus == HealthDataPermissionStatus.GRANTED,
            recordCount = recordCount,
            recentRecordCount = recentRecordCount,
            lastSynced = null,
            lastSyncStatus = null,
            lastSyncError = null,
            latestRecordTime = null,
            summaryText = "",
            latestReadable = null,
            todayTotal = null
        )
    }
}
