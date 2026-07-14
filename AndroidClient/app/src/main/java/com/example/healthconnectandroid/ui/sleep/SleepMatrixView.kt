package com.example.healthconnectandroid.ui.sleep

import com.example.healthconnectandroid.hc.SleepQualityMatrixModel

fun selectedSleepLabel(model: SleepQualityMatrixModel, selectedBoxIds: Set<String>): String? {
    val selected = model.boxes.filter { it.id in selectedBoxIds }
    if (selected.isEmpty()) return null
    val label = when {
        selected.size == 1 -> selected.first().label
        selected.size <= 4 -> selected.joinToString(", ") { it.label }
        else -> "${selected.size} groups"
    }
    return "Selected: $label"
}
