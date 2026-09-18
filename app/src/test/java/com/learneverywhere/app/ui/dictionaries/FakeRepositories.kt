package com.learneverywhere.app.ui.dictionaries

import android.net.Uri
import com.learneverywhere.app.data.model.Dictionary
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.WordEntry
import com.learneverywhere.app.data.repository.DictionaryRepository
import com.learneverywhere.app.settings.AppSettings
import com.learneverywhere.app.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory тестові дублери `DictionaryRepository`/`SettingsRepository` —
 * тестуємо `DictionariesViewModel` на публічній межі цих репозиторіїв
 * (interfaces.md), не на Room/Robolectric: логіка вкладок і лічильника слів
 * не залежить від бази, тому справжня БД тут зайва вага.
 */
internal class FakeDictionaryRepository : DictionaryRepository {

    private var nextId = 1L
    private val dictionariesState = MutableStateFlow<List<Dictionary>>(emptyList())
    private val wordsState = MutableStateFlow<Map<Long, List<WordEntry>>>(emptyMap())

    override fun dictionaries(language: DictionaryLanguage): Flow<List<Dictionary>> =
        dictionariesState.map { list -> list.filter { it.language == language } }

    override fun defaultDictionary(language: DictionaryLanguage): Flow<Dictionary?> =
        dictionaries(language).map { list -> list.firstOrNull { it.isDefault } }

    override suspend fun createDictionary(language: DictionaryLanguage, name: String): Dictionary {
        val isFirstForLanguage = dictionariesState.value.none { it.language == language }
        val dictionary = Dictionary(id = nextId++, language = language, name = name, isDefault = isFirstForLanguage)
        dictionariesState.value = dictionariesState.value + dictionary
        return dictionary
    }

    override suspend fun setDefault(id: Long) {
        val language = dictionariesState.value.first { it.id == id }.language
        dictionariesState.value = dictionariesState.value.map { dictionary ->
            when {
                dictionary.language != language -> dictionary
                dictionary.id == id -> dictionary.copy(isDefault = true)
                else -> dictionary.copy(isDefault = false)
            }
        }
    }

    override suspend fun rename(id: Long, name: String) {
        dictionariesState.value = dictionariesState.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun deleteDictionary(id: Long) {
        dictionariesState.value = dictionariesState.value.filterNot { it.id == id }
    }

    override fun words(dictionaryId: Long): Flow<List<WordEntry>> =
        wordsState.map { it[dictionaryId].orEmpty() }

    override suspend fun addWord(
        dictionaryId: Long,
        ukrainian: String,
        translation1: String,
        translation2: String?,
        example: String,
        isExampleGenerated: Boolean,
    ): WordEntry {
        val entry = WordEntry(
            id = wordsState.value.values.sumOf { it.size }.toLong() + 1,
            dictionaryId = dictionaryId,
            ukrainian = ukrainian,
            translation1 = translation1,
            translation2 = translation2,
            example = example,
            isExampleGenerated = isExampleGenerated,
        )
        wordsState.value = wordsState.value + (dictionaryId to (wordsState.value[dictionaryId].orEmpty() + entry))
        return entry
    }

    override suspend fun updateWord(word: WordEntry) {
        wordsState.value = wordsState.value.mapValues { (_, words) ->
            words.map { if (it.id == word.id) word else it }
        }
    }

    override suspend fun deleteWord(id: Long) {
        wordsState.value = wordsState.value.mapValues { (_, words) -> words.filterNot { it.id == id } }
    }

    override suspend fun exportToJson(dictionaryId: Long): Uri =
        throw UnsupportedOperationException("не потрібно для тесту DictionariesViewModel")

    override suspend fun importFromJson(uri: Uri, language: DictionaryLanguage): Dictionary =
        throw UnsupportedOperationException("не потрібно для тесту DictionariesViewModel")

    override suspend fun detectImportLanguage(uri: Uri): DictionaryLanguage? =
        throw UnsupportedOperationException("не потрібно для тесту DictionariesViewModel")

    override suspend fun ensureDefaultDictionaries() {
        // Не потрібно для тестів цього екрана — ViewModel лише читає й перемикає дефолт.
    }
}

internal class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }

    override suspend fun resetToDefaults() {
        state.value = AppSettings()
    }
}
