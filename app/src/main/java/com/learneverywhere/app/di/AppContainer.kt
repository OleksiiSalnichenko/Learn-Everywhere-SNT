package com.learneverywhere.app.di

import android.content.Context
import com.learneverywhere.app.data.db.AppDatabase
import com.learneverywhere.app.data.model.InterfaceLanguage
import com.learneverywhere.app.data.repository.DictionaryRepository
import com.learneverywhere.app.data.repository.DictionaryRepositoryImpl
import com.learneverywhere.app.settings.AppSettings
import com.learneverywhere.app.settings.SettingsRepository
import com.learneverywhere.app.settings.SettingsRepositoryImpl
import com.learneverywhere.app.settings.settingsDataStore
import com.learneverywhere.app.translation.OkHttpTranslationHttpClient
import com.learneverywhere.app.translation.TranslationService
import com.learneverywhere.app.translation.TranslationServiceImpl
import com.learneverywhere.app.voice.AndroidSpeaker
import com.learneverywhere.app.voice.AndroidSpeechInput
import com.learneverywhere.app.voice.LanguageDetector
import com.learneverywhere.app.voice.Speaker
import com.learneverywhere.app.voice.SpeechInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Простий сервіс-локатор (свідоме рішення проти Hilt/Dagger — spec.md
 * §«Ін'єкція залежностей», п.8 брифа: не ускладнювати). Один інстанс
 * тримається в `LearnEverywhereApplication` і живе весь процес.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database: AppDatabase = AppDatabase.build(appContext)

    // Власний scope контейнера — лише для фонового кешування `latestSettings`
    // нижче (не для доменної логіки: та й далі живе в per-екранних
    // CoroutineScope, переданих ззовні, як `DictionariesViewModel`/`HomeViewModel`).
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val dictionaryRepository: DictionaryRepository =
        DictionaryRepositoryImpl(database.dictionaryDao(), database.wordEntryDao(), appContext)

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(context.settingsDataStore)

    // `AndroidSpeechInput`/`LanguageDetector` (тікет 03) хочуть поточну мову
    // інтерфейсу синхронно (`() -> InterfaceLanguage`), а `settingsRepository.settings`
    // — це `Flow`: посеред виклику `SpeechRecognizer`/евристики чекати на suspend
    // нема де. Тому тримаємо простий фоновий кеш останнього значення — той самий
    // прийом, що `LearnEverywhereApplication.applicationScope` для `ensureDefaultDictionaries()`.
    @Volatile
    private var latestSettings: AppSettings = AppSettings()

    init {
        containerScope.launch {
            settingsRepository.settings.collect { latestSettings = it }
        }
    }

    private val interfaceLanguage: () -> InterfaceLanguage = { latestSettings.interfaceLanguage }

    // Тікет 02 — переклад (MyMemory), не підключено до тікета 06 (interfaces.md §«З таска 02»).
    val translationService: TranslationService = TranslationServiceImpl(OkHttpTranslationHttpClient())

    // Тікет 03 — голос, так само не підключено раніше (interfaces.md §«З таска 03»).
    val speechInput: SpeechInput = AndroidSpeechInput(appContext, interfaceLanguage)
    val languageDetector: LanguageDetector = LanguageDetector(interfaceLanguage)
    val speaker: Speaker = AndroidSpeaker(appContext)
}
