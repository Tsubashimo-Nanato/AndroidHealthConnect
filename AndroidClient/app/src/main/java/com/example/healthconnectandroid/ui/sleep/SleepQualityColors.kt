package com.example.healthconnectandroid.ui.sleep

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.healthconnectandroid.hc.SleepQualityBand

internal data class SleepQualityColors(
    val cardContainer: Color
)

internal data class SleepDurationColors(
    val container: Color,
    val content: Color
)

@Composable
internal fun sleepQualityColors(band: SleepQualityBand?): SleepQualityColors {
    val scheme = MaterialTheme.colorScheme
    return when (band) {
        SleepQualityBand.GOOD -> sleepColors(0xFF204E35)
        SleepQualityBand.FAIR -> sleepColors(0xFF243A55)
        SleepQualityBand.NAP -> sleepColors(0xFF30375F)
        SleepQualityBand.FRAGMENTED -> sleepColors(0xFF4B3B16)
        SleepQualityBand.SHORT -> sleepColors(0xFF53241F)
        SleepQualityBand.UNKNOWN -> SleepQualityColors(
            cardContainer = scheme.surfaceVariant.copy(alpha = 0.22f)
        )
        null -> SleepQualityColors(
            cardContainer = scheme.surfaceVariant.copy(alpha = 0.16f)
        )
    }
}

@Composable
internal fun sleepDurationColors(totalMinutes: Long?): SleepDurationColors {
    if (totalMinutes == null) {
        val scheme = MaterialTheme.colorScheme
        return SleepDurationColors(
            container = scheme.surfaceVariant.copy(alpha = 0.34f),
            content = scheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
    return SleepDurationColors(
        container = sleepDurationColor(totalMinutes),
        content = Color(0xFF202A24)
    )
}

internal fun sleepDurationChartBarColor(totalMinutes: Long): Color =
    sleepDurationColor(totalMinutes)

internal fun sleepDurationColor(totalMinutes: Long): Color = when {
    totalMinutes < 4 * 60 -> Color(0xFFC9857E)
    totalMinutes < 6 * 60 -> Color(0xFFD3A071)
    totalMinutes < 7 * 60 -> Color(0xFFC5B66F)
    totalMinutes < 9 * 60 -> Color(0xFF7FAA82)
    else -> Color(0xFF6F9C78)
}

private fun sleepColors(container: Long): SleepQualityColors {
    val cardBase = Color(container)
    return SleepQualityColors(
        cardContainer = cardBase.copy(alpha = 0.24f)
    )
}
