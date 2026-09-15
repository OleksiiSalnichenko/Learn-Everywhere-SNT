package com.learneverywhere.app.ui.dictionaries

import com.learneverywhere.app.data.model.Dictionary
import com.learneverywhere.app.data.model.DictionaryLanguage

/**
 * Один словник у списку разом з кількістю слів. Лічильник — деталь цього
 * екрана (кільце-індикатор, історія 43/54, R52, A03), тому рахуємо його тут,
 * а не в доменній моделі `Dictionary` з `DictionaryRepository`.
 */
data class DictionaryListItem(
    val dictionary: Dictionary,
    val wordCount: Int,
)

/**
 * Стан екрана "Словники" для активної вкладки (мови). `items` — уже
 * відфільтровані по мові словники з кількістю слів кожного.
 */
data class DictionariesUiState(
    val selectedLanguage: DictionaryLanguage = DictionaryLanguage.GERMAN,
    val items: List<DictionaryListItem> = emptyList(),
) {
    /** Дефолтний словник активної вкладки — хедер і кнопка плей показують саме його (історія 25/R42). */
    val defaultItem: DictionaryListItem?
        get() = items.firstOrNull { it.dictionary.isDefault }
}
