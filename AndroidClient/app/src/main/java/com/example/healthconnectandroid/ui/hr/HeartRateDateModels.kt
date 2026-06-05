package com.example.healthconnectandroid.ui.hr

import com.example.healthconnectandroid.hc.HrDateQuality
import java.time.LocalDate

data class HrDateCell(
    val date: LocalDate,
    val label: String,
    val quality: HrDateQuality,
    val zoneScore: Float = 0f,
    val sampleCount: Int
)
