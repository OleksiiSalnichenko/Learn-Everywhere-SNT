package com.learneverywhere.app.data.repository

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.learneverywhere.app.data.db.AppDatabase
import com.learneverywhere.app.data.model.DictionaryLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Юніт-тест на шов `DictionaryRepository` (interfaces.md) — найважливіша
 * поведінка тікета 01: "якщо словника ще немає, система створює його сама
 * і робить дефолтовим" (історія 16/17, критерій приймання тікета 01).
 *
 * In-memory Room + Robolectric (не справжній пристрій) — публічний контракт
 * репозиторію, без заглядання у внутрішню схему таблиць.
 */
@RunWith(RobolectricTestRunner::class)
class DictionaryRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: DictionaryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = DictionaryRepositoryImpl(database.dictionaryDao(), database.wordEntryDao(), context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `ensureDefaultDictionaries creates one default dictionary per language when none exist`() = runBlocking {
        // База щойно створена — жодного словника немає.
        assertEquals(emptyList<Any>(), repository.dictionaries(DictionaryLanguage.GERMAN).first())
        assertEquals(emptyList<Any>(), repository.dictionaries(DictionaryLanguage.ENGLISH).first())

        repository.ensureDefaultDictionaries()

        val germanDefault = repository.defaultDictionary(DictionaryLanguage.GERMAN).first()
        val englishDefault = repository.defaultDictionary(DictionaryLanguage.ENGLISH).first()

        assertTrue("німецький дефолтний словник має бути створений", germanDefault != null)
        assertTrue("англійський дефолтний словник має бути створений", englishDefault != null)
        assertTrue(germanDefault!!.isDefault)
        assertTrue(englishDefault!!.isDefault)
    }

    @Test
    fun `ensureDefaultDictionaries does not duplicate an existing default`() = runBlocking {
        // Користувач уже створив свій словник для німецької до виклику ensure —
        // повторний виклик (напр. при кожному старті застосунку) не має
        // плодити другий "Основний" поверх наявного дефолтного.
        val custom = repository.createDictionary(DictionaryLanguage.GERMAN, "Мій словник")
        assertTrue(custom.isDefault)

        repository.ensureDefaultDictionaries()

        val germanDictionaries = repository.dictionaries(DictionaryLanguage.GERMAN).first()
        assertEquals(1, germanDictionaries.size)
        assertEquals("Мій словник", germanDictionaries.first().name)
    }

    @Test
    fun `setDefault keeps exactly one default dictionary per language after repeated calls`() = runBlocking {
        // Атомарність гарантується `@Transaction` на DictionaryDao.setDefault
        // (рев'ю тікета 01): скільки б разів дефолт не перемикали, рівно
        // один словник мови лишається дефолтним — ніколи ні одного, ніколи
        // два одразу.
        val first = repository.createDictionary(DictionaryLanguage.GERMAN, "Перший")
        val second = repository.createDictionary(DictionaryLanguage.GERMAN, "Другий")
        val third = repository.createDictionary(DictionaryLanguage.GERMAN, "Третій")

        repository.setDefault(second.id)
        repository.setDefault(third.id)
        repository.setDefault(first.id)

        val defaults = repository.dictionaries(DictionaryLanguage.GERMAN).first().filter { it.isDefault }
        assertEquals(1, defaults.size)
        assertEquals(first.id, defaults.single().id)
    }

    // --- Тікет 09 — імпорт/експорт JSON ------------------------------------------------

    @Test
    fun `exportToJson then importFromJson recreates an identical word list`() = runBlocking {
        val dictionary = repository.createDictionary(DictionaryLanguage.GERMAN, "Тварини")
        repository.addWord(dictionary.id, "кіт", "die Katze", null, "Die Katze schläft.", false)
        repository.addWord(dictionary.id, "собака", "der Hund", "der Wauwau", "Der Hund bellt.", true)
        val originalWords = repository.words(dictionary.id).first()

        val uri = repository.exportToJson(dictionary.id)
        val imported = repository.importFromJson(uri, DictionaryLanguage.GERMAN)
        val importedWords = repository.words(imported.id).first()

        assertEquals(originalWords.size, importedWords.size)
        originalWords.zip(importedWords).forEach { (original, actual) ->
            assertEquals(original.ukrainian, actual.ukrainian)
            assertEquals(original.translation1, actual.translation1)
            assertEquals(original.translation2, actual.translation2)
            assertEquals(original.example, actual.example)
        }
    }

    @Test
    fun `importFromJson with malformed file throws and leaves the database unchanged`() = runBlocking {
        val malformedFile = File.createTempFile("malformed-import", ".json").apply { writeText("this is not json") }
        val before = repository.dictionaries(DictionaryLanguage.GERMAN).first()

        try {
            repository.importFromJson(Uri.fromFile(malformedFile), DictionaryLanguage.GERMAN)
            org.junit.Assert.fail("Очікувався InvalidDictionaryFileException")
        } catch (expected: InvalidDictionaryFileException) {
            // очікувано
        }

        assertEquals(before, repository.dictionaries(DictionaryLanguage.GERMAN).first())
    }

    @Test
    fun `importFromJson with a word missing translation1 throws and leaves the database unchanged`() = runBlocking {
        val missingFieldFile = File.createTempFile("missing-field-import", ".json").apply {
            writeText("""{"dictionaryName":"Без перекладу","words":[{"ukrainian":"кіт","example":"A cat."}]}""")
        }
        val before = repository.dictionaries(DictionaryLanguage.ENGLISH).first()

        try {
            repository.importFromJson(Uri.fromFile(missingFieldFile), DictionaryLanguage.ENGLISH)
            org.junit.Assert.fail("Очікувався InvalidDictionaryFileException")
        } catch (expected: InvalidDictionaryFileException) {
            // очікувано — обов'язкове поле "translation1" відсутнє.
        }

        assertEquals(before, repository.dictionaries(DictionaryLanguage.ENGLISH).first())
    }
}
