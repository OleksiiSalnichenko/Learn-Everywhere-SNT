package com.learneverywhere.app.translation

import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Юніт-тести на шов `TranslationService.translate` (interfaces.md
 * §translation, критерії приймання тікета 02) із замоканим
 * `TranslationHttpClient` — без реальної мережі.
 */
class TranslationServiceImplTest {

    private class FakeHttpClient(
        private val response: String? = null,
        private val error: IOException? = null,
    ) : TranslationHttpClient {
        override fun get(url: String): String {
            error?.let { throw it }
            return response!!
        }
    }

    @Test
    fun `translate returns up to two candidates sorted by match quality`() = runBlocking {
        val body = """
            {
              "responseData": {"translatedText": "Hund", "match": 1},
              "matches": [
                {"segment": "собака", "translation": "Köter", "match": 0.7},
                {"segment": "собака", "translation": "Hund", "match": 1}
              ]
            }
        """.trimIndent()
        val service: TranslationService = TranslationServiceImpl(FakeHttpClient(response = body))

        val result = service.translate("собака", Language.UKRAINIAN, Language.GERMAN)

        assertEquals(listOf("Hund", "Köter"), result.candidates)
    }

    @Test
    fun `translate returns single candidate without a second element when only one match exists`() = runBlocking {
        val body = """
            {
              "responseData": {"translatedText": "Hund", "match": 1},
              "matches": [
                {"segment": "собака", "translation": "Hund", "match": 1}
              ]
            }
        """.trimIndent()
        val service: TranslationService = TranslationServiceImpl(FakeHttpClient(response = body))

        val result = service.translate("собака", Language.UKRAINIAN, Language.GERMAN)

        assertEquals(listOf("Hund"), result.candidates)
    }

    @Test
    fun `translate falls back to a templated example when no match sentence is available`() = runBlocking {
        val body = """
            {
              "responseData": {"translatedText": "Hund", "match": 1},
              "matches": [
                {"segment": "собака", "translation": "Hund", "match": 1}
              ]
            }
        """.trimIndent()
        val service: TranslationService = TranslationServiceImpl(FakeHttpClient(response = body))

        val result = service.translate("собака", Language.UKRAINIAN, Language.GERMAN)

        assertEquals("Das ist Hund.", result.example)
        assertEquals(true, result.isExampleGenerated)
    }

    @Test
    fun `translate uses a full-sentence match as example when available`() = runBlocking {
        val body = """
            {
              "responseData": {"translatedText": "Hund", "match": 1},
              "matches": [
                {"segment": "собака", "translation": "Hund", "match": 1},
                {"segment": "Я маю собаку.", "translation": "Ich habe einen Hund.", "match": 0.9}
              ]
            }
        """.trimIndent()
        val service: TranslationService = TranslationServiceImpl(FakeHttpClient(response = body))

        val result = service.translate("собака", Language.UKRAINIAN, Language.GERMAN)

        assertEquals("Ich habe einen Hund.", result.example)
        assertEquals(false, result.isExampleGenerated)
    }

    @Test(expected = TranslationUnavailableException::class)
    fun `translate wraps a network failure into a domain exception`() {
        val service: TranslationService = TranslationServiceImpl(FakeHttpClient(error = IOException("timeout")))

        runBlocking { service.translate("собака", Language.UKRAINIAN, Language.GERMAN) }
    }
}
