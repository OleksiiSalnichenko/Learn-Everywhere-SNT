package com.learneverywhere.app.playback

import com.learneverywhere.app.data.model.WordEntry

/**
 * Один крок конечного автомата програвання (spec.md §«Фонове програвання»):
 * `SpeakUkrainian(×N) → WaitBeforeTranslation → SpeakTranslation(×M) →
 * [WaitBeforeExample → SpeakExample] → PauseBeforeNext → наступне слово / стоп`.
 *
 * Кожна фаза несе все, що потрібно [PlaybackEngine], щоб порахувати наступну
 * (кількість/номер повтору), тож автомат не тримає прихованого мутабельного
 * стану повтору — лише позицію в черзі слів ([PlaybackEngine] §wordIndex/order).
 */
sealed interface PlaybackPhase {

    /** Слово поточної фази; `null` для термінальних станів без слова. */
    val word: WordEntry?

    data class SpeakUkrainian(override val word: WordEntry, val repeat: Int, val totalRepeats: Int) : PlaybackPhase

    data class PauseBetweenUkrainianRepeats(
        override val word: WordEntry,
        val seconds: Int,
        val nextRepeat: Int,
        val totalRepeats: Int,
    ) : PlaybackPhase

    data class WaitBeforeTranslation(override val word: WordEntry, val seconds: Int) : PlaybackPhase

    data class SpeakTranslation(override val word: WordEntry, val repeat: Int, val totalRepeats: Int) : PlaybackPhase

    data class PauseBetweenTranslationRepeats(
        override val word: WordEntry,
        val seconds: Int,
        val nextRepeat: Int,
        val totalRepeats: Int,
    ) : PlaybackPhase

    /** Активна лише коли `AppSettings.includeExampleInPlayback` (R33). */
    data class WaitBeforeExample(override val word: WordEntry, val seconds: Int) : PlaybackPhase

    data class SpeakExample(override val word: WordEntry) : PlaybackPhase

    data class PauseBeforeNext(override val word: WordEntry, val seconds: Int) : PlaybackPhase

    /** Дефолтний словник порожній — плей неактивний (критерій приймання тікета 08). */
    data object Empty : PlaybackPhase {
        override val word: WordEntry? = null
    }

    /** Черга дійшла до кінця і `loop` вимкнено — програвання зупинено. */
    data object Finished : PlaybackPhase {
        override val word: WordEntry? = null
    }
}
