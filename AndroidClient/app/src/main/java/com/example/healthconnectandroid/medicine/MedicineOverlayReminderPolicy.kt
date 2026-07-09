package com.example.healthconnectandroid.medicine

object MedicineOverlayReminderPolicy {
    fun shouldShowOverlay(
        enabled: Boolean,
        overlayPermissionGranted: Boolean,
        medicineCount: Int
    ): Boolean =
        enabled && overlayPermissionGranted && medicineCount > 0

    fun shouldUseFullScreenFallback(
        enabled: Boolean,
        medicineCount: Int
    ): Boolean =
        enabled && medicineCount > 0
}
