package com.example.healthconnectandroid.medicine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineReminderMotionPolicyTest {
    @Test
    fun keepsPanelCenteredAtTheSensorBaseline() {
        val tilt = MedicineReminderMotionPolicy.nextTilt(
            previous = MedicineReminderTilt(),
            baselineX = 1.2f,
            baselineY = 8.6f,
            currentX = 1.2f,
            currentY = 8.6f
        )

        assertEquals(0f, tilt.horizontal, 0.0001f)
        assertEquals(0f, tilt.vertical, 0.0001f)
    }

    @Test
    fun clampsLargeMovementAndApproachesItGradually() {
        var tilt = MedicineReminderTilt()
        repeat(30) {
            tilt = MedicineReminderMotionPolicy.nextTilt(
                previous = tilt,
                baselineX = 0f,
                baselineY = 0f,
                currentX = 50f,
                currentY = -50f
            )
        }

        assertTrue(tilt.horizontal in 0.99f..1f)
        assertTrue(tilt.vertical in -1f..-0.99f)
    }

    @Test
    fun ignoresInvalidSensorSamples() {
        val previous = MedicineReminderTilt(horizontal = 0.25f, vertical = -0.4f)

        val tilt = MedicineReminderMotionPolicy.nextTilt(
            previous = previous,
            baselineX = 0f,
            baselineY = 0f,
            currentX = Float.NaN,
            currentY = 1f
        )

        assertEquals(previous, tilt)
    }
}
