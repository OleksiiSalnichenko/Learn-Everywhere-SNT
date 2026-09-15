package com.learneverywhere.app.voice

import com.learneverywhere.app.data.model.InterfaceLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Юніт-тест на шов `LanguageDetector` (interfaces.md §voice) — детермінований,
 * без Android-залежностей (тікет 03, критерій приймання: "detect(Hallo) ->
 * німецька, detect(Hello) -> англійська, detect(Привіт) -> українська").
 * Значення очікувань узяті прямо з тексту тікета/spec.md, не з реалізації.
 */
class LanguageDetectorTest {

    private fun detectorWithInterfaceLanguage(language: InterfaceLanguage) =
        LanguageDetector(interfaceLanguage = { language })

    @Test
    fun `detect returns Ukrainian for Cyrillic text`() {
        val detector = detectorWithInterfaceLanguage(InterfaceLanguage.ENGLISH)
        assertEquals(SpokenLanguage.UKRAINIAN, detector.detect("Привіт"))
    }

    @Test
    fun `detect returns German for a word from the ticket's German example`() {
        val detector = detectorWithInterfaceLanguage(InterfaceLanguage.ENGLISH)
        assertEquals(SpokenLanguage.GERMAN, detector.detect("Hallo"))
    }

    @Test
    fun `detect returns English for a word from the ticket's English example`() {
        val detector = detectorWithInterfaceLanguage(InterfaceLanguage.GERMAN)
        assertEquals(SpokenLanguage.ENGLISH, detector.detect("Hello"))
    }

    @Test
    fun `detect recognizes German function words from spec (der die das und ist)`() {
        val detector = detectorWithInterfaceLanguage(InterfaceLanguage.ENGLISH)
        assertEquals(SpokenLanguage.GERMAN, detector.detect("Das ist ein Haus"))
    }

    @Test
    fun `detect recognizes English function words from spec (the and is a)`() {
        val detector = detectorWithInterfaceLanguage(InterfaceLanguage.GERMAN)
        assertEquals(SpokenLanguage.ENGLISH, detector.detect("This is a test"))
    }

    @Test
    fun `detect falls back to current interface language when no marker matches`() {
        val detector = detectorWithInterfaceLanguage(InterfaceLanguage.GERMAN)
        // "Xylophon" — латиниця, але жодне службове слово зі списку не збігається.
        assertEquals(SpokenLanguage.GERMAN, detector.detect("Xylophon"))
    }

    @Test
    fun `detect fallback follows interface language even when it changes`() {
        val detector = detectorWithInterfaceLanguage(InterfaceLanguage.ENGLISH)
        assertEquals(SpokenLanguage.ENGLISH, detector.detect("Xylophon"))
    }
}
