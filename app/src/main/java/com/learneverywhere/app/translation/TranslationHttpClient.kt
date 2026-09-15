package com.learneverywhere.app.translation

import java.io.IOException

/**
 * Мінімальна межа для HTTP-виклику, схована за `TranslationService`
 * (interfaces.md §translation ховає «HTTP-деталі»). Саме цей інтерфейс
 * замокується в юніт-тестах `translate()` — без реальної мережі.
 */
fun interface TranslationHttpClient {
    @Throws(IOException::class)
    fun get(url: String): String
}
