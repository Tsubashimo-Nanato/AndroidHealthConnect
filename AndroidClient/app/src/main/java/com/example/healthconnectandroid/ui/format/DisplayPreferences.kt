package com.example.healthconnectandroid.ui.format

import com.example.healthconnectandroid.UnitSystemPreference
import com.example.healthconnectandroid.UserPreferences
import com.example.healthconnectandroid.AppLanguagePreference
import java.time.DayOfWeek
import java.time.ZoneId

data class DisplayPreferences(
    val unitSystem: UnitSystemPreference,
    val weekStart: DayOfWeek,
    val zoneId: ZoneId,
    val language: AppLanguagePreference
)

fun UserPreferences.toDisplayPreferences(): DisplayPreferences =
    DisplayPreferences(
        unitSystem = unitSystem,
        weekStart = weekStart.dayOfWeek,
        zoneId = zoneId,
        language = language
    )
