package com.learneverywhere.app.playback

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.WordEntry
import com.learneverywhere.app.data.repository.DictionaryRepository
import com.learneverywhere.app.settings.AppSettings
import com.learneverywhere.app.settings.SettingsRepository
import com.learneverywhere.app.voice.Speaker
import com.learneverywhere.app.voice.SpokenLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Публічна межа модуля `playback` (interfaces.md §playback, spec.md
 * §«Фонове програвання»): `.start(dictId)`, `.pause()`, `.resume()`, `.stop()`,
 * `.skipNext()`, `currentWord`, `isPlaying`.
 *
 * Звичайний клас (не сам `Service`) — Android не дає конструювати `Service`
 * викликом конструктора, тож вся бізнес-логіка (черга [PlaybackEngine], TTS
 * через [Speaker], паузи як `delay`) живе тут і тестується без Robolectric
 * фейковими репозиторіями/[Speaker] (як `DictionariesViewModel`, тікет 05).
 * [PlaybackMediaService] — тонка Android-обгортка навколо *цього* інстансу:
 * foreground-сервіс, `MediaSession`, audio focus. Служба не знає нічого про
 * чергу — лише відбиває [currentWord]/[isPlaying] і транслює команди системи
 * (плей/пауза/скіп, втрата фокуса) назад у виклики цього класу. Оскільки
 * `Service` створює ОС, а не викликач, міст між двома — [active]
 * (задокументовано нижче, свідоме відхилення від "просто підклас
 * MediaSessionService" зі спеки, щоб `.start(dictId)` лишався звичайним
 * викликом методу, як каже контракт).
 */
class PlaybackController(
    private val context: Context,
    private val dictionaryRepository: DictionaryRepository,
    private val settingsRepository: SettingsRepository,
    private val speaker: Speaker,
    private val scope: CoroutineScope,
) : PlaybackActions {

    private val _currentWord = MutableStateFlow<WordEntry?>(null)
    override val currentWord: StateFlow<WordEntry?> = _currentWord.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var engine: PlaybackEngine? = null
    private var translationLanguage: DictionaryLanguage = DictionaryLanguage.GERMAN
    private var driverJob: Job? = null

    /** Запускає програвання дефолтного/обраного словника [dictionaryId] з початку черги. */
    override fun start(dictionaryId: Long) {
        driverJob?.cancel()
        driverJob = scope.launch {
            val words = dictionaryRepository.words(dictionaryId).first()
            val settings = settingsRepository.settings.first()
            translationLanguage = resolveLanguage(dictionaryId)
            val newEngine = PlaybackEngine(words, settings)
            engine = newEngine

            if (newEngine.phase == PlaybackPhase.Empty) {
                // Порожній словник — критерій приймання тікета 08: без падіння, просто нема що грати.
                _currentWord.value = null
                _isPlaying.value = false
                return@launch
            }

            active = this@PlaybackController
            ContextCompat.startForegroundService(context, Intent(context, PlaybackMediaService::class.java))
            _isPlaying.value = true
            runLoop(newEngine)
        }
    }

    private suspend fun resolveLanguage(dictionaryId: Long): DictionaryLanguage {
        val germanDictionaries = dictionaryRepository.dictionaries(DictionaryLanguage.GERMAN).first()
        return if (germanDictionaries.any { it.id == dictionaryId }) {
            DictionaryLanguage.GERMAN
        } else {
            DictionaryLanguage.ENGLISH
        }
    }

    private suspend fun runLoop(playbackEngine: PlaybackEngine) {
        var phase = playbackEngine.phase
        while (true) {
            if (phase is PlaybackPhase.Finished || phase is PlaybackPhase.Empty) {
                stop()
                return
            }
            _currentWord.value = phase.word
            // Пауза діє на межі фаз — фаза, що вже виконується, договорює/дочікує до кінця
            // (Speaker не підтримує переривання одного вислову без вимкнення всього рушія).
            isPlaying.first { it }
            executePhase(phase)
            phase = playbackEngine.advance()
        }
    }

    private suspend fun executePhase(phase: PlaybackPhase) {
        when (phase) {
            is PlaybackPhase.SpeakUkrainian -> speak(phase.word.ukrainian, SpokenLanguage.UKRAINIAN)
            is PlaybackPhase.SpeakTranslation -> speak(phase.word.translation1, translationLanguage.toSpokenLanguage())
            is PlaybackPhase.SpeakExample -> speak(phase.word.example, translationLanguage.toSpokenLanguage())
            is PlaybackPhase.PauseBetweenUkrainianRepeats -> delay(phase.seconds * 1000L)
            is PlaybackPhase.WaitBeforeTranslation -> delay(phase.seconds * 1000L)
            is PlaybackPhase.PauseBetweenTranslationRepeats -> delay(phase.seconds * 1000L)
            is PlaybackPhase.WaitBeforeExample -> delay(phase.seconds * 1000L)
            is PlaybackPhase.PauseBeforeNext -> delay(phase.seconds * 1000L)
            PlaybackPhase.Empty, PlaybackPhase.Finished -> Unit
        }
    }

    private suspend fun speak(text: String, locale: SpokenLanguage) = suspendCancellableCoroutine { continuation ->
        speaker.speak(text, locale) {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    /** Ставить чергу на паузу (в т.ч. викликається з [PlaybackMediaService] при втраті audio focus, R45.1). */
    override fun pause() {
        _isPlaying.value = false
    }

    /** Знімає з паузи — no-op, якщо чергу вже зупинено (нема що відновлювати). */
    override fun resume() {
        if (engine != null) _isPlaying.value = true
    }

    override fun stop() {
        driverJob?.cancel()
        driverJob = null
        engine = null
        _isPlaying.value = false
        _currentWord.value = null
        if (active === this) active = null
        context.stopService(Intent(context, PlaybackMediaService::class.java))
    }

    /** Пропускає решту поточного слова й одразу переходить до наступного (кнопка "пропустити", A02). */
    fun skipNext() {
        val currentEngine = engine ?: return
        val next = currentEngine.skipToNextWord()
        if (next is PlaybackPhase.Finished) {
            stop()
        } else {
            _currentWord.value = next.word
        }
    }

    private fun DictionaryLanguage.toSpokenLanguage(): SpokenLanguage = when (this) {
        DictionaryLanguage.GERMAN -> SpokenLanguage.GERMAN
        DictionaryLanguage.ENGLISH -> SpokenLanguage.ENGLISH
    }

    companion object {
        /**
         * Живий інстанс, поки триває програвання — міст до [PlaybackMediaService],
         * якого створює ОС і який тому не може отримати цей об'єкт через конструктор.
         * `null`, якщо нічого не грає (мініплеєр орієнтується саме на це, критерій
         * приймання "доступний, поки сервіс живий").
         */
        var active: PlaybackController? = null
            private set
    }
}
