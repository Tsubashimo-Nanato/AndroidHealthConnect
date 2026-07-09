package com.example.healthconnectandroid.medicine

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.AppPreferences
import com.example.healthconnectandroid.data.AppDb
import com.example.healthconnectandroid.ui.medicine.translateMedicineUiText
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object MedicineReminderOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentRoot: View? = null
    private var currentWindowManager: WindowManager? = null

    fun canShow(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show(
        context: Context,
        slot: MedicineSlot,
        medicines: List<MedicineItem>
    ): Boolean {
        val appContext = context.applicationContext
        if (!canShow(appContext) || medicines.isEmpty()) return false

        val language = AppPreferences.userPreferences(appContext).language
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return showOnMain(appContext, slot, medicines, language)
        }

        val latch = CountDownLatch(1)
        var shown = false
        mainHandler.post {
            shown = showOnMain(appContext, slot, medicines, language)
            latch.countDown()
        }
        return latch.await(2, TimeUnit.SECONDS) && shown
    }

    private fun showOnMain(
        context: Context,
        slot: MedicineSlot,
        medicines: List<MedicineItem>,
        language: AppLanguagePreference
    ): Boolean {
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
        val selectedIds = MedicineReminderPromptPolicy.defaultSelectedIds(medicines).toMutableSet()

        return runCatching {
            dismissOnMain()
            val root = buildOverlayView(
                context = context,
                slot = slot,
                medicines = medicines,
                selectedIds = selectedIds,
                language = language
            )
            windowManager.addView(root, overlayParams())
            currentRoot = root
            currentWindowManager = windowManager
            true
        }.onFailure { throwable ->
            Log.e(TAG, "Medicine overlay show failed slot=${slot.id}", throwable)
        }.getOrDefault(false)
    }

    private fun buildOverlayView(
        context: Context,
        slot: MedicineSlot,
        medicines: List<MedicineItem>,
        selectedIds: MutableSet<Long>,
        language: AppLanguagePreference
    ): View {
        val root = FrameLayout(context).apply {
            setBackgroundColor(ALERT_BACKGROUND)
            isClickable = true
            isFocusable = true
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 28), dp(context, 64), dp(context, 28), dp(context, 30))
            background = GradientDrawable().apply {
                setColor(ALERT_BACKGROUND)
            }
        }
        root.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
        )

        val title = TextView(context).apply {
            text = translateMedicineUiText("Medicine check", language)
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(ALERT_TEXT)
        }
        val slotText = TextView(context).apply {
            text = slot.displayLabel(language)
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(ALERT_TEXT)
        }
        val selectedText = TextView(context).apply {
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(ALERT_SECONDARY_TEXT)
        }
        val takenButton = Button(context).apply {
            text = translateMedicineUiText("Taken", language)
        }
        fun updateTakenState() {
            takenButton.isEnabled = MedicineReminderPromptPolicy.canConfirmTaken(selectedIds)
            selectedText.text = selectedSummary(selectedIds, medicines, language)
        }
        val listContainer = medicineCheckList(
            context = context,
            medicines = medicines,
            selectedIds = selectedIds,
            language = language,
            onSelectionChange = ::updateTakenState
        ).apply {
            visibility = View.GONE
        }
        val collapsedSpacer = View(context)

        val reviewButton = Button(context).apply {
            text = translateMedicineUiText("Review medicines", language)
            setOnClickListener {
                val expanding = listContainer.visibility != View.VISIBLE
                listContainer.visibility = if (expanding) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                collapsedSpacer.visibility = if (expanding) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            }
        }
        val cancelButton = Button(context).apply {
            text = translateMedicineUiText("Cancel", language)
            setOnClickListener {
                dismiss()
                MedicineReminderNotifier.cancel(context, slot)
            }
        }
        updateTakenState()
        takenButton.setOnClickListener {
            if (!MedicineReminderPromptPolicy.canConfirmTaken(selectedIds)) {
                Toast.makeText(
                    context,
                    translateMedicineUiText("Select at least one medicine", language),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            recordTakenAndClose(
                context = context,
                slot = slot,
                selectedIds = selectedIds.toSet(),
                language = language,
                takenButton = takenButton,
                cancelButton = cancelButton
            )
        }

        panel.addView(title)
        panel.addView(slotText, topMarginParams(context, 18))
        panel.addView(selectedText, topMarginParams(context, 10))
        panel.addView(reviewButton, topMarginParams(context, 24))
        panel.addView(
            listContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(context, 14)
            }
        )
        panel.addView(
            collapsedSpacer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        panel.addView(buttonRow(context, cancelButton, takenButton), topMarginParams(context, 18))
        return root
    }

    private fun medicineCheckList(
        context: Context,
        medicines: List<MedicineItem>,
        selectedIds: MutableSet<Long>,
        language: AppLanguagePreference,
        onSelectionChange: () -> Unit
    ): ScrollView {
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        medicines.forEach { medicine ->
            val localId = medicine.localId
            val checkbox = CheckBox(context).apply {
                text = medicine.displayText(language).name
                textSize = 15f
                setTextColor(ALERT_TEXT)
                isEnabled = localId > 0L
                isChecked = localId in selectedIds
                setOnCheckedChangeListener { _, checked ->
                    if (localId <= 0L) return@setOnCheckedChangeListener
                    if (checked) selectedIds += localId else selectedIds -= localId
                    onSelectionChange()
                }
            }
            list.addView(checkbox)
        }
        return ScrollView(context).apply {
            addView(list)
        }
    }

    private fun recordTakenAndClose(
        context: Context,
        slot: MedicineSlot,
        selectedIds: Set<Long>,
        language: AppLanguagePreference,
        takenButton: Button,
        cancelButton: Button
    ) {
        takenButton.isEnabled = false
        cancelButton.isEnabled = false
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val repository = MedicineRepository(AppDb.get(context))
                    repository.logReminderResponse(
                        slot = slot,
                        status = MedicineDoseStatus.TAKEN,
                        zoneId = ZoneId.systemDefault(),
                        medicineLocalIds = selectedIds
                    )
                    MedicineReminderScheduler.scheduleSlot(
                        context = context,
                        repository = repository,
                        slot = slot,
                        zoneId = ZoneId.systemDefault()
                    )
                }
            }.onSuccess {
                MedicineReminderNotifier.cancel(context, slot)
                dismiss()
            }.onFailure { throwable ->
                val message = "Medicine check failed: ${throwable.message ?: throwable.javaClass.simpleName}"
                Log.e(TAG, "Medicine overlay confirm failed slot=${slot.id}", throwable)
                Toast.makeText(
                    context,
                    translateMedicineUiText(message, language),
                    Toast.LENGTH_LONG
                ).show()
                takenButton.isEnabled = true
                cancelButton.isEnabled = true
            }
        }
    }

    private fun buttonRow(context: Context, cancelButton: Button, takenButton: Button): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(
                cancelButton,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                takenButton,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(context, 10) }
            )
        }

    private fun selectedSummary(
        selectedIds: Set<Long>,
        medicines: List<MedicineItem>,
        language: AppLanguagePreference
    ): String =
        translateMedicineUiText("Selected medicines: ${selectedIds.size}/${medicines.size}", language)

    fun dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissOnMain()
        } else {
            mainHandler.post { dismissOnMain() }
        }
    }

    private fun dismissOnMain() {
        val root = currentRoot ?: return
        runCatching {
            currentWindowManager?.removeView(root)
        }.onFailure { throwable ->
            Log.w(TAG, "Medicine overlay dismiss failed", throwable)
        }
        currentRoot = null
        currentWindowManager = null
    }

    private fun overlayParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

    private fun topMarginParams(context: Context, top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(context, top)
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private const val TAG = "MedicineReminder"
    private val ALERT_BACKGROUND = Color.rgb(120, 32, 38)
    private val ALERT_TEXT = Color.WHITE
    private val ALERT_SECONDARY_TEXT = Color.rgb(255, 226, 222)
}
