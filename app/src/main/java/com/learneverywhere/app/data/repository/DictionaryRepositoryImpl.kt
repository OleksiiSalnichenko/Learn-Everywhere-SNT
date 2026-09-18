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
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

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
        val dictionaryEntity = dictionaryDao.getById(dictionaryId)
            ?: throw IllegalArgumentException("Dictionary $dictionaryId not found")
        val words = wordEntryDao.observeByDictionary(dictionaryId).first()

        val json = JSONObject().apply {
            put("dictionaryName", dictionaryEntity.name)
            put("language", dictionaryEntity.language.name)
            put(
                "words",
                JSONArray().apply {
                    words.forEach { word ->
                        put(
                            JSONObject().apply {
                                put("ukrainian", word.ukrainian)
                                put("translation1", word.translation1)
                                // translation2 необов'язковий (R06/R07) — putOpt пропускає ключ,
                                // якщо значення null, а не пише JSON-null.
                                putOpt("translation2", word.translation2)
                                put("example", word.example)
                            },
                        )
                    }
                },
            )
        }

        // Кеш застосунку (не публічне сховище) — файл читається назад лише
        // через content://-Uri нашого DictionaryExportProvider, який ми самі
        // й повертаємо; окремого дозволу на сховище не потрібно (R70i вже
        // покриває RECORD_AUDIO/INTERNET, більше дозволів історія 20/21 не вимагає).
        val exportsDir = File(context.cacheDir, DictionaryExportProvider.EXPORTS_DIR_NAME).apply { mkdirs() }
        val fileName = "dictionary-${dictionaryEntity.id}-${System.currentTimeMillis()}.json"
        val file = File(exportsDir, fileName)
        file.writeText(json.toString(JSON_INDENT_SPACES))

        return DictionaryExportProvider.uriFor("${context.packageName}.exportprovider", fileName)
    }

    override suspend fun importFromJson(uri: Uri, language: DictionaryLanguage): Dictionary {
        val json = readDictionaryJson(uri)

        val dictionaryName = json.optString(FIELD_DICTIONARY_NAME).trim().ifEmpty {
            throw InvalidDictionaryFileException("Файл без поля \"$FIELD_DICTIONARY_NAME\"")
        }
        val wordsArray = json.optJSONArray(FIELD_WORDS)
            ?: throw InvalidDictionaryFileException("Файл без масиву \"$FIELD_WORDS\"")

        // Спершу повністю розбираємо й валідуємо ВСІ слова і лише тоді пишемо
        // в базу (нижче) — так неправильна структура ніколи не лишає
        // напівстворений словник (критерій приймання тікета 09, історія 22/R10.2).
        val parsedWords = (0 until wordsArray.length()).map { index ->
            val wordJson = wordsArray.optJSONObject(index)
                ?: throw InvalidDictionaryFileException("Слово #${index + 1} не є об'єктом")
            val ukrainian = wordJson.optString(FIELD_UKRAINIAN).trim().ifEmpty {
                throw InvalidDictionaryFileException("Слово #${index + 1} без поля \"$FIELD_UKRAINIAN\"")
            }
            val translation1 = wordJson.optString(FIELD_TRANSLATION1).trim().ifEmpty {
                throw InvalidDictionaryFileException("Слово #${index + 1} без поля \"$FIELD_TRANSLATION1\"")
            }
            val translation2 = wordJson.optString(FIELD_TRANSLATION2).trim().ifEmpty { null }
            val example = wordJson.optString(FIELD_EXAMPLE)
            ParsedWord(ukrainian, translation1, translation2, example)
        }

        val dictionary = createDictionary(language, dictionaryName)
        parsedWords.forEach { word ->
            addWord(
                dictionaryId = dictionary.id,
                ukrainian = word.ukrainian,
                translation1 = word.translation1,
                translation2 = word.translation2,
                example = word.example,
                // Слово прийшло з файлу, а не згенероване перекладачем при
                // захопленні (тікет 06) — прапорець не частина JSON-схеми
                // interfaces.md, тож для імпортованих слів він завжди false.
                isExampleGenerated = false,
            )
        }
        return dictionary
    }

    override suspend fun detectImportLanguage(uri: Uri): DictionaryLanguage? {
        val json = try {
            readDictionaryJson(uri)
        } catch (e: InvalidDictionaryFileException) {
            return null
        }
        val raw = json.optString(FIELD_LANGUAGE)
        return try {
            DictionaryLanguage.valueOf(raw)
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun readDictionaryJson(uri: Uri): JSONObject {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw InvalidDictionaryFileException("Не вдалося відкрити файл")
        } catch (e: IOException) {
            throw InvalidDictionaryFileException("Не вдалося прочитати файл", e)
        }
        return try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw InvalidDictionaryFileException("Файл не є валідним JSON", e)
        }
    }

    private data class ParsedWord(
        val ukrainian: String,
        val translation1: String,
        val translation2: String?,
        val example: String,
    )

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

    private companion object {
        // Ключі JSON-схеми з interfaces.md §«Дані» (R72i) — єдине місце, де
        // формат файлу відомий за межами export/import-функцій вище.
        const val FIELD_DICTIONARY_NAME = "dictionaryName"
        const val FIELD_LANGUAGE = "language"
        const val FIELD_WORDS = "words"
        const val FIELD_UKRAINIAN = "ukrainian"
        const val FIELD_TRANSLATION1 = "translation1"
        const val FIELD_TRANSLATION2 = "translation2"
        const val FIELD_EXAMPLE = "example"
        const val JSON_INDENT_SPACES = 2
    }
}
