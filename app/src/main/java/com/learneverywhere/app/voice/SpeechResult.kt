package com.learneverywhere.app.voice

import android.speech.SpeechRecognizer

/**
 * Результат одного циклу [SpeechInput.listen] (R14). Один виклик -> рівно
 * один [SpeechResult], потік після нього завершується (не безкінечне
 * мовлення).
 */
sealed interface SpeechResult {
    data class Recognized(val text: String) : SpeechResult
    data class Failed(val reason: SpeechFailureReason) : SpeechResult
}

/**
 * Чому розпізнавання не вдалося (R14.1: "тиша, шум, незрозуміла мова") —
 * окремий тип результату, а не необроблений виняток з `SpeechRecognizer`.
 */
enum class SpeechFailureReason {
    NOT_UNDERSTOOD,
    PERMISSION_DENIED,
    AUDIO_ERROR,
    NETWORK_ERROR,
    OTHER,
}

/**
 * Мапінг кодів помилок `android.speech.SpeechRecognizer` (`ERROR_*` —
 * прості `Int`-константи, доступні в юніт-тестах без Robolectric) у
 * [SpeechFailureReason]. Винесено в чисту функцію окремо від
 * [AndroidSpeechInput], щоб критерій приймання "невдале розпізнавання
 * повертає окремий результат-помилку" був детерміновано тестований.
 */
fun mapSpeechRecognizerError(errorCode: Int): SpeechFailureReason = when (errorCode) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
    -> SpeechFailureReason.NOT_UNDERSTOOD

    SpeechRecognizer.ERROR_AUDIO -> SpeechFailureReason.AUDIO_ERROR

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
    -> SpeechFailureReason.NETWORK_ERROR

    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailureReason.PERMISSION_DENIED

    else -> SpeechFailureReason.OTHER
}
