package com.learneverywhere.app.translation

/**
 * Результат `TranslationService.translate` (interfaces.md §translation).
 *
 * [candidates] — 1 або 2 варіанти перекладу з найвищою якістю збігу
 * (R06/R07); якщо кандидат один — другого елемента в списку просто
 * немає (не порожній рядок).
 * [example] — речення-приклад мовою словника (R08); [isExampleGenerated]
 * — `true`, якщо в відповіді MyMemory не знайшлося придатного речення
 * і підставлена шаблонна фраза (R08.1).
 */
data class TranslationResult(
    val candidates: List<String>,
    val example: String,
    val isExampleGenerated: Boolean,
)
