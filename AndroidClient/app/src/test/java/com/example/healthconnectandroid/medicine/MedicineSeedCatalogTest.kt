package com.example.healthconnectandroid.medicine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineSeedCatalogTest {
    @Test
    fun seedMedicinesHaveReadableMetadata() {
        val seeds = MedicineSeedCatalog.testingMedicines

        assertTrue(seeds.isNotEmpty())
        seeds.forEach { seed ->
            assertTrue(seed.name.isNotBlank())
            assertTrue(seed.doseText.isNotBlank())
            assertTrue(seed.summary.isNotBlank())
            assertTrue(seed.details.isNotBlank())
            assertTrue(seed.slots.isNotEmpty())
        }
    }

    @Test
    fun seedMedicineNamesAreUnique() {
        val normalizedNames = MedicineSeedCatalog.testingMedicines
            .map { it.name.trim().lowercase() }

        assertEquals(normalizedNames.size, normalizedNames.toSet().size)
    }

    @Test
    fun seedAliasesAreUniqueAcrossCatalog() {
        val aliases = MedicineSeedCatalog.testingMedicines
            .flatMap { seed -> seed.aliases.map { it.trim().lowercase() } }
            .filter { it.isNotBlank() }

        assertEquals(aliases.size, aliases.toSet().size)
    }

    @Test
    fun asNeededSeedsDoNotCreateReminderSlots() {
        val asNeededSeeds = MedicineSeedCatalog.testingMedicines
            .filter { MedicineSlot.AS_NEEDED in it.slots }

        assertTrue(asNeededSeeds.isNotEmpty())
        asNeededSeeds.forEach { seed ->
            assertEquals(setOf(MedicineSlot.AS_NEEDED), seed.slots)
        }
    }
}
