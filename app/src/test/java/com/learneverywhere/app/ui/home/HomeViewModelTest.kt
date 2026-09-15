package com.learneverywhere.app.ui.home

import com.learneverywhere.app.data.model.DictionaryLanguage
import com.learneverywhere.app.data.model.MainLanguage
import com.learneverywhere.app.settings.AppSettings
import com.learneverywhere.app.translation.Language
import com.learneverywhere.app.translation.TranslationResult
import com.learneverywhere.app.data.model.InterfaceLanguage
import com.learneverywhere.app.ui.dictionaries.FakeDictionaryRepository
import com.learneverywhere.app.ui.dictionaries.FakeSettingsRepository
import com.learneverywhere.app.voice.LanguageDetector
import com.learneverywhere.app.voice.SpeechFailureReason
import com.learneverywhere.app.voice.SpeechResult
import com.learneverywhere.app.data.model.Dictionary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Юніт-тести на шов `HomeViewModel` (тікет 06, критерії приймання
 * `.autopilot/.../tickets/06-home-capture.md`). Замінники `SpeechInput`/
 * `TranslationService` — `FakeHomeDependencies.kt`; `DictionaryRepository`/
 * `SettingsRepository` — перевикористані `internal`-дублери з тікета 05
 * (`ui.dictionaries.FakeRepositories`, видимі всюди в модулі).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private fun viewModel(
        testScope: kotlinx.coroutines.CoroutineScope,
        dictionaryRepository: FakeDictionaryRepository = FakeDictionaryRepository(),
        translationService: FakeTranslationService = FakeTranslationService(),
        speechInput: FakeSpeechInput = FakeSpeechInput(),
        settings: AppSettings = AppSettings(),
    ) = HomeViewModel(
        dictionaryRepository = dictionaryRepository,
        translationService = translationService,
        speechInput = speechInput,
        languageDetector = LanguageDetector { InterfaceLanguage.ENGLISH },
        settingsRepository = FakeSettingsRepository(settings),
        scope = testScope,
        defaultDictionaryName = { "Основний" },
    )

    @Test
    fun `voice and text input lead to the identical confirmation dialog`() = runTest(UnconfinedTestDispatcher()) {
        val response = TranslationResult(candidates = listOf("стіл"), example = "Das ist der Tisch.", isExampleGenerated = false)

        val voiceSpeech = FakeSpeechInput().apply { enqueue(SpeechResult.Recognized("der Tisch")) }
        val voiceViewModel = viewModel(
            backgroundScope,
            translationService = FakeTranslationService().apply { enqueueSuccess(response) },
            speechInput = voiceSpeech,
        )
        voiceViewModel.onMicClick()

        val textViewModel = viewModel(backgroundScope, translationService = FakeTranslationService().apply { enqueueSuccess(response) })
        textViewModel.onInputTextChange("der Tisch")
        textViewModel.onSubmitText()

        assertEquals(voiceViewModel.uiState.value.dialog, textViewModel.uiState.value.dialog)
        assertTrue(voiceViewModel.uiState.value.dialog is HomeDialog.Confirm)
    }

    @Test
    fun `ukrainian word with no main language shows the dictionary choice dialog`() = runTest(UnconfinedTestDispatcher()) {
        val viewModel = viewModel(backgroundScope, settings = AppSettings(mainLanguage = MainLanguage.NONE))

        viewModel.onInputTextChange("стіл")
        viewModel.onSubmitText()

        assertEquals(HomeDialog.ChooseDictionary("стіл"), viewModel.uiState.value.dialog)
    }

    @Test
    fun `ukrainian word with main language set skips the choice and translates straight away`() =
        runTest(UnconfinedTestDispatcher()) {
            val translation = FakeTranslationService().apply {
                enqueueSuccess(TranslationResult(listOf("der Tisch"), "Das ist der Tisch.", false))
            }
            val viewModel = viewModel(backgroundScope, translationService = translation, settings = AppSettings(mainLanguage = MainLanguage.GERMAN))

            viewModel.onInputTextChange("стіл")
            viewModel.onSubmitText()

            assertEquals(1, translation.calls.size)
            assertEquals(FakeTranslationService.Call("стіл", Language.UKRAINIAN, Language.GERMAN), translation.calls.single())
            val dialog = viewModel.uiState.value.dialog as HomeDialog.Confirm
            assertEquals("стіл", dialog.capture.ukrainian)
            assertEquals("der Tisch", dialog.capture.translation1)
            assertEquals(DictionaryLanguage.GERMAN, dialog.capture.dictionaryLanguage)
        }

    @Test
    fun `choosing a dictionary in the choice dialog translates into that language`() = runTest(UnconfinedTestDispatcher()) {
        val translation = FakeTranslationService().apply {
            enqueueSuccess(TranslationResult(listOf("table"), "This is a table.", false))
        }
        val viewModel = viewModel(backgroundScope, translationService = translation, settings = AppSettings(mainLanguage = MainLanguage.NONE))
        viewModel.onInputTextChange("стіл")
        viewModel.onSubmitText()

        viewModel.onChooseDictionary(DictionaryLanguage.ENGLISH)

        assertEquals(FakeTranslationService.Call("стіл", Language.UKRAINIAN, Language.ENGLISH), translation.calls.single())
        val dialog = viewModel.uiState.value.dialog as HomeDialog.Confirm
        assertEquals(DictionaryLanguage.ENGLISH, dialog.capture.dictionaryLanguage)
    }

    @Test
    fun `german word resolves to a single ukrainian value with the word itself as translation1`() =
        runTest(UnconfinedTestDispatcher()) {
            val translation = FakeTranslationService().apply {
                enqueueSuccess(TranslationResult(listOf("стіл"), "Das ist der Tisch.", false))
            }
            val viewModel = viewModel(backgroundScope, translationService = translation)

            viewModel.onInputTextChange("der Tisch")
            viewModel.onSubmitText()

            assertEquals(FakeTranslationService.Call("der Tisch", Language.GERMAN, Language.UKRAINIAN), translation.calls.single())
            val dialog = viewModel.uiState.value.dialog as HomeDialog.Confirm
            assertEquals("стіл", dialog.capture.ukrainian)
            assertEquals("der Tisch", dialog.capture.translation1)
            assertNull(dialog.capture.translation2)
            assertEquals("Das ist der Tisch.", dialog.capture.example)
        }

    @Test
    fun `cancel on the confirmation dialog saves nothing`() = runTest(UnconfinedTestDispatcher()) {
        val translation = FakeTranslationService().apply {
            enqueueSuccess(TranslationResult(listOf("стіл"), "Das ist der Tisch.", false))
        }
        val repository = FakeDictionaryRepository()
        val viewModel = viewModel(backgroundScope, dictionaryRepository = repository, translationService = translation)
        viewModel.onInputTextChange("der Tisch")
        viewModel.onSubmitText()

        viewModel.onCancelConfirmation()

        assertNull(viewModel.uiState.value.dialog)
        val dictionariesAfterCancel = repository.dictionaries(DictionaryLanguage.GERMAN).first()
        assertEquals(emptyList<Dictionary>(), dictionariesAfterCancel)
    }

    @Test
    fun `confirm creates a default dictionary named Osnovnyi when none exists yet and saves the word`() =
        runTest(UnconfinedTestDispatcher()) {
            val translation = FakeTranslationService().apply {
                enqueueSuccess(TranslationResult(listOf("стіл"), "Das ist der Tisch.", false))
            }
            val repository = FakeDictionaryRepository()
            val viewModel = viewModel(backgroundScope, dictionaryRepository = repository, translationService = translation)
            viewModel.onInputTextChange("der Tisch")
            viewModel.onSubmitText()

            viewModel.onConfirm()

            val created = repository.dictionaries(DictionaryLanguage.GERMAN).first().singleOrNull()
            assertEquals("Основний", created?.name)
            assertTrue(created?.isDefault == true)
            val words = repository.words(created!!.id).first()
            assertEquals(1, words.size)
            assertEquals("стіл", words.single().ukrainian)
            assertEquals("der Tisch", words.single().translation1)
            assertNull(viewModel.uiState.value.dialog)
            assertEquals("", viewModel.uiState.value.inputText)
        }

    @Test
    fun `confirm reuses the existing default dictionary instead of creating a new one`() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeDictionaryRepository()
        val existing = repository.createDictionary(DictionaryLanguage.GERMAN, "Мій словник")
        val translation = FakeTranslationService().apply {
            enqueueSuccess(TranslationResult(listOf("стіл"), "Das ist der Tisch.", false))
        }
        val viewModel = viewModel(backgroundScope, dictionaryRepository = repository, translationService = translation)
        viewModel.onInputTextChange("der Tisch")
        viewModel.onSubmitText()

        viewModel.onConfirm()

        val dictionaries = repository.dictionaries(DictionaryLanguage.GERMAN).first()
        assertEquals(listOf(existing.id), dictionaries.map { it.id })
        val words = repository.words(existing.id).first()
        assertEquals(1, words.size)
    }

    @Test
    fun `failed speech recognition shows a not-understood dialog instead of silence`() = runTest(UnconfinedTestDispatcher()) {
        val speech = FakeSpeechInput().apply { enqueue(SpeechResult.Failed(SpeechFailureReason.NOT_UNDERSTOOD)) }
        val viewModel = viewModel(backgroundScope, speechInput = speech)

        viewModel.onMicClick()

        assertEquals(HomeDialog.RecognitionFailed, viewModel.uiState.value.dialog)
        assertEquals(false, viewModel.uiState.value.isListening)
    }

    @Test
    fun `translation failure shows a retry dialog and keeps the word in the input field`() = runTest(UnconfinedTestDispatcher()) {
        val translation = FakeTranslationService().apply { enqueueFailure() }
        val viewModel = viewModel(backgroundScope, translationService = translation)

        viewModel.onInputTextChange("der Tisch")
        viewModel.onSubmitText()

        assertEquals(HomeDialog.TranslationFailed, viewModel.uiState.value.dialog)
        assertEquals("der Tisch", viewModel.uiState.value.inputText)
    }

    @Test
    fun `retry after a translation failure re-attempts with the same word and succeeds`() = runTest(UnconfinedTestDispatcher()) {
        val translation = FakeTranslationService().apply {
            enqueueFailure()
            enqueueSuccess(TranslationResult(listOf("стіл"), "Das ist der Tisch.", false))
        }
        val viewModel = viewModel(backgroundScope, translationService = translation)
        viewModel.onInputTextChange("der Tisch")
        viewModel.onSubmitText()
        assertEquals(HomeDialog.TranslationFailed, viewModel.uiState.value.dialog)

        viewModel.onRetryTranslation()

        assertEquals(2, translation.calls.size)
        assertTrue(viewModel.uiState.value.dialog is HomeDialog.Confirm)
    }
}
