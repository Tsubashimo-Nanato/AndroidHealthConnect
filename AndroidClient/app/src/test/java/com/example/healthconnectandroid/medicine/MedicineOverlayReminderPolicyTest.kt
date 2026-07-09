package com.example.healthconnectandroid.medicine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineOverlayReminderPolicyTest {
    @Test
    fun overlayRequiresUserSwitchPermissionAndMedicine() {
        assertTrue(
            MedicineOverlayReminderPolicy.shouldShowOverlay(
                enabled = true,
                overlayPermissionGranted = true,
                medicineCount = 1
            )
        )
    }

    @Test
    fun overlayStaysOffWhenSwitchIsOff() {
        assertFalse(
            MedicineOverlayReminderPolicy.shouldShowOverlay(
                enabled = false,
                overlayPermissionGranted = true,
                medicineCount = 1
            )
        )
    }

    @Test
    fun overlayStaysOffWithoutPermission() {
        assertFalse(
            MedicineOverlayReminderPolicy.shouldShowOverlay(
                enabled = true,
                overlayPermissionGranted = false,
                medicineCount = 1
            )
        )
    }

    @Test
    fun overlayStaysOffWhenThereIsNothingToConfirm() {
        assertFalse(
            MedicineOverlayReminderPolicy.shouldShowOverlay(
                enabled = true,
                overlayPermissionGranted = true,
                medicineCount = 0
            )
        )
    }

    @Test
    fun fullScreenFallbackUsesTheSameStrongReminderSwitch() {
        assertTrue(
            MedicineOverlayReminderPolicy.shouldUseFullScreenFallback(
                enabled = true,
                medicineCount = 1
            )
        )
        assertFalse(
            MedicineOverlayReminderPolicy.shouldUseFullScreenFallback(
                enabled = false,
                medicineCount = 1
            )
        )
        assertFalse(
            MedicineOverlayReminderPolicy.shouldUseFullScreenFallback(
                enabled = true,
                medicineCount = 0
            )
        )
    }
}
