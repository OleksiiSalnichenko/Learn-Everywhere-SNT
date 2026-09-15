package com.learneverywhere.app.voice

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Юніт-тест на шов `Speaker`/`SpeechInput` (interfaces.md §voice) —
 * критерій приймання тікета 03: "Speaker.speak(...) вимовляє текст
 * правильною локаллю для кожної з трьох мов". Сама озвучка через
 * `android.speech.tts.TextToSpeech` у юніт-тесті не перевіряється (потрібен
 * реальний пристрій/двигун TTS), але вибір локалі — детермінований
 * лаппінг, який і фіксує цей тест: значення взяті дослівно зі spec.md
 * §«Озвучення» (uk-UA / de-DE / en-US).
 */
class SpokenLanguageTest {

    @Test
    fun `Ukrainian maps to uk-UA`() {
        assertEquals(Locale("uk", "UA"), SpokenLanguage.UKRAINIAN.toLocale())
    }

    @Test
    fun `German maps to de-DE`() {
        assertEquals(Locale("de", "DE"), SpokenLanguage.GERMAN.toLocale())
    }

    @Test
    fun `English maps to en-US`() {
        assertEquals(Locale("en", "US"), SpokenLanguage.ENGLISH.toLocale())
    }
}
