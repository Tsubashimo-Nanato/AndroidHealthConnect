package com.example.healthconnectandroid.medicine

import com.example.healthconnectandroid.AppLanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineDisplayTextTest {
    @Test
    fun medicineNameUsesLanguageSpecificReadableParts() {
        val medicine = MedicineItem(
            localId = 1,
            name = "Sample Brand / sample ingredient / sample kana / 示例药",
            notes = null,
            doseText = "1 tablet / 1片",
            summary = "Daily scheduled sample / 每日计划示例",
            details = null,
            active = true,
            slots = listOf(MedicineSlot.MORNING)
        )

        val english = medicine.displayText(AppLanguagePreference.ENGLISH)
        val chinese = medicine.displayText(AppLanguagePreference.CHINESE_SIMPLIFIED)

        assertEquals("Sample Brand / sample ingredient", english.name)
        assertEquals("示例药", chinese.name)
        assertEquals("1 tablet", english.doseText)
        assertEquals("1片", chinese.doseText)
        assertEquals("Daily scheduled sample", english.summary)
        assertEquals("每日计划示例", chinese.summary)
    }

    @Test
    fun singlePartMedicineNameStaysReadable() {
        assertEquals(
            "One-off sample",
            displayMedicineName("One-off sample", AppLanguagePreference.ENGLISH)
        )
    }

    @Test
    fun sentenceSeparatedBilingualSummaryUsesOnlyTheSelectedLanguage() {
        val medicine = MedicineItem(
            localId = 2,
            name = "Sample Brand / sample ingredient / sample kana / 示例药",
            notes = null,
            doseText = null,
            summary = "Scheduled medicine summary. 计划用药简介。",
            details = null,
            active = true,
            slots = listOf(MedicineSlot.BEDTIME)
        )

        assertEquals(
            "Scheduled medicine summary.",
            medicine.displayText(AppLanguagePreference.ENGLISH).summary
        )
        assertEquals(
            "计划用药简介。",
            medicine.displayText(AppLanguagePreference.CHINESE_SIMPLIFIED).summary
        )
    }
}
