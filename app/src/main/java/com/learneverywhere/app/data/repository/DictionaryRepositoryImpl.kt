package com.learneverywhere.app.data.repository

import android.content.Context
import android.net.Uri
import com.learneverywhere.app.R
import com.learneverywhere.app.data.db.DictionaryDao
import com.learneverywhere.app.data.db.DictionaryEntity
import com.learneverywhere.app.data.db.WordEntryDao
import com.learneverywhere.app.data.db.WordEntryEntity
import com.learneverywhere.app.data.model.Dictionary
import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.WordEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DictionaryRepositoryImpl(
    private val dictionaryDao: DictionaryDao,
    private val wordEntryDao: WordEntryDao,
    private val context: Context,
) : DictionaryRepository {

    /**
     * Ім'я дефолтного словника, який система створює сама, коли для мови ще
     * немає жодного (історії 16/17). Текст видимий користувачу (заголовок
     * словника в UI), тому йде через `res/values/strings.xml` разом з
     * перекладами (`values-en`, `values-de`), а не хардкодиться — користувач
     * може перейменувати словник будь-коли (історія 41), це лише початкове
     * значення.
     */
    private val defaultDictionaryName: String
        get() = context.getString(R.string.default_dictionary_name)

    override fun dictionaries(language: DictionaryLanguage): Flow<List<Dictionary>> =
        dictionaryDao.observeByLanguage(language).map { list -> list.map { it.toDomain() } }

    override fun defaultDictionary(language: DictionaryLanguage): Flow<Dictionary?> =
        dictionaryDao.observeDefault(language).map { it?.toDomain() }

    override suspend fun createDictionary(language: DictionaryLanguage, name: String): Dictionary {
        // Перший словник мови одразу стає дефолтним — інакше мова лишається
        // без жодного дефолтного словника, що суперечить історії 15.
        val entity = DictionaryEntity(language = language, name = name, isDefault = isFirstDictionaryForLanguage(language))
        val id = dictionaryDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun setDefault(id: Long) {
        // Зняття прапорця зі старого дефолтного й встановлення нового —
        // в одній DAO-транзакції (`@Transaction` на `DictionaryDao.setDefault`),
        // тому рівно один дефолтний на мову гарантований навіть при падінні
        // між двома записами (історія 18, рев'ю тікета 01).
        dictionaryDao.setDefault(id)
    }

    override suspend fun rename(id: Long, name: String) {
        dictionaryDao.rename(id, name)
    }

    override suspend fun deleteDictionary(id: Long) {
        val entity = dictionaryDao.getById(id) ?: return
        dictionaryDao.delete(entity)
    }

    override fun words(dictionaryId: Long): Flow<List<WordEntry>> =
        wordEntryDao.observeByDictionary(dictionaryId).map { list -> list.map { it.toDomain() } }

    override suspend fun addWord(
        dictionaryId: Long,
        ukrainian: String,
        translation1: String,
        translation2: String?,
        example: String,
        isExampleGenerated: Boolean,
    ): WordEntry {
        val entity = WordEntryEntity(
            dictionaryId = dictionaryId,
            ukrainian = ukrainian,
            translation1 = translation1,
            translation2 = translation2,
            example = example,
            isExampleGenerated = isExampleGenerated,
        )
        val id = wordEntryDao.insert(entity)
        return entity.copy(id = id).toDomain()
    }

    override suspend fun updateWord(word: WordEntry) {
        wordEntryDao.update(word.toEntity())
    }

    override suspend fun deleteWord(id: Long) {
        wordEntryDao.deleteById(id)
    }

    override suspend fun exportToJson(dictionaryId: Long): Uri {
        // Формат і запис файлу — тікет 09 (зона "import-export" за манифестом).
        // Тут лише контракт із interfaces.md, щоб наступні тікети компілювались
        // проти стабільної сигнатури репозиторію.
        TODO("Реалізація в тікеті 09 — import/export JSON")
    }

    override suspend fun importFromJson(uri: Uri, language: DictionaryLanguage): Dictionary {
        TODO("Реалізація в тікеті 09 — import/export JSON")
    }

    override suspend fun ensureDefaultDictionaries() {
        for (language in DictionaryLanguage.entries) {
            if (isFirstDictionaryForLanguage(language)) {
                dictionaryDao.insert(
                    DictionaryEntity(language = language, name = defaultDictionaryName, isDefault = true),
                )
            }
        }
    }

    /** Чи це буде перший словник цієї мови — спільна перевірка для [createDictionary] і [ensureDefaultDictionaries]. */
    private suspend fun isFirstDictionaryForLanguage(language: DictionaryLanguage): Boolean =
        dictionaryDao.countByLanguage(language) == 0

    private fun DictionaryEntity.toDomain() = Dictionary(id = id, language = language, name = name, isDefault = isDefault)

    private fun WordEntryEntity.toDomain() = WordEntry(
        id = id,
        dictionaryId = dictionaryId,
        ukrainian = ukrainian,
        translation1 = translation1,
        translation2 = translation2,
        example = example,
        isExampleGenerated = isExampleGenerated,
    )

    private fun WordEntry.toEntity() = WordEntryEntity(
        id = id,
        dictionaryId = dictionaryId,
        ukrainian = ukrainian,
        translation1 = translation1,
        translation2 = translation2,
        example = example,
        isExampleGenerated = isExampleGenerated,
    )
}
