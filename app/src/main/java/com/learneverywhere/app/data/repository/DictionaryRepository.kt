package com.learneverywhere.app.data.repository

import android.net.Uri
import com.learneverywhere.app.data.model.Dictionary
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.WordEntry
import kotlinx.coroutines.flow.Flow

/**
 * Публічна межа модуля `data` (зафіксована в `interfaces.md`). Ховає схему
 * Room-таблиць і формат JSON-файлу — споживачі (ViewModel-і) працюють лише
 * з доменними моделями `Dictionary` / `WordEntry`.
 */
interface DictionaryRepository {

    fun dictionaries(language: DictionaryLanguage): Flow<List<Dictionary>>

    fun defaultDictionary(language: DictionaryLanguage): Flow<Dictionary?>

    suspend fun createDictionary(language: DictionaryLanguage, name: String): Dictionary

    suspend fun setDefault(id: Long)

    suspend fun rename(id: Long, name: String)

    suspend fun deleteDictionary(id: Long)

    fun words(dictionaryId: Long): Flow<List<WordEntry>>

    suspend fun addWord(
        dictionaryId: Long,
        ukrainian: String,
        translation1: String,
        translation2: String?,
        example: String,
        isExampleGenerated: Boolean,
    ): WordEntry

    suspend fun updateWord(word: WordEntry)

    suspend fun deleteWord(id: Long)

    suspend fun exportToJson(dictionaryId: Long): Uri

    suspend fun importFromJson(uri: Uri, language: DictionaryLanguage): Dictionary

    /**
     * Гарантує, що для GERMAN і ENGLISH існує дефолтний словник "Основний"
     * (історії 16, 17 / R04, R04.1). Публічна межа, якої не було в
     * первісному контракті `interfaces.md` — додана в тікеті 01, бо без неї
     * нема кому створити дефолтні словники при першому запуску.
     * Викликається один раз при старті застосунку (`LearnEverywhereApplication`).
     */
    suspend fun ensureDefaultDictionaries()
}
