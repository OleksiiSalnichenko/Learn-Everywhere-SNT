package com.learneverywhere.app.data.model

/**
 * Доменна модель словника — те, що бачать ViewModel і Compose-екрани.
 * Навмисно окрема від `DictionaryEntity` (шар `data.db`), щоб схема
 * Room-таблиці лишалася деталлю реалізації, схованою за `DictionaryRepository`
 * (межа зафіксована в `interfaces.md`).
 */
data class Dictionary(
    val id: Long,
    val language: DictionaryLanguage,
    val name: String,
    val isDefault: Boolean,
)
