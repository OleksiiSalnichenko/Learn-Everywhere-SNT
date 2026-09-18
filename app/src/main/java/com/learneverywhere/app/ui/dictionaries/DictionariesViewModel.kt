package com.learneverywhere.app.ui.dictionaries

import android.net.Uri
import com.learneverywhere.app.data.model.Dictionary
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.MainLanguage
import com.learneverywhere.app.data.model.WordEntry
import com.learneverywhere.app.data.repository.DictionaryRepository
import com.learneverywhere.app.data.repository.InvalidDictionaryFileException
import com.learneverywhere.app.playback.PlaybackActions
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
    // Тікет 10 — `null` за замовчуванням: composable, що не передав контролер
    // (напр. майбутній прев'ю), лишається робочим, просто без плею. Реальний
    // екран (`DictionariesScreen`) завжди передає живий `PlaybackController`
    // (08) — сам клас лишається єдиною реалізацією, інтерфейс лише для тестів.
    private val playback: PlaybackActions? = null,
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

    // --- Плей у хедері (тікет 10, історії 25/28, R42/R44) ------------------------------------

    /** `null`-контролер (прев'ю без Android `Context`) — стан завжди "нічого не грає". */
    val playbackUiState: StateFlow<PlaybackUiState> = playback?.let { controller ->
        combine(
            controller.currentWord,
            controller.isPlaying,
            settingsRepository.settings,
        ) { word, isPlaying, settings ->
            PlaybackUiState(
                currentWord = word,
                isPlaying = isPlaying,
                showCard = settings.showCardDuringPlayback && word != null,
            )
        }.stateIn(scope, SharingStarted.Eagerly, PlaybackUiState())
    } ?: MutableStateFlow(PlaybackUiState())

    /**
     * Велика кнопка плей у хедері (історія 25, R42). Нічого не грає — стартує
     * дефолтний словник активної вкладки; уже грає — та сама кнопка перемикає
     * пауза/відтворити (той самий ефект, що й кнопка мініплеєра, 08).
     * Порожній дефолтний словник (історія 27/R43.1) — кнопка неактивна в UI,
     * тут теж no-op, щоб не впасти на випадковому подвійному натисканні.
     */
    fun onPlayHeaderClick() {
        val controller = playback ?: return
        val state = playbackUiState.value
        when {
            !state.isActive -> {
                val defaultDictionaryId = uiState.value.defaultItem?.dictionary?.id ?: return
                controller.start(defaultDictionaryId)
            }
            state.isPlaying -> controller.pause()
            else -> controller.resume()
        }
    }

    /** Стоп з екрана словників (критерій приймання тікета 10) — той самий ефект, що й у мініплеєра (08). */
    fun onStopPlaybackClick() {
        playback?.stop()
    }

    // --- Деталі словника (тікет 07, історія 38/R47) ---------------------------------------

    // null — показаний список; id — показані деталі цього словника на тому ж екрані.
    private val selectedDictionaryId = MutableStateFlow<Long?>(null)
    private val selectedWordId = MutableStateFlow<Long?>(null)
    private val wordSearchQuery = MutableStateFlow("")

    // Читаємо об'єкт словника з тих самих `uiState.items`, а не окремим запитом до
    // репозиторію — так перейменування (rename) одразу видно і в заголовку деталей,
    // і в списку (05), бо обидва похідні від одного Flow з DictionaryRepository.
    private val selectedDictionary: Flow<Dictionary?> =
        combine(selectedDictionaryId, uiState) { id, ui -> ui.items.firstOrNull { it.dictionary.id == id }?.dictionary }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedDictionaryWords: Flow<List<WordEntry>> =
        selectedDictionaryId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else dictionaryRepository.words(id)
        }

    val detailUiState: StateFlow<DictionaryDetailUiState?> =
        combine(
            selectedDictionary,
            selectedDictionaryWords,
            selectedWordId,
            wordSearchQuery,
        ) { dictionary, words, wordId, query ->
            dictionary?.let { DictionaryDetailUiState(it, words, wordId, query) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /** Тап на картку словника (05) — заміняє список деталями на тому ж екрані. */
    fun openDictionary(dictionaryId: Long) {
        selectedDictionaryId.value = dictionaryId
        selectedWordId.value = null
        wordSearchQuery.value = ""
    }

    /** Кнопка "назад" у панелі деталей — повертає список. */
    fun closeDictionary() {
        selectedDictionaryId.value = null
        selectedWordId.value = null
        wordSearchQuery.value = ""
    }

    /** Тап на рядок слова — виділяє його для кнопок "редагувати"/"видалити" зверху; повторний тап знімає виділення. */
    fun selectWord(wordId: Long) {
        selectedWordId.value = if (selectedWordId.value == wordId) null else wordId
    }

    /** Поле пошуку (A05) — фільтрує `filteredWords` за укр. словом, з'являється лише коли слів > 20. */
    fun setWordSearchQuery(query: String) {
        wordSearchQuery.value = query
    }

    /** Перейменування словника (R49) — діалог з полем, кнопка "Ок". Порожню назву ігнорує. */
    fun renameDictionary(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val id = selectedDictionaryId.value ?: return
        scope.launch { dictionaryRepository.rename(id, trimmed) }
    }

    /** Видалення словника (R49) — з підтвердженням у UI; тут одразу закриваємо деталі й повертаємось до списку. */
    fun deleteDictionary() {
        val id = selectedDictionaryId.value ?: return
        scope.launch { dictionaryRepository.deleteDictionary(id) }
        closeDictionary()
    }

    /** Ок у діалозі редагування виділеного слова (R49/R50) — зберігає всі поля разом. */
    fun updateWord(word: WordEntry) {
        scope.launch { dictionaryRepository.updateWord(word) }
    }

    /** Видалення виділеного слова (R49) — без підтвердження, легко повернути повторним вводом. */
    fun deleteWord(wordId: Long) {
        scope.launch { dictionaryRepository.deleteWord(wordId) }
        if (selectedWordId.value == wordId) selectedWordId.value = null
    }

    // --- Імпорт/експорт JSON (тікет 09, історії 20-22/R10, R10.1, R10.2) -----------------

    private val _importDialog = MutableStateFlow<ImportDialog?>(null)
    val importDialog: StateFlow<ImportDialog?> = _importDialog

    /**
     * Кнопка експорту в деталях словника (07) — репозиторій готує JSON-файл і
     * повертає його Uri; [onReady] будує системний Intent "поділитися" — те,
     * як саме показати діалог, лишається UI (composable), не ViewModel.
     */
    fun exportDictionary(dictionaryId: Long, onReady: (Uri) -> Unit) {
        scope.launch {
            val uri = dictionaryRepository.exportToJson(dictionaryId)
            onReady(uri)
        }
    }

    /**
     * Кнопка імпорту (05) обрала файл через системний вибір — визначаємо мову
     * з файлу (R10.1); якщо там її нема чи вона невалідна, питаємо
     * користувача через [ImportDialog.ChooseLanguage] замість негайного імпорту.
     */
    fun onImportFileSelected(uri: Uri) {
        scope.launch {
            val detectedLanguage = dictionaryRepository.detectImportLanguage(uri)
            if (detectedLanguage != null) {
                performImport(uri, detectedLanguage)
            } else {
                _importDialog.value = ImportDialog.ChooseLanguage(uri)
            }
        }
    }

    /** Користувач обрав мову в діалозі [ImportDialog.ChooseLanguage]. */
    fun onImportLanguageChosen(uri: Uri, language: DictionaryLanguage) {
        performImport(uri, language)
    }

    /** Закриває будь-який діалог імпорту (скасування вибору мови або закриття повідомлення про помилку). */
    fun onDismissImportDialog() {
        _importDialog.value = null
    }

    private fun performImport(uri: Uri, language: DictionaryLanguage) {
        scope.launch {
            try {
                dictionaryRepository.importFromJson(uri, language)
                _importDialog.value = null
            } catch (e: InvalidDictionaryFileException) {
                // Неправильна структура файлу (історія 22/R10.2) — репозиторій
                // гарантує, що до цього моменту в базу нічого не записано.
                _importDialog.value = ImportDialog.InvalidFile
            }
        }
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
