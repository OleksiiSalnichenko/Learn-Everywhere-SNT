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

internal class FakeTranslationService : TranslationService {

    data class Call(val word: String, val sourceLang: Language, val targetLang: Language)

    val calls = mutableListOf<Call>()

    /** Черга запланованих відповідей — `Result.success(TranslationResult(...))` або
     * `Result.failure(TranslationUnavailableException(...))`, по одній на виклик [translate]. */
    private val queue = ArrayDeque<Result<TranslationResult>>()

    fun enqueueSuccess(result: TranslationResult) {
        queue.addLast(Result.success(result))
    }

    fun enqueueFailure(exception: TranslationUnavailableException = TranslationUnavailableException("мережа недоступна")) {
        queue.addLast(Result.failure(exception))
    }

    override suspend fun translate(word: String, sourceLang: Language, targetLang: Language): TranslationResult {
        calls += Call(word, sourceLang, targetLang)
        val next = queue.removeFirstOrNull()
            ?: error("FakeTranslationService: немає запланованої відповіді — виклич enqueueSuccess/enqueueFailure перед translate()")
        return next.getOrThrow()
    }
}
