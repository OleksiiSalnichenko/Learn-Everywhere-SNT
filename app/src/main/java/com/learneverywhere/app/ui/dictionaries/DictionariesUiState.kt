package com.learneverywhere.app.ui.dictionaries

import android.net.Uri
import com.learneverywhere.app.data.model.Dictionary
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.WordEntry

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

/**
 * Стан екрана "деталі словника" (тікет 07, історія 38/R47) — заміняє
 * список на місці того самого екрана, коли користувач тапає на картку.
 * `null` в `DictionariesViewModel.detailUiState` означає "список", не "деталі".
 */
data class DictionaryDetailUiState(
    val dictionary: Dictionary,
    val words: List<WordEntry>,
    val selectedWordId: Long? = null,
    val searchQuery: String = "",
) {
    /** Поле пошуку з'являється лише коли слів багато (історія 40/A05, R48.1). */
    val isSearchVisible: Boolean
        get() = words.size > SEARCH_VISIBLE_THRESHOLD

    /** Відфільтровані за укр. словом записи — те, що реально показує `LazyColumn`. */
    val filteredWords: List<WordEntry>
        get() = if (searchQuery.isBlank()) {
            words
        } else {
            words.filter { it.ukrainian.contains(searchQuery, ignoreCase = true) }
        }

    val selectedWord: WordEntry?
        get() = words.firstOrNull { it.id == selectedWordId }

    private companion object {
        const val SEARCH_VISIBLE_THRESHOLD = 20
    }
}

/**
 * Стан плею в хедері екрана словників (тікет 10, історії 25/28, R42/R44).
 * `currentWord`/`isPlaying` — пряме відображення `PlaybackController` (08);
 * `showCard` уже враховує і налаштування "показати картку" (04,
 * `AppSettings.showCardDuringPlayback`), і те, що грати взагалі нема чого —
 * composable не повинен сам перевіряти обидві умови.
 */
data class PlaybackUiState(
    val currentWord: WordEntry? = null,
    val isPlaying: Boolean = false,
    val showCard: Boolean = false,
) {
    /** Щось грає/на паузі — хедер показує пауза/стоп замість "плей" (критерій приймання тікета 10). */
    val isActive: Boolean
        get() = currentWord != null
}

/**
 * Діалоги імпорту (тікет 09, історії 21/22, R10.1/R10.2). `null` у
 * [DictionariesViewModel.importDialog] — жодного діалогу не показано.
 */
sealed interface ImportDialog {

    /** Мова не визначена з файлу (R10.1) — просимо користувача обрати перед [DictionariesViewModel.onImportLanguageChosen]. */
    data class ChooseLanguage(val uri: Uri) : ImportDialog

    /** Неправильна структура файлу (історія 22/R10.2) — нічого не імпортовано, база не змінена. */
    data object InvalidFile : ImportDialog
}
