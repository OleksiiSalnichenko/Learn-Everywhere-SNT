package com.learneverywhere.app.ui.dictionaries

import com.learneverywhere.app.data.model.Dictionary
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.MainLanguage
import com.learneverywhere.app.data.repository.DictionaryRepository
import com.learneverywhere.app.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Стан і дії екрана "Словники" (тікет 05) — публічний шов, який тестується
 * напряму (без Compose UI-тестів). Навмисно НЕ успадковує `androidx.lifecycle.ViewModel`:
 * проєкт уникає зайвих фреймворкових залежностей (DI — простий сервіс-локатор
 * `AppContainer`, spec.md §«Ін'єкція залежностей»), а `lifecycle-viewmodel`
 * ще не був потрібен жодному тікету. Замість `viewModelScope` приймає
 * `CoroutineScope` ззовні — composable дістає його з `rememberCoroutineScope()`,
 * тест — з `TestScope`. Ціна: стан не переживає зміну конфігурації (поворот
 * екрана) — для цього екрана це прийнятно, дані однаково приходять з Room
 * і перечитуються миттєво.
 */
class DictionariesViewModel(
    private val dictionaryRepository: DictionaryRepository,
    settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {

    // null, поки користувач сам не перемкнув вкладку — тоді активна вкладка
    // визначається "головною мовою" з налаштувань (історія 24, R38, R41).
    // Явний вибір користувача завжди перекриває налаштування, навіть якщо
    // налаштування зміняться після цього.
    private val selectedLanguageOverride = MutableStateFlow<DictionaryLanguage?>(null)

    private val defaultLanguage: Flow<DictionaryLanguage> =
        settingsRepository.settings.map { it.mainLanguage.toDictionaryLanguageOrDefault() }

    val selectedLanguage: StateFlow<DictionaryLanguage> =
        combine(selectedLanguageOverride, defaultLanguage) { override, default -> override ?: default }
            .stateIn(scope, SharingStarted.Eagerly, DictionaryLanguage.GERMAN)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DictionariesUiState> =
        selectedLanguage
            .flatMapLatest { language ->
                dictionaryRepository.dictionaries(language)
                    .flatMapLatest { dictionaries -> withWordCounts(language, dictionaries) }
            }
            .stateIn(scope, SharingStarted.Eagerly, DictionariesUiState())

    /** Перемикання вкладки (історія 24). */
    fun selectTab(language: DictionaryLanguage) {
        selectedLanguageOverride.value = language
    }

    /** Радіокнопка картки (історія 18/37, R11/R46). */
    fun setDefault(dictionaryId: Long) {
        scope.launch { dictionaryRepository.setDefault(dictionaryId) }
    }

    /** Кнопка "+" (історія 42, R51) — створює словник в активній вкладці. Порожню назву ігнорує. */
    fun createDictionary(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val language = selectedLanguage.value
        scope.launch { dictionaryRepository.createDictionary(language, trimmed) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun withWordCounts(
        language: DictionaryLanguage,
        dictionaries: List<Dictionary>,
    ): Flow<DictionariesUiState> {
        if (dictionaries.isEmpty()) {
            return flowOf(DictionariesUiState(language, emptyList()))
        }
        val perDictionary = dictionaries.map { dictionary ->
            dictionaryRepository.words(dictionary.id).map { words -> DictionaryListItem(dictionary, words.size) }
        }
        return combine(perDictionary) { items -> DictionariesUiState(language, items.toList()) }
    }
}

private fun MainLanguage.toDictionaryLanguageOrDefault(): DictionaryLanguage = when (this) {
    MainLanguage.GERMAN -> DictionaryLanguage.GERMAN
    MainLanguage.ENGLISH -> DictionaryLanguage.ENGLISH
    // "ніяка" — дефолт вкладки німецька (історія 24, дослівно з брифа тікета 05).
    MainLanguage.NONE -> DictionaryLanguage.GERMAN
}
