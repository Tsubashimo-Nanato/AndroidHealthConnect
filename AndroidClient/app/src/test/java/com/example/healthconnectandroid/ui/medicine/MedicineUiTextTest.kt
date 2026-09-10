package com.example.healthconnectandroid.ui.medicine

import com.example.healthconnectandroid.AppLanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineUiTextTest {
    @Test
    fun chineseLanguageTranslatesMedicineSummaryCounts() {
        assertEquals(
            "23 个启用",
            translateMedicineUiText("23 active", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
        assertEquals(
            "0 条今日记录",
            translateMedicineUiText("0 logs today", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
    }
}
