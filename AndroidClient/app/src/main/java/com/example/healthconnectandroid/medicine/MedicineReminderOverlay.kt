package com.example.healthconnectandroid.medicine

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.healthconnectandroid.AppLanguagePreference
import com.example.healthconnectandroid.AppPreferences
import com.example.healthconnectandroid.HealthConnectApplication
import com.example.healthconnectandroid.LocalProfileStore
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

internal class MedicineReminderOverlayHost {
    var currentRoot: View? = null
    var currentScrim: View? = null
    var currentPanel: View? = null
    var currentWindowManager: WindowManager? = null
    var stopMotion: (() -> Unit)? = null
    var dismissing: Boolean = false
}

object MedicineReminderOverlay {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(
        context: Context,
        slot: MedicineSlot,
        medicines: List<MedicineItem>,
        profileId: String = LocalProfileStore.activeProfile(context).id
    ): Boolean {
        val appContext = context.applicationContext
        if (!canShow(appContext) || medicines.isEmpty()) return false
        val host = overlayHost(appContext)
        val language = AppPreferences.userPreferences(appContext).language

        if (Looper.myLooper() == Looper.getMainLooper()) {
            return showOnMain(appContext, slot, medicines, profileId, language, host)
        }

        val latch = CountDownLatch(1)
        var shown = false
        mainHandler.post {
            shown = showOnMain(appContext, slot, medicines, profileId, language, host)
            latch.countDown()
        }
        return latch.await(2, TimeUnit.SECONDS) && shown
    }

    private fun showOnMain(
        context: Context,
        slot: MedicineSlot,
        medicines: List<MedicineItem>,
        profileId: String,
        language: AppLanguagePreference,
        host: MedicineReminderOverlayHost
    ): Boolean {
        val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
        val selectedIds = MedicineReminderPromptPolicy.defaultSelectedIds(medicines).toMutableSet()

        return runCatching {
            removeImmediately(host)
            val overlay = buildOverlayView(
                context = context,
                slot = slot,
                medicines = medicines,
                selectedIds = selectedIds,
                profileId = profileId,
                language = language,
                host = host
            )
            windowManager.addView(overlay.root, overlayParams())
            host.currentRoot = overlay.root
            host.currentScrim = overlay.scrim
            host.currentPanel = overlay.panel
            host.currentWindowManager = windowManager
            host.stopMotion = overlay.motion::stop
            host.dismissing = false
            animateEntrance(context, overlay, host)
            true
        }.onFailure { throwable ->
            removeImmediately(host)
            Log.e(TAG, "Medicine overlay show failed slot=${slot.id}", throwable)
        }.getOrDefault(false)
    }

    private fun buildOverlayView(
        context: Context,
        slot: MedicineSlot,
        medicines: List<MedicineItem>,
        selectedIds: MutableSet<Long>,
        profileId: String,
        language: AppLanguagePreference,
        host: MedicineReminderOverlayHost
    ): OverlayView {
        val root = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
        }
        val scrim = View(context).apply {
            setBackgroundColor(OVERLAY_SCRIM)
            alpha = 0f
        }
        root.addView(
            scrim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 24), dp(context, 22), dp(context, 24), dp(context, 22))
            background = roundedBackground(PANEL_BACKGROUND, dp(context, 16).toFloat())
            elevation = dp(context, 18).toFloat()
            cameraDistance = resources.displayMetrics.density * 8_000f
            alpha = 0f
            scaleX = 0.88f
            scaleY = 0.88f
            translationY = dp(context, 42).toFloat()
            rotation = -1.4f
        }
        root.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ).apply {
                leftMargin = dp(context, 18)
                topMargin = dp(context, 30)
                rightMargin = dp(context, 18)
                bottomMargin = dp(context, 30)
            }
        )

        val accent = View(context).apply {
            background = roundedBackground(ACCENT, dp(context, 3).toFloat())
        }
        panel.addView(
            accent,
            LinearLayout.LayoutParams(dp(context, 48), dp(context, 5))
        )

        val slotBadge = TextView(context).apply {
            text = slot.displayLabel(language)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ACCENT_TEXT)
            setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
            background = roundedBackground(ACCENT_SURFACE, dp(context, 10).toFloat())
        }
        panel.addView(slotBadge, wrapTopMarginParams(context, 18))

        val title = TextView(context).apply {
            text = translateMedicineUiText("Medicine check", language)
            textSize = 29f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(PRIMARY_TEXT)
        }
        panel.addView(title, topMarginParams(context, 14))

        val selectedText = TextView(context).apply {
            textSize = 16f
            setTextColor(SECONDARY_TEXT)
        }
        panel.addView(selectedText, topMarginParams(context, 8))

        val takenButton = actionButton(
            context = context,
            text = translateMedicineUiText("Taken", language),
            backgroundColor = CONFIRM_BACKGROUND,
            textColor = CONFIRM_TEXT
        )

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

        val reviewButton = actionButton(
            context = context,
            text = translateMedicineUiText("Review medicines", language),
            backgroundColor = SECONDARY_ACTION_BACKGROUND,
            textColor = PRIMARY_TEXT
        ).apply {
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val expanding = listContainer.visibility != View.VISIBLE
                TransitionManager.beginDelayedTransition(
                    panel,
                    medicineListTransition()
                )
                listContainer.visibility = if (expanding) View.VISIBLE else View.GONE
                text = translateMedicineUiText(
                    if (expanding) "Hide medicines" else "Review medicines",
                    language
                )
            }
        }
        panel.addView(reviewButton, topMarginParams(context, 22))
        panel.addView(
            listContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, (medicines.size * 50).coerceIn(92, 260))
            ).apply {
                topMargin = dp(context, 10)
            }
        )

        val cancelButton = actionButton(
            context = context,
            text = translateMedicineUiText("Cancel", language),
            backgroundColor = SECONDARY_ACTION_BACKGROUND,
            textColor = PRIMARY_TEXT
        ).apply {
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                dismiss(host)
                MedicineReminderNotifier.cancel(context, slot, profileId)
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
            takenButton.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            recordTakenAndClose(
                context = context,
                slot = slot,
                selectedIds = selectedIds.toSet(),
                profileId = profileId,
                language = language,
                takenButton = takenButton,
                cancelButton = cancelButton,
                host = host
            )
        }

        panel.addView(buttonRow(context, cancelButton, takenButton), topMarginParams(context, 22))
        return OverlayView(
            root = root,
            scrim = scrim,
            panel = panel,
            motion = MedicineReminderTiltController(context, panel)
        )
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
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
        }
        medicines.forEach { medicine ->
            val localId = medicine.localId
            val checkbox = CheckBox(context).apply {
                text = medicine.displayText(language).name
                textSize = 15f
                setTextColor(PRIMARY_TEXT)
                buttonTintList = ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_checked),
                        intArrayOf()
                    ),
                    intArrayOf(CONFIRM_BACKGROUND, SECONDARY_TEXT)
                )
                minHeight = dp(context, 46)
                isEnabled = localId > 0L
                isChecked = localId in selectedIds
                setOnCheckedChangeListener { _, checked ->
                    if (localId <= 0L) return@setOnCheckedChangeListener
                    if (checked) selectedIds += localId else selectedIds -= localId
                    onSelectionChange()
                }
            }
            list.addView(
                checkbox,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
        return ScrollView(context).apply {
            background = roundedBackground(LIST_BACKGROUND, dp(context, 10).toFloat())
            isFillViewport = false
            addView(list)
        }
    }

    private fun recordTakenAndClose(
        context: Context,
        slot: MedicineSlot,
        selectedIds: Set<Long>,
        profileId: String,
        language: AppLanguagePreference,
        takenButton: Button,
        cancelButton: Button,
        host: MedicineReminderOverlayHost
    ) {
        takenButton.isEnabled = false
        cancelButton.isEnabled = false
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val repository = MedicineRepository(AppDb.get(context, profileId))
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
                        profileId = profileId,
                        zoneId = ZoneId.systemDefault()
                    )
                }
            }.onSuccess {
                MedicineReminderNotifier.cancel(context, slot, profileId)
                dismiss(host)
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

    private fun animateEntrance(
        context: Context,
        overlay: OverlayView,
        host: MedicineReminderOverlayHost
    ) {
        overlay.root.post {
            if (host.currentRoot !== overlay.root) return@post
            vibrateOnce(context)
            // Keeping the scrim separate avoids compositing the animated panel twice.
            overlay.scrim.animate()
                .alpha(1f)
                .setDuration(SCRIM_ENTER_DURATION_MILLIS)
                .withLayer()
                .start()
            overlay.panel.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .rotation(0f)
                .setDuration(PANEL_ENTER_DURATION_MILLIS)
                .setInterpolator(ENTER_INTERPOLATOR)
                .withLayer()
                .withEndAction {
                    if (host.currentRoot === overlay.root) overlay.motion.start()
                }
                .start()
        }
    }

    private fun selectedSummary(
        selectedIds: Set<Long>,
        medicines: List<MedicineItem>,
        language: AppLanguagePreference
    ): String = translateMedicineUiText(
        "Selected medicines: ${selectedIds.size}/${medicines.size}",
        language
    )

    private fun dismiss(host: MedicineReminderOverlayHost) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissOnMain(host)
        } else {
            mainHandler.post { dismissOnMain(host) }
        }
    }

    private fun dismissOnMain(host: MedicineReminderOverlayHost) {
        val root = host.currentRoot ?: return
        val scrim = host.currentScrim ?: return removeImmediately(host)
        val panel = host.currentPanel ?: return removeImmediately(host)
        if (host.dismissing) return

        host.dismissing = true
        host.stopMotion?.invoke()
        host.stopMotion = null
        scrim.animate().cancel()
        panel.animate().cancel()
        scrim.animate()
            .alpha(0f)
            .setDuration(EXIT_DURATION_MILLIS)
            .withLayer()
            .start()
        panel.animate()
            .alpha(0f)
            .scaleX(0.94f)
            .scaleY(0.94f)
            .translationY(dp(root.context, 24).toFloat())
            .setDuration(EXIT_DURATION_MILLIS)
            .withLayer()
            .withEndAction {
                if (host.currentRoot === root) removeImmediately(host)
            }
            .start()
    }

    private fun removeImmediately(host: MedicineReminderOverlayHost) {
        val root = host.currentRoot
        host.stopMotion?.invoke()
        if (root != null) {
            runCatching { host.currentWindowManager?.removeViewImmediate(root) }
                .onFailure { throwable ->
                    Log.w(TAG, "Medicine overlay dismiss failed", throwable)
                }
        }
        host.currentRoot = null
        host.currentScrim = null
        host.currentPanel = null
        host.currentWindowManager = null
        host.stopMotion = null
        host.dismissing = false
    }

    private fun overlayHost(context: Context): MedicineReminderOverlayHost =
        (context.applicationContext as HealthConnectApplication).medicineReminderOverlayHost

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

    private fun actionButton(
        context: Context,
        text: String,
        backgroundColor: Int,
        textColor: Int
    ): Button = Button(context).apply {
        this.text = text
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(
            ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                intArrayOf(DISABLED_TEXT, textColor)
            )
        )
        setAllCaps(false)
        minHeight = dp(context, 50)
        minimumHeight = dp(context, 50)
        stateListAnimator = null
        background = buttonBackground(context, backgroundColor)
    }

    private fun buttonRow(
        context: Context,
        cancelButton: Button,
        takenButton: Button
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END
        addView(
            cancelButton,
            LinearLayout.LayoutParams(0, dp(context, 50), 1f)
        )
        addView(
            takenButton,
            LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply {
                marginStart = dp(context, 10)
            }
        )
    }

    private fun buttonBackground(context: Context, enabledColor: Int): RippleDrawable {
        val states = StateListDrawable().apply {
            addState(
                intArrayOf(-android.R.attr.state_enabled),
                roundedBackground(DISABLED_BACKGROUND, dp(context, 8).toFloat())
            )
            addState(
                intArrayOf(),
                roundedBackground(enabledColor, dp(context, 8).toFloat())
            )
        }
        return RippleDrawable(ColorStateList.valueOf(RIPPLE), states, null)
    }

    private fun roundedBackground(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }

    private fun medicineListTransition(): TransitionSet = TransitionSet().apply {
        // AutoTransition runs fade-out, resize, and fade-in sequentially. Together keeps
        // medicine names readable while the overlay changes height on slower devices.
        ordering = TransitionSet.ORDERING_TOGETHER
        duration = EXPAND_DURATION_MILLIS
        interpolator = ENTER_INTERPOLATOR
        addTransition(ChangeBounds())
        addTransition(Fade())
    }

    private fun topMarginParams(context: Context, top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(context, top)
        }

    private fun wrapTopMarginParams(context: Context, top: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(context, top)
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun vibrateOnce(context: Context) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Vibrator::class.java)
            }
            if (vibrator?.hasVibrator() != true) return
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0L, 42L, 68L, 58L), -1)
            )
        }.onFailure { error ->
            Log.w(TAG, "Medicine overlay vibration failed", error)
        }
    }

    private data class OverlayView(
        val root: View,
        val scrim: View,
        val panel: View,
        val motion: MedicineReminderTiltController
    )

    private const val TAG = "MedicineReminder"
    private const val SCRIM_ENTER_DURATION_MILLIS = 190L
    private const val PANEL_ENTER_DURATION_MILLIS = 420L
    private const val EXIT_DURATION_MILLIS = 170L
    private const val EXPAND_DURATION_MILLIS = 220L
    private val ENTER_INTERPOLATOR = PathInterpolator(0.2f, 0.9f, 0.2f, 1f)

    private val OVERLAY_SCRIM = Color.argb(236, 8, 11, 13)
    private val PANEL_BACKGROUND = Color.rgb(21, 26, 30)
    private val LIST_BACKGROUND = Color.rgb(33, 40, 45)
    private val PRIMARY_TEXT = Color.rgb(241, 244, 245)
    private val SECONDARY_TEXT = Color.rgb(170, 181, 187)
    private val ACCENT = Color.rgb(221, 126, 96)
    private val ACCENT_SURFACE = Color.rgb(57, 39, 35)
    private val ACCENT_TEXT = Color.rgb(249, 181, 156)
    private val CONFIRM_BACKGROUND = Color.rgb(91, 190, 158)
    private val CONFIRM_TEXT = Color.rgb(12, 32, 26)
    private val SECONDARY_ACTION_BACKGROUND = Color.rgb(42, 50, 55)
    private val DISABLED_BACKGROUND = Color.rgb(37, 42, 45)
    private val DISABLED_TEXT = Color.rgb(104, 112, 117)
    private val RIPPLE = Color.argb(48, 255, 255, 255)
}

private class MedicineReminderTiltController(
    context: Context,
    private val panel: View
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val density = context.resources.displayMetrics.density
    private var baselineX: Float? = null
    private var baselineY: Float? = null
    private var tilt = MedicineReminderTilt()
    private var running = false

    fun start() {
        if (running || sensorManager == null || accelerometer == null) return
        running = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )
    }

    fun stop() {
        if (!running) return
        sensorManager?.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER || event.values.size < 2) return
        val currentX = event.values[0]
        val currentY = event.values[1]
        val originX = baselineX ?: currentX.also { baselineX = it }
        val originY = baselineY ?: currentY.also { baselineY = it }
        tilt = MedicineReminderMotionPolicy.nextTilt(
            previous = tilt,
            baselineX = originX,
            baselineY = originY,
            currentX = currentX,
            currentY = currentY
        )

        panel.translationX = -tilt.horizontal * density * 6f
        panel.translationY = tilt.vertical * density * 4f
        panel.rotationY = tilt.horizontal * 2.4f
        panel.rotationX = -tilt.vertical * 1.8f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
