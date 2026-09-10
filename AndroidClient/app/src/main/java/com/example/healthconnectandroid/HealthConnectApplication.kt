package com.example.healthconnectandroid

import android.app.Application
import com.example.healthconnectandroid.medicine.MedicineReminderOverlayHost

class HealthConnectApplication : Application() {
    internal val medicineReminderOverlayHost = MedicineReminderOverlayHost()
}
