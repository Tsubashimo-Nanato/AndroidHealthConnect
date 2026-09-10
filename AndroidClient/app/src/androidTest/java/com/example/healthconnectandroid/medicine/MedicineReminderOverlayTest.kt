package com.example.healthconnectandroid.medicine

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.healthconnectandroid.AppPreferences
import com.example.healthconnectandroid.HealthConnectApplication
import com.example.healthconnectandroid.ui.medicine.translateMedicineUiText
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicineReminderOverlayTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = ApplicationProvider.getApplicationContext<HealthConnectApplication>()
    private var overlayPermissionWasGranted = false

    @Before
    fun allowOverlayForTest() {
        overlayPermissionWasGranted = Settings.canDrawOverlays(app)
        if (!overlayPermissionWasGranted) {
            runShell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW allow")
            waitForOverlayPermission(expected = true)
        }
    }

    @After
    fun cleanUpOverlay() {
        val root = app.medicineReminderOverlayHost.currentRoot
        val cancel = root?.findTextView(cancelText())
        if (cancel != null) instrumentation.runOnMainSync(cancel::performClick)
        waitForRoot(expectedPresent = false)

        if (!overlayPermissionWasGranted) {
            runShell("appops set ${app.packageName} SYSTEM_ALERT_WINDOW deny")
            waitForOverlayPermission(expected = false)
        }
    }

    @Test
    fun startsCollapsedExpandsMedicineNamesAndCancels() {
        val medicines = listOf(
            medicine(1L, "Morning tablet"),
            medicine(2L, "Daily capsule")
        )

        assertTrue(
            MedicineReminderOverlay.show(
                context = app,
                slot = MedicineSlot.MORNING,
                medicines = medicines
            )
        )
        val root = requireNotNull(waitForRoot(expectedPresent = true))
        assertNotNull(root)
        Thread.sleep(500L)

        val firstMedicine = requireNotNull(root.findTextView("Morning tablet"))
        assertFalse(firstMedicine.isShown)
        captureIfRequested("medicine-reminder-collapsed.png")

        val review = requireNotNull(root.findTextView(reviewText()))
        instrumentation.runOnMainSync(review::performClick)
        Thread.sleep(500L)

        assertTrue(firstMedicine.isShown)
        assertTrue(requireNotNull(root.findTextView("Daily capsule")).isShown)
        captureIfRequested("medicine-reminder-expanded.png")

        val cancel = requireNotNull(root.findTextView(cancelText()))
        instrumentation.runOnMainSync(cancel::performClick)
        waitForRoot(expectedPresent = false)
        assertNull(app.medicineReminderOverlayHost.currentRoot)
    }

    private fun medicine(localId: Long, name: String) = MedicineItem(
        localId = localId,
        name = name,
        notes = null,
        doseText = null,
        summary = null,
        details = null,
        active = true,
        slots = listOf(MedicineSlot.MORNING)
    )

    private fun reviewText(): String = translateMedicineUiText(
        "Review medicines",
        AppPreferences.userPreferences(app).language
    )

    private fun cancelText(): String = translateMedicineUiText(
        "Cancel",
        AppPreferences.userPreferences(app).language
    )

    private fun waitForRoot(expectedPresent: Boolean): View? {
        repeat(40) {
            val root = app.medicineReminderOverlayHost.currentRoot
            if ((root != null) == expectedPresent) return root
            Thread.sleep(50L)
        }
        return app.medicineReminderOverlayHost.currentRoot
    }

    private fun waitForOverlayPermission(expected: Boolean) {
        repeat(40) {
            if (Settings.canDrawOverlays(app) == expected) return
            Thread.sleep(50L)
        }
        assertTrue(
            "Overlay permission did not reach expected state=$expected",
            Settings.canDrawOverlays(app) == expected
        )
    }

    private fun runShell(command: String) {
        instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).readBytes()
        }
    }

    private fun captureIfRequested(fileName: String) {
        val arguments = InstrumentationRegistry.getArguments()
        if (arguments.getString("captureArtifacts") != "true") return

        val output = File(requireNotNull(app.getExternalFilesDir(null)), fileName)
        FileOutputStream(output).use { stream ->
            instrumentation.uiAutomation.takeScreenshot()
                .compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        println("medicineReminderCapture=${output.absolutePath}")
    }
}

private fun View.findTextView(text: String): TextView? {
    if (this is TextView && this.text.toString() == text) return this
    if (this !is ViewGroup) return null

    for (index in 0 until childCount) {
        getChildAt(index).findTextView(text)?.let { return it }
    }
    return null
}
