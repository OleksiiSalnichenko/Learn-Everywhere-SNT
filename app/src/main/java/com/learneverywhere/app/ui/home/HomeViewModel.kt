package com.learneverywhere.app.ui.home

import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.MainLanguage
import com.learneverywhere.app.data.repository.DictionaryRepository
import com.learneverywhere.app.settings.AppSettings
import com.learneverywhere.app.settings.SettingsRepository
import com.learneverywhere.app.translation.Language
import com.learneverywhere.app.translation.TranslationService
import com.learneverywhere.app.translation.TranslationUnavailableException
import com.learneverywhere.app.voice.LanguageDetector
import com.learneverywhere.app.voice.SpeechInput
import com.learneverywhere.app.voice.SpeechResult
import com.learneverywhere.app.voice.SpokenLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Стан і дії головного екрана (тікет 06) — публічний шов, що тестується
 * напряму (interfaces.md, executor.md: "тести на швах, названих у спеці").
 * Навмисно НЕ `androidx.lifecycle.ViewModel`, той самий свідомий компроміс,
 * що й `DictionariesViewModel` (тікет 05, не тягнути `lifecycle-viewmodel`
 * заради одного екрана) — приймає `CoroutineScope` ззовні.
 *
 * Голосовий і текстовий шлях сходяться в одному приватному методі
 * [beginCapture] — обидва критерії приймання "однаковий діалог
 * підтвердження" виконуються самою формою коду, а не окремою перевіркою.
 */
class HomeViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val translationService: TranslationService,
    private val speechInput: SpeechInput,
    // Той самий інстанс, що тримає `AppContainer` (interfaces.md §voice) —
    // не будуємо тут власний, щоб не дублювати гачок `interfaceLanguage`.
    private val languageDetector: LanguageDetector,
    settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
    /** Локалізована назва дефолтного словника (`R.string.default_dictionary_name`) — переданa
     * ззовні, а не читана з `Context`, щоб ViewModel лишався тестованим без Android. */
    private val defaultDictionaryName: () -> String,
) {

    private val settingsSnapshot: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // Що саме повторити після "повторити" в діалозі мережевої помилки (R14.2).
    // Не частина HomeUiState — це поведінка, не дані для показу.
    private var pendingRetry: (() -> Unit)? = null

    /** Поле вводу — спільне для голосового і текстового шляху (історія 13, R21). */
    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /** Натискання мікрофона (історія 1, R14). Дозвіл RECORD_AUDIO — відповідальність UI-шару
     * (ui/home composable), тут лише слухаємо: `SpeechInput.listen()` сама віддає
     * `Failed(PERMISSION_DENIED)`, якщо дозволу немає (interfaces.md §voice). */
    fun onMicClick() {
        if (_uiState.value.isListening) return
        _uiState.update { it.copy(isListening = true) }
        scope.launch {
            when (val result = speechInput.listen().first()) {
                is SpeechResult.Recognized -> {
                    _uiState.update { it.copy(isListening = false) }
                    beginCapture(result.text)
                }
                is SpeechResult.Failed -> {
                    _uiState.update { it.copy(isListening = false, dialog = HomeDialog.RecognitionFailed) }
                }
            }
        }
    }

    /** Кнопка "Ок" біля поля вводу (історія 12, R19/R20). */
    fun onSubmitText() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        beginCapture(text)
    }

    /** Історія 5, R16: вибір словника в діалозі "українське слово без головної мови". */
    fun onChooseDictionary(language: DictionaryLanguage) {
        val word = (_uiState.value.dialog as? HomeDialog.ChooseDictionary)?.ukrainianWord ?: return
        _uiState.update { it.copy(dialog = null) }
        translateFromUkrainian(word, language)
    }

    /** Cancel у діалозі підтвердження — нічого не зберігає (критерій приймання). */
    fun onCancelConfirmation() {
        _uiState.update { it.copy(dialog = null) }
    }

    /** "Не вдалося розпізнати" — просто закриваємо, слово (якщо було в полі) лишається. */
    fun onDismissRecognitionFailed() {
        _uiState.update { it.copy(dialog = null) }
    }

    /** Закрити діалог мережевої помилки без повтору — слово лишається в полі. */
    fun onDismissTranslationFailed() {
        pendingRetry = null
        _uiState.update { it.copy(dialog = null) }
    }

    /** "Повторити" в діалозі мережевої помилки (R14.2). */
    fun onRetryTranslation() {
        val retry = pendingRetry ?: return
        _uiState.update { it.copy(dialog = null) }
        retry()
    }

    /** Ок у діалозі підтвердження (історія 11, R18) — записує в [DictionaryRepository],
     * створюючи дефолтний словник мови, якщо його ще нема (історія 16, R04). */
    fun onConfirm() {
        val capture = (_uiState.value.dialog as? HomeDialog.Confirm)?.capture ?: return
        scope.launch {
            val dictionary = dictionaryRepository.defaultDictionary(capture.dictionaryLanguage).first()
                ?: dictionaryRepository.createDictionary(capture.dictionaryLanguage, defaultDictionaryName())
            dictionaryRepository.addWord(
                dictionaryId = dictionary.id,
                ukrainian = capture.ukrainian,
                translation1 = capture.translation1,
                translation2 = capture.translation2,
                example = capture.example,
                isExampleGenerated = capture.isExampleGenerated,
            )
            _uiState.update { it.copy(dialog = null, inputText = "") }
        }
    }

    /** Спільна точка входу голосового і текстового шляху (історії 12–13, R19–R21):
     * визначає мову й веде до того самого діалогу підтвердження, незалежно від джерела тексту. */
    private fun beginCapture(word: String) {
        _uiState.update { it.copy(inputText = word) }
        when (val spoken = languageDetector.detect(word)) {
            SpokenLanguage.UKRAINIAN -> {
                val mainLanguage = settingsSnapshot.value.mainLanguage
                if (mainLanguage == MainLanguage.NONE) {
                    _uiState.update { it.copy(dialog = HomeDialog.ChooseDictionary(word)) }
                } else {
                    translateFromUkrainian(word, mainLanguage.toDictionaryLanguage())
                }
            }
            SpokenLanguage.GERMAN -> translateForeignWord(word, Language.GERMAN, DictionaryLanguage.GERMAN)
            SpokenLanguage.ENGLISH -> translateForeignWord(word, Language.ENGLISH, DictionaryLanguage.ENGLISH)
        }
    }

    /** Історії 5–6, 8–9 (R16, R06/R07, R08): українське слово -> до двох перекладів
     * словниковою мовою + приклад тією ж мовою (targetLang службового виклику —
     * дефолтна мова словника, тож один виклик закриває і кандидатів, і приклад). */
    private fun translateFromUkrainian(ukrainianWord: String, dictionaryLanguage: DictionaryLanguage) {
        runTranslation(retry = { translateFromUkrainian(ukrainianWord, dictionaryLanguage) }) {
            val result = translationService.translate(ukrainianWord, Language.UKRAINIAN, dictionaryLanguage.toTranslationLanguage())
            WordCapture(
                ukrainian = ukrainianWord,
                translation1 = result.candidates.getOrElse(0) { ukrainianWord },
                translation2 = result.candidates.getOrNull(1),
                example = result.example,
                isExampleGenerated = result.isExampleGenerated,
                dictionaryLanguage = dictionaryLanguage,
            )
        }
    }

    /** Історія 7 (R17): німецьке/англійське слово -> один переклад українською.
     * Приклад — ОБОВ'ЯЗКОВО мовою словника (R08: "до кожного нового слова... мовою
     * словника", без винятку напрямку; підтверджено шаблонами R08.1 — "Das ist X."/
     * "This is X.", ніколи українською). `TranslationServiceImpl` завжди повертає
     * `example` мовою `targetLang` виклику (закріплено `TranslationServiceImplTest`),
     * тому один виклик word->UKRAINIAN дає лише український відповідник; другий,
     * UKRAINIAN->dictionaryLanguage, — саме за прикладом словниковою мовою.
     * `translation1` лишається введеним/розпізнаним словом з першого виклику —
     * другий виклик його не перезаписує (слово вже словниковою мовою як є). */
    private fun translateForeignWord(word: String, sourceLang: Language, dictionaryLanguage: DictionaryLanguage) {
        runTranslation(retry = { translateForeignWord(word, sourceLang, dictionaryLanguage) }) {
            val toUkrainian = translationService.translate(word, sourceLang, Language.UKRAINIAN)
            val ukrainianValue = toUkrainian.candidates.getOrElse(0) { word }
            val example = translationService.translate(ukrainianValue, Language.UKRAINIAN, dictionaryLanguage.toTranslationLanguage())
            WordCapture(
                ukrainian = ukrainianValue,
                translation1 = word,
                translation2 = null,
                example = example.example,
                isExampleGenerated = example.isExampleGenerated,
                dictionaryLanguage = dictionaryLanguage,
            )
        }
    }

    /** Спільна форма обох гілок перекладу (craft-рев'ю): запам'ятовує [retry] на випадок
     * `TranslationUnavailableException` (R14.2) і перетворює успіх на діалог підтвердження.
     * [buildCapture] може викликати `translationService.translate` кілька разів (гілка
     * "іноземне слово" вище) — весь блок під одним `runCatching`, тож збій будь-якого
     * виклику всередині веде до того самого діалогу "повторити", а `retry()` повторює
     * від початку, включно з уже вдалими викликами (простіше й надійніше часткового
     * відновлення, узгоджено з "не ускладнювати", spec.md п.8 брифа). */
    private fun runTranslation(retry: () -> Unit, buildCapture: suspend () -> WordCapture) {
        pendingRetry = retry
        scope.launch {
            runCatching { buildCapture() }
                .onSuccess { capture ->
                    pendingRetry = null
                    _uiState.update { it.copy(dialog = HomeDialog.Confirm(capture)) }
                }
                .onFailure { error -> handleTranslationFailure(error) }
        }
    }

    private fun handleTranslationFailure(error: Throwable) {
        if (error !is TranslationUnavailableException) throw error
        _uiState.update { it.copy(dialog = HomeDialog.TranslationFailed) }
    }
}

private fun MainLanguage.toDictionaryLanguage(): DictionaryLanguage = when (this) {
    MainLanguage.GERMAN -> DictionaryLanguage.GERMAN
    MainLanguage.ENGLISH -> DictionaryLanguage.ENGLISH
    // beginCapture() уже відгалужує NONE до ChooseDictionary до виклику цієї функції.
    MainLanguage.NONE -> error("mainLanguage=NONE не веде сюди — перевіряється в beginCapture()")
}

private fun DictionaryLanguage.toTranslationLanguage(): Language = when (this) {
    DictionaryLanguage.GERMAN -> Language.GERMAN
    DictionaryLanguage.ENGLISH -> Language.ENGLISH
}
