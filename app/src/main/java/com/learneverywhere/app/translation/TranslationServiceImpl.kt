package com.learneverywhere.app.translation

import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * Реалізація `TranslationService` через MyMemory Translation API
 * (spec.md §«Рішення щодо реалізації → Переклад слів»). HTTP-виклик
 * делегується в [TranslationHttpClient] — саме тут проходить межа
 * мокання в тестах.
 */
class TranslationServiceImpl(
    private val httpClient: TranslationHttpClient,
) : TranslationService {

    private data class Match(val segment: String, val translation: String, val score: Double)

    override suspend fun translate(word: String, sourceLang: Language, targetLang: Language): TranslationResult =
        withContext(Dispatchers.IO) {
            val body = try {
                httpClient.get(buildUrl(word, sourceLang, targetLang))
            } catch (e: IOException) {
                throw TranslationUnavailableException("Не вдалося звернутися до сервісу перекладу", e)
            }
            parseResponse(body, word, targetLang)
        }

    private fun buildUrl(word: String, sourceLang: Language, targetLang: Language): String {
        val encodedWord = URLEncoder.encode(word, "UTF-8")
        return "https://api.mymemory.translated.net/get" +
            "?q=$encodedWord&langpair=${sourceLang.isoCode}|${targetLang.isoCode}"
    }

    private fun parseResponse(body: String, word: String, targetLang: Language): TranslationResult {
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw TranslationUnavailableException("Сервіс перекладу повернув неочікувану відповідь", e)
        }

        val allMatches = mutableListOf<Match>()
        json.optJSONArray("matches")?.let { matches ->
            for (i in 0 until matches.length()) {
                val m = matches.optJSONObject(i) ?: continue
                val translation = m.optString("translation").trim()
                if (translation.isEmpty()) continue
                allMatches += Match(
                    segment = m.optString("segment").trim(),
                    translation = translation,
                    score = m.optDouble("match", 0.0),
                )
            }
        }

        // Кандидати перекладу — тільки збіги, чий вихідний текст дорівнює
        // самому слову (не приклад речення з ним).
        val wordMatches = allMatches.filter { it.segment.equals(word, ignoreCase = true) }
        val candidates = wordMatches
            .sortedByDescending { it.score }
            .map { it.translation }
            .distinctBy { it.lowercase() }
            .take(2)
            .ifEmpty {
                val fallback = json.optJSONObject("responseData")?.optString("translatedText")?.trim()
                if (fallback.isNullOrEmpty()) emptyList() else listOf(fallback)
            }

        if (candidates.isEmpty()) {
            throw TranslationUnavailableException("Сервіс перекладу не повернув жодного варіанту перекладу")
        }

        // Приклад речення — збіг, чий вихідний текст є повним реченням
        // (не самим словом), R08.
        val exampleMatch = allMatches
            .filter { isSentence(it.segment, word) }
            .maxByOrNull { it.score }

        val example: String
        val isExampleGenerated: Boolean
        if (exampleMatch != null) {
            example = exampleMatch.translation
            isExampleGenerated = false
        } else {
            example = templatePhrase(candidates.first(), targetLang)
            isExampleGenerated = true
        }

        return TranslationResult(candidates = candidates, example = example, isExampleGenerated = isExampleGenerated)
    }

    private fun isSentence(segment: String, word: String): Boolean {
        if (segment.isBlank() || segment.equals(word, ignoreCase = true)) return false
        return segment.trim().split(Regex("\\s+")).size >= 2
    }

    private fun templatePhrase(translatedWord: String, targetLang: Language): String = when (targetLang) {
        Language.GERMAN -> "Das ist $translatedWord."
        Language.ENGLISH -> "This is $translatedWord."
        Language.UKRAINIAN -> "Це $translatedWord."
    }
}
