package com.learneverywhere.app.playback

import com.learneverywhere.app.data.model.WordEntry
import com.learneverywhere.app.settings.AppSettings

/**
 * Чистий (без Android-залежностей) конечний автомат і черга програвання —
 * найважливіший шов тікета 08 (interfaces.md §playback, spec.md §«Фонове
 * програвання»). [PlaybackController] — тонка Android-обгортка: виконує
 * кожну фазу (TTS-виклик через `Speaker` або таймер-пауза) і викликає
 * [advance] чи [skipToNextWord], коли фаза реально завершилась.
 *
 * Порядок слів фіксується один раз при створенні (і за кожним новим проходом
 * при `loop`, якщо `shuffle` — див. [orderForNewPass]) — сам автомат не читає
 * `AppSettings` під час гри, лише при обчисленні наступної фази.
 */
class PlaybackEngine(
    private val words: List<WordEntry>,
    private val settings: AppSettings,
    private val shuffler: (List<WordEntry>) -> List<WordEntry> = { it.shuffled() },
) {

    private var order: List<WordEntry> = orderForNewPass()
    private var wordIndex: Int = 0

    var phase: PlaybackPhase = initialPhase()
        private set

    private fun orderForNewPass(): List<WordEntry> = if (settings.shuffle) shuffler(words) else words

    private fun initialPhase(): PlaybackPhase =
        if (words.isEmpty()) PlaybackPhase.Empty else firstPhaseFor(order[0])

    private fun firstPhaseFor(word: WordEntry): PlaybackPhase =
        PlaybackPhase.SpeakUkrainian(word, repeat = 1, totalRepeats = settings.ukrainianRepeatCount)

    /** Просуває автомат на один крок — фаза [phase] справді завершилась. */
    fun advance(): PlaybackPhase {
        phase = computeNext(phase)
        return phase
    }

    /** Пропускає решту поточного слова (всі повтори/паузи) й переходить до наступного. */
    fun skipToNextWord(): PlaybackPhase {
        if (phase == PlaybackPhase.Empty || phase == PlaybackPhase.Finished) return phase
        phase = startNextWordOrFinish()
        return phase
    }

    private fun computeNext(current: PlaybackPhase): PlaybackPhase = when (current) {
        is PlaybackPhase.SpeakUkrainian ->
            if (current.repeat < current.totalRepeats) {
                PlaybackPhase.PauseBetweenUkrainianRepeats(
                    word = current.word,
                    seconds = settings.ukrainianRepeatPause,
                    nextRepeat = current.repeat + 1,
                    totalRepeats = current.totalRepeats,
                )
            } else {
                PlaybackPhase.WaitBeforeTranslation(current.word, settings.translationDelay)
            }

        is PlaybackPhase.PauseBetweenUkrainianRepeats ->
            PlaybackPhase.SpeakUkrainian(current.word, current.nextRepeat, current.totalRepeats)

        is PlaybackPhase.WaitBeforeTranslation ->
            PlaybackPhase.SpeakTranslation(current.word, repeat = 1, totalRepeats = settings.translationRepeatCount)

        is PlaybackPhase.SpeakTranslation ->
            if (current.repeat < current.totalRepeats) {
                PlaybackPhase.PauseBetweenTranslationRepeats(
                    word = current.word,
                    seconds = settings.translationRepeatPause,
                    nextRepeat = current.repeat + 1,
                    totalRepeats = current.totalRepeats,
                )
            } else if (settings.includeExampleInPlayback) {
                PlaybackPhase.WaitBeforeExample(current.word, settings.examplePause)
            } else {
                PlaybackPhase.PauseBeforeNext(current.word, settings.nextWordPause)
            }

        is PlaybackPhase.PauseBetweenTranslationRepeats ->
            PlaybackPhase.SpeakTranslation(current.word, current.nextRepeat, current.totalRepeats)

        is PlaybackPhase.WaitBeforeExample ->
            PlaybackPhase.SpeakExample(current.word)

        is PlaybackPhase.SpeakExample ->
            PlaybackPhase.PauseBeforeNext(current.word, settings.nextWordPause)

        is PlaybackPhase.PauseBeforeNext ->
            startNextWordOrFinish()

        PlaybackPhase.Empty, PlaybackPhase.Finished -> current
    }

    private fun startNextWordOrFinish(): PlaybackPhase {
        wordIndex++
        if (wordIndex >= order.size) {
            if (!settings.loop) return PlaybackPhase.Finished
            order = orderForNewPass()
            wordIndex = 0
        }
        return firstPhaseFor(order[wordIndex])
    }
}
