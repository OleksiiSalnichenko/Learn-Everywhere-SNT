package com.learneverywhere.app.ui.home

import com.learneverywhere.app.translation.Language
import com.learneverywhere.app.translation.TranslationResult
import com.learneverywhere.app.translation.TranslationService
import com.learneverywhere.app.translation.TranslationUnavailableException
import com.learneverywhere.app.voice.SpeechInput
import com.learneverywhere.app.voice.SpeechResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory тестові дублери `SpeechInput`/`TranslationService` для
 * `HomeViewModelTest` — той самий підхід, що `FakeDictionaryRepository`/
 * `FakeSettingsRepository` з `ui.dictionaries.FakeRepositories` (тікет 05),
 * які цей тест теж перевикористовує (`internal` — видимі всюди в модулі).
 */
internal class FakeSpeechInput : SpeechInput {

    private val queue = ArrayDeque<SpeechResult>()

    fun enqueue(result: SpeechResult) {
        queue.addLast(result)
    }

    override fun listen(): Flow<SpeechResult> {
        val next = queue.removeFirstOrNull()
            ?: error("FakeSpeechInput: немає запланованого результату — виклич enqueue() перед listen()")
        return flowOf(next)
    }
}

/**
 * `example` тут НЕ береться з даних, підготовлених тестом — він рахується самим
 * фейком з фактичного `targetLang` виклику, так само, як `TranslationServiceImpl`
 * справді поводиться (доказ — `TranslationServiceImplTest`: приклад завжди мовою
 * `targetLang`, не мовою слова). Це навмисно: якщо код під тестом випадково
 * переплутає напрям виклику (наприклад, попросить приклад українською замість
 * словникової мови — саме такий баг ловило рев'ю тікета 06), канонічний
 * фейк-приклад вийде в неправильній мові і тест-асерція на `example` це впіймає;
 * фейк, що сліпо повертає заздалегідь підготовлений рядок незалежно від `targetLang`,
 * такий клас помилок не ловить.
 */
internal class FakeTranslationService : TranslationService {

    data class Call(val word: String, val sourceLang: Language, val targetLang: Language)

    val calls = mutableListOf<Call>()

    private data class QueuedResponse(val candidates: List<String>, val isExampleGenerated: Boolean)

    /** Черга запланованих відповідей — кандидати й прапорець генерованого прикладу,
     * по одній на виклик [translate]; `example` фейк рахує сам (див. коментар класу). */
    private val queue = ArrayDeque<Result<QueuedResponse>>()

    fun enqueueSuccess(candidates: List<String>, isExampleGenerated: Boolean = false) {
        queue.addLast(Result.success(QueuedResponse(candidates, isExampleGenerated)))
    }

    fun enqueueFailure(exception: TranslationUnavailableException = TranslationUnavailableException("мережа недоступна")) {
        queue.addLast(Result.failure(exception))
    }

    override suspend fun translate(word: String, sourceLang: Language, targetLang: Language): TranslationResult {
        calls += Call(word, sourceLang, targetLang)
        val next = queue.removeFirstOrNull()
            ?: error("FakeTranslationService: немає запланованої відповіді — виклич enqueueSuccess/enqueueFailure перед translate()")
        val response = next.getOrThrow()
        return TranslationResult(
            candidates = response.candidates,
            example = exampleIn(targetLang, response.candidates.firstOrNull() ?: word),
            isExampleGenerated = response.isExampleGenerated,
        )
    }
}

/** Той самий шаблон, що `TranslationServiceImpl.templatePhrase` (тікет 02) — навмисний
 * дублікат-мнемонік у тестовому фейку, щоб приклад однозначно "видавав" мову виклику. */
private fun exampleIn(language: Language, translatedWord: String): String = when (language) {
    Language.GERMAN -> "Das ist $translatedWord."
    Language.ENGLISH -> "This is $translatedWord."
    Language.UKRAINIAN -> "Це $translatedWord."
}
