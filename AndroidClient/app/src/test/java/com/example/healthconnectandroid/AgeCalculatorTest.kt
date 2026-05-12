package com.example.healthconnectandroid

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgeCalculatorTest {
    @Test
    fun ageIsNullWhenDateOfBirthIsMissing() {
        assertNull(AgeCalculator.ageOn(null, LocalDate.of(2026, 5, 10)))
    }

    @Test
    fun ageUsesBirthdayInCurrentYear() {
        val dob = LocalDate.of(1990, 5, 10)

        assertEquals(35, AgeCalculator.ageOn(dob, LocalDate.of(2026, 5, 9)))
        assertEquals(36, AgeCalculator.ageOn(dob, LocalDate.of(2026, 5, 10)))
    }
}
