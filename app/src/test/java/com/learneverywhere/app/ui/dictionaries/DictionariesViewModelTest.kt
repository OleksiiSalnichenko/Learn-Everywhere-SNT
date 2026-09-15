package com.learneverywhere.app.ui.dictionaries

import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.MainLanguage
import com.learneverywhere.app.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Юніт-тести на шов `DictionariesViewModel` (тікет 05, критерії приймання
 * `.autopilot/.../tickets/05-dictionaries-list.md`). `UnconfinedTestDispatcher`
 * виконує корутини `stateIn`/`launch` синхронно — стан читається одразу після
 * дії, без ручного `advanceUntilIdle()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DictionariesViewModelTest {

    @Test
    fun `active tab defaults to main language, NONE falls back to German`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = DictionariesViewModel(
            FakeDictionaryRepository(),
            FakeSettingsRepository(AppSettings(mainLanguage = MainLanguage.NONE)),
            backgroundScope,
        )

        assertEquals(DictionaryLanguage.GERMAN, viewModel.selectedLanguage.value)
    }

    @Test
    fun `active tab defaults to English when main language is English`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = DictionariesViewModel(
            FakeDictionaryRepository(),
            FakeSettingsRepository(AppSettings(mainLanguage = MainLanguage.ENGLISH)),
            backgroundScope,
        )

        assertEquals(DictionaryLanguage.ENGLISH, viewModel.selectedLanguage.value)
    }

    @Test
    fun `selectTab overrides the main-language default and each tab shows only its own language`() =
        runTest(UnconfinedTestDispatcher()) {
            val repository = FakeDictionaryRepository()
            repository.createDictionary(DictionaryLanguage.GERMAN, "Deutsch-Basis")
            repository.createDictionary(DictionaryLanguage.ENGLISH, "English-Basis")
            val viewModel = DictionariesViewModel(
                repository,
                FakeSettingsRepository(AppSettings(mainLanguage = MainLanguage.GERMAN)),
                backgroundScope,
            )

            viewModel.selectTab(DictionaryLanguage.ENGLISH)

            assertEquals(DictionaryLanguage.ENGLISH, viewModel.selectedLanguage.value)
            assertEquals(listOf("English-Basis"), viewModel.uiState.value.items.map { it.dictionary.name })
        }

    @Test
    fun `setDefault keeps exactly one default dictionary selected in the ui state`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeDictionaryRepository()
        val first = repository.createDictionary(DictionaryLanguage.GERMAN, "Перший")
        val second = repository.createDictionary(DictionaryLanguage.GERMAN, "Другий")
        val viewModel = DictionariesViewModel(
            repository,
            FakeSettingsRepository(AppSettings(mainLanguage = MainLanguage.GERMAN)),
            backgroundScope,
        )
        // Перший словник мови стає дефолтним автоматично (репозиторій, тікет 01) — тут перевіряємо саме перемикання.
        assertTrue(viewModel.uiState.value.items.first { it.dictionary.id == first.id }.dictionary.isDefault)

        viewModel.setDefault(second.id)

        val defaults = viewModel.uiState.value.items.filter { it.dictionary.isDefault }
        assertEquals(1, defaults.size)
        assertEquals(second.id, defaults.single().dictionary.id)
    }

    @Test
    fun `createDictionary adds a new dictionary to the currently active tab`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = DictionariesViewModel(
            FakeDictionaryRepository(),
            FakeSettingsRepository(AppSettings(mainLanguage = MainLanguage.ENGLISH)),
            backgroundScope,
        )

        viewModel.createDictionary("Подорожі")

        val items = viewModel.uiState.value.items
        assertEquals(listOf("Подорожі"), items.map { it.dictionary.name })
        assertEquals(DictionaryLanguage.ENGLISH, items.single().dictionary.language)
    }

    @Test
    fun `createDictionary ignores a blank name`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = DictionariesViewModel(
            FakeDictionaryRepository(),
            FakeSettingsRepository(),
            backgroundScope,
        )

        viewModel.createDictionary("   ")

        assertTrue(viewModel.uiState.value.items.isEmpty())
    }

    @Test
    fun `word count in the ui state reflects the number of words in the dictionary`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeDictionaryRepository()
        val dictionary = repository.createDictionary(DictionaryLanguage.GERMAN, "Deutsch")
        repository.addWord(dictionary.id, "стіл", "der Tisch", null, "Das ist der Tisch.", false)
        repository.addWord(dictionary.id, "стілець", "der Stuhl", null, "Das ist der Stuhl.", false)
        val viewModel = DictionariesViewModel(
            repository,
            FakeSettingsRepository(AppSettings(mainLanguage = MainLanguage.GERMAN)),
            backgroundScope,
        )

        assertEquals(2, viewModel.uiState.value.items.single().wordCount)
    }

    @Test
    fun `empty dictionary list for a language has no items and no default, without throwing`() =
        runTest(UnconfinedTestDispatcher()) {
            val viewModel = DictionariesViewModel(
                FakeDictionaryRepository(),
                FakeSettingsRepository(AppSettings(mainLanguage = MainLanguage.GERMAN)),
                backgroundScope,
            )

            assertTrue(viewModel.uiState.value.items.isEmpty())
            assertNull(viewModel.uiState.value.defaultItem)
        }
}
