package com.learneverywhere.app.ui.home

import com.learneverywhere.app.data.model.DictionaryLanguage

/**
 * Стан головного екрана (тікет 06) — публічний шов [HomeViewModel], що
 * тестується напряму (без Compose UI-тестів, executor.md). [inputText] —
 * поле вводу, спільне для голосового і текстового шляху: розпізнаний або
 * введений текст завжди потрапляє сюди, тому невдалий переклад (R14.2) не
 * губить слово — воно вже стоїть у полі, діалог лише зникає.
 */
data class HomeUiState(
    val inputText: String = "",
    val isListening: Boolean = false,
    val dialog: HomeDialog? = null,
)

/** Один з чотирьох діалогів шляху захоплення слова (spec.md історії 1–14). */
sealed interface HomeDialog {

    /** Історія 5 / R16: українське слово, головна мова "ніяка" — питаємо, куди покласти. */
    data class ChooseDictionary(val ukrainianWord: String) : HomeDialog

    /** Історія 11 / R18: підсумок перед записом у базу — Ок/Cancel. */
    data class Confirm(val capture: WordCapture) : HomeDialog

    /** Історія 2 / R14.1: розпізнавання не вдалося (тиша, шум, дозвіл). */
    data object RecognitionFailed : HomeDialog

    /** Історія 14 / R14.2: переклад недоступний — кнопка "повторити". */
    data object TranslationFailed : HomeDialog
}

/**
 * Дані, готові піти в [com.learneverywhere.app.data.repository.DictionaryRepository.addWord],
 * зібрані з результату [com.learneverywhere.app.translation.TranslationService.translate]
 * (детально — коментар над `HomeViewModel.translateFromUkrainian`/`translateForeignWord`).
 */
data class WordCapture(
    val ukrainian: String,
    val translation1: String,
    val translation2: String?,
    val example: String,
    val isExampleGenerated: Boolean,
    val dictionaryLanguage: DictionaryLanguage,
)
