package com.learneverywhere.app.translation

/**
 * Публічний контракт модуля `translation` (interfaces.md §translation).
 * Звертається до MyMemory Translation API й повертає до двох кращих
 * варіантів перекладу плюс приклад речення. HTTP-деталі й парсинг
 * відповіді — імплементаційна деталь (`TranslationServiceImpl`).
 */
interface TranslationService {
    suspend fun translate(word: String, sourceLang: Language, targetLang: Language): TranslationResult
}
