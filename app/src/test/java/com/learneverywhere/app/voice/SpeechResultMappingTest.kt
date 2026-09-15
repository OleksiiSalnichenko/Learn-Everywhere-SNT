package com.learneverywhere.app.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Юніт-тест на `mapSpeechRecognizerError` — шов, що реалізує критерій
 * приймання тікета 03 "невдале розпізнавання (тиша/шум/помилка) повертає
 * окремий результат-помилку, а не кидає необроблений виняток" (R14.1).
 * `SpeechRecognizer.ERROR_*` — Int-константи SDK, доступні без Robolectric.
 */
class SpeechResultMappingTest {

    @Test
    fun `no match or timeout means not understood (silence, noise)`() {
        assertEquals(SpeechFailureReason.NOT_UNDERSTOOD, mapSpeechRecognizerError(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(SpeechFailureReason.NOT_UNDERSTOOD, mapSpeechRecognizerError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun `network errors are reported as network failures`() {
        assertEquals(SpeechFailureReason.NETWORK_ERROR, mapSpeechRecognizerError(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(SpeechFailureReason.NETWORK_ERROR, mapSpeechRecognizerError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
    }

    @Test
    fun `permission errors are reported as permission denied`() {
        assertEquals(
            SpeechFailureReason.PERMISSION_DENIED,
            mapSpeechRecognizerError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
    }

    @Test
    fun `audio errors are reported distinctly from other errors`() {
        assertEquals(SpeechFailureReason.AUDIO_ERROR, mapSpeechRecognizerError(SpeechRecognizer.ERROR_AUDIO))
        assertEquals(SpeechFailureReason.OTHER, mapSpeechRecognizerError(SpeechRecognizer.ERROR_CLIENT))
    }
}
