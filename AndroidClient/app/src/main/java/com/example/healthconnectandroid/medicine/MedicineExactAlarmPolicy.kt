package com.example.healthconnectandroid.medicine

object MedicineExactAlarmPolicy {
    const val EXACT_ALARM_ACCESS_SDK = 31

    fun canScheduleExactAlarm(sdkInt: Int, specialAccessGranted: Boolean): Boolean =
        sdkInt < EXACT_ALARM_ACCESS_SDK || specialAccessGranted

    fun resolveMode(
        requestedMode: MedicineReminderScheduleMode,
        exactAlarmAccessGranted: Boolean
    ): MedicineReminderScheduleMode =
        if (requestedMode == MedicineReminderScheduleMode.Alarm && !exactAlarmAccessGranted) {
            MedicineReminderScheduleMode.Work
        } else {
            requestedMode
        }
}
