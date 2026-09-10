package com.example.healthconnectandroid.medicine

import com.example.healthconnectandroid.AppLanguagePreference

data class MedicineDisplayText(
    val name: String,
    val doseText: String?,
    val summary: String?,
    val details: String?
)

fun MedicineItem.displayText(language: AppLanguagePreference): MedicineDisplayText =
    MedicineDisplayText(
        name = fallbackMedicineName(name, language),
        doseText = fallbackLocalizedText(doseText, language),
        summary = fallbackLocalizedText(summary, language),
        details = fallbackLocalizedText(details, language)
    )

fun MedicineDoseLog.displayMedicineName(language: AppLanguagePreference): String =
    displayMedicineName(medicineName, language)

fun displayMedicineName(raw: String, language: AppLanguagePreference): String =
    fallbackMedicineName(raw, language)

fun MedicineSlot.displayLabel(language: AppLanguagePreference): String =
    when (language) {
        AppLanguagePreference.ENGLISH -> label
        AppLanguagePreference.CHINESE_SIMPLIFIED -> when (this) {
            MedicineSlot.MORNING -> "早上"
            MedicineSlot.MIDDAY -> "中午"
            MedicineSlot.EVENING -> "晚上"
            MedicineSlot.BEDTIME -> "睡前"
            MedicineSlot.AS_NEEDED -> "按需"
        }
    }

fun MedicineDoseStatus.displayLabel(language: AppLanguagePreference): String =
    when (language) {
        AppLanguagePreference.ENGLISH -> label
        AppLanguagePreference.CHINESE_SIMPLIFIED -> when (this) {
            MedicineDoseStatus.TAKEN -> "已服用"
            MedicineDoseStatus.MISSED -> "未服用"
            MedicineDoseStatus.SKIPPED -> "跳过"
        }
    }

private fun fallbackMedicineName(raw: String, language: AppLanguagePreference): String {
    val parts = raw.split('/').map { it.trim() }.filter { it.isNotBlank() }
    if (parts.size < 2) return raw.trim()

    return if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
        parts.getOrNull(3) ?: parts.getOrNull(2) ?: parts.first()
    } else {
        parts.take(2).joinToString(" / ")
    }
}

private fun fallbackLocalizedText(raw: String?, language: AppLanguagePreference): String? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parts = value.split('/').map { it.trim() }.filter { it.isNotBlank() }
    if (parts.size >= 2) {
        return if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) parts.last() else parts.first()
    }

    val bilingualParts = splitLocalizedSuffix(value) ?: return value
    return if (language == AppLanguagePreference.CHINESE_SIMPLIFIED) {
        bilingualParts.second
    } else {
        bilingualParts.first
    }
}

private fun splitLocalizedSuffix(value: String): Pair<String, String>? {
    val localizedStart = value.indexOfFirst(::isEastAsianLetter)
    if (localizedStart <= 0) return null

    val english = value.substring(0, localizedStart).trim()
    val localized = value.substring(localizedStart).trim()
    if (english.isEmpty() || localized.isEmpty()) return null
    return english to localized
}

private fun isEastAsianLetter(char: Char): Boolean =
    when (Character.UnicodeScript.of(char.code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA -> true
        else -> false
    }
