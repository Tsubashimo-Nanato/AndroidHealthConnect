package com.example.healthconnectandroid.medicine

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.AppPreferences
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.ui.i18n.LocalAppLanguage
import com.example.healthconnectandroid.ui.medicine.MedicineReminderPrompt
import com.example.healthconnectandroid.ui.medicine.translateMedicineUiText
import com.example.healthconnectandroid.ui.theme.HealthConnectAndroidTheme
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MedicineReminderActivity : ComponentActivity() {
    private val repository by lazy { MedicineRepository(AppDb.get(applicationContext)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val slot = reminderSlot()
        if (slot == null) {
            finish()
            return
        }

        val preferences = AppPreferences.userPreferences(this)
        val palette = AppPreferences.themePalette(this)

        setContent {
            CompositionLocalProvider(LocalAppLanguage provides preferences.language) {
                HealthConnectAndroidTheme(palette = palette) {
                    MedicineReminderPrompt(
                        slot = slot,
                        loadMedicines = { repository.activeScheduledMedicines(slot) },
                        onTaken = { selectedIds ->
                            recordTakenAndClose(
                                slot = slot,
                                selectedIds = selectedIds,
                                zoneId = preferences.zoneId,
                                language = preferences.language
                            )
                        },
                        onCancel = {
                            MedicineReminderNotifier.cancel(this, slot)
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun reminderSlot(): MedicineSlot? =
        intent.getStringExtra(MedicineReminderIntents.EXTRA_SLOT)
            ?.let(MedicineSlot::fromId)
            ?.takeIf { it.supportsReminder }

    private fun recordTakenAndClose(
        slot: MedicineSlot,
        selectedIds: Set<Long>,
        zoneId: ZoneId,
        language: AppLanguagePreference
    ) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    repository.logReminderResponse(
                        slot = slot,
                        status = MedicineDoseStatus.TAKEN,
                        zoneId = zoneId,
                        medicineLocalIds = selectedIds
                    )
                    MedicineReminderScheduler.scheduleSlot(
                        context = this@MedicineReminderActivity,
                        repository = repository,
                        slot = slot,
                        zoneId = zoneId
                    )
                }
            }.onSuccess {
                MedicineReminderNotifier.cancel(this@MedicineReminderActivity, slot)
                finish()
            }.onFailure { throwable ->
                val message = "Medicine check failed: ${throwable.message ?: throwable.javaClass.simpleName}"
                Log.e(TAG, "Medicine reminder confirm failed slot=${slot.id}", throwable)
                Toast.makeText(
                    this@MedicineReminderActivity,
                    translateMedicineUiText(message, language),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showOverLockScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            return
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    private companion object {
        const val TAG = "MedicineReminder"
    }
}
