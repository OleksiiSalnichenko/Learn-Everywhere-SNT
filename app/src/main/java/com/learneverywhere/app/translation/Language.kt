package com.learneverywhere.app.translation

/**
 * Мова для запиту до MyMemory (`langpair=<sourceLang>|<targetLang>`,
 * spec.md §«Переклад слів»). Українська — завжди мова слова (джерело),
 * німецька/англійська — мова словника (ціль).
 */
enum class Language(val isoCode: String) {
    UKRAINIAN("uk"),
    GERMAN("de"),
    ENGLISH("en"),
}
