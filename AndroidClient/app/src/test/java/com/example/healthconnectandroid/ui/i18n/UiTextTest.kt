package com.example.healthconnectandroid.ui.i18n

import com.example.healthconnectandroid.AppLanguagePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTest {
    @Test
    fun englishLanguageLeavesTextUnchanged() {
        assertEquals(
            "Settings",
            translateUiText("Settings", AppLanguagePreference.ENGLISH)
        )
        assertEquals(
            "Last sync: 5/10 09:00",
            translateUiText("Last sync: 5/10 09:00", AppLanguagePreference.ENGLISH)
        )
    }

    @Test
    fun chineseLanguageTranslatesCoreNavigationAndPreferences() {
        assertEquals(
            "设置",
            translateUiText("Settings", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
        assertEquals(
            "偏好设置",
            translateUiText("Preferences", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
        assertEquals(
            "语言",
            translateUiText("Language", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
    }

    @Test
    fun chineseLanguageTranslatesDynamicStatusPrefixes() {
        assertEquals(
            "上次同步：5/10 09:00",
            translateUiText("Last sync: 5/10 09:00", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
        assertEquals(
            "所选同步失败：no field LAST_3_DAYS",
            translateUiText("Selected sync failed: no field LAST_3_DAYS", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
        assertEquals(
            "1,024 条记录",
            translateUiText("1,024 records", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
        assertEquals(
            "8 个阶段",
            translateUiText("8 stages", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
        assertEquals(
            "已选择：Sun",
            translateUiText("Selected: Sun", AppLanguagePreference.CHINESE_SIMPLIFIED)
        )
    }
}
