package com.example.healthconnectandroid.medicine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineExactAlarmPolicyTest {
    @Test
    fun olderAndroidVersionsDoNotNeedSpecialAccess() {
        assertTrue(
            MedicineExactAlarmPolicy.canScheduleExactAlarm(
                sdkInt = MedicineExactAlarmPolicy.EXACT_ALARM_ACCESS_SDK - 1,
                specialAccessGranted = false
            )
        )
    }

    @Test
    fun currentAndroidVersionsRequireSpecialAccess() {
        assertFalse(
            MedicineExactAlarmPolicy.canScheduleExactAlarm(
                sdkInt = MedicineExactAlarmPolicy.EXACT_ALARM_ACCESS_SDK,
                specialAccessGranted = false
            )
        )
    }

    @Test
    fun deniedAlarmModeFallsBackToWorkManager() {
        assertEquals(
            MedicineReminderScheduleMode.Work,
            MedicineExactAlarmPolicy.resolveMode(
                requestedMode = MedicineReminderScheduleMode.Alarm,
                exactAlarmAccessGranted = false
            )
        )
    }

    @Test
    fun grantedAlarmModeKeepsAlarmManager() {
        assertEquals(
            MedicineReminderScheduleMode.Alarm,
            MedicineExactAlarmPolicy.resolveMode(
                requestedMode = MedicineReminderScheduleMode.Alarm,
                exactAlarmAccessGranted = true
            )
        )
    }
}
