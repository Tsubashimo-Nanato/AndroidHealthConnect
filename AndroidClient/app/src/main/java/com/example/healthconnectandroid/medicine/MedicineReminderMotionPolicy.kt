package com.example.healthconnectandroid.medicine

data class MedicineReminderTilt(
    val horizontal: Float = 0f,
    val vertical: Float = 0f
)

object MedicineReminderMotionPolicy {
    fun nextTilt(
        previous: MedicineReminderTilt,
        baselineX: Float,
        baselineY: Float,
        currentX: Float,
        currentY: Float
    ): MedicineReminderTilt {
        if (!baselineX.isFinite() || !baselineY.isFinite()) return previous
        if (!currentX.isFinite() || !currentY.isFinite()) return previous

        val targetHorizontal = ((currentX - baselineX) / FULL_TILT_DELTA).coerceIn(-1f, 1f)
        val targetVertical = ((currentY - baselineY) / FULL_TILT_DELTA).coerceIn(-1f, 1f)
        return MedicineReminderTilt(
            horizontal = smooth(previous.horizontal, targetHorizontal),
            vertical = smooth(previous.vertical, targetVertical)
        )
    }

    private fun smooth(previous: Float, target: Float): Float =
        previous + (target - previous) * SENSOR_RESPONSE

    private const val FULL_TILT_DELTA = 3.5f
    private const val SENSOR_RESPONSE = 0.18f
}
