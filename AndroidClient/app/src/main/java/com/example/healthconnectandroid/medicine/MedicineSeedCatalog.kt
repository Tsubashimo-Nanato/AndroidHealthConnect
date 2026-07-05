package com.example.healthconnectandroid.medicine

internal data class MedicineSeed(
    val name: String,
    val doseText: String,
    val summary: String,
    val details: String,
    val slots: Set<MedicineSlot>,
    val aliases: Set<String> = emptySet()
)

internal object MedicineSeedCatalog {
    val testingMedicines: List<MedicineSeed> = listOf(
        MedicineSeed(
            name = "Morning maintenance sample / active ingredient A",
            doseText = "1 tablet in the morning",
            summary = "Daily scheduled placeholder for morning reminder testing.",
            details = "Public seed data is intentionally generic. Replace it locally with real medicines when testing personal schedules.",
            slots = setOf(MedicineSlot.MORNING)
        ),
        MedicineSeed(
            name = "Bedtime maintenance sample / active ingredient B",
            doseText = "1 tablet before bed",
            summary = "Daily scheduled placeholder for bedtime reminder testing.",
            details = "Used to exercise bedtime schedules, notification actions, and dose logs without publishing private medication details.",
            slots = setOf(MedicineSlot.BEDTIME)
        ),
        MedicineSeed(
            name = "As-needed sample / active ingredient C",
            doseText = "Use as needed",
            summary = "Manual-log placeholder for as-needed medicine testing.",
            details = "As-needed seeds stay unchecked by default so quick logging can test optional medicine selection.",
            slots = setOf(MedicineSlot.AS_NEEDED)
        )
    )
}
