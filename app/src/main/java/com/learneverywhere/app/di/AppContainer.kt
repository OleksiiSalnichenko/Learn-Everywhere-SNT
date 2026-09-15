package com.learneverywhere.app.di

import android.content.Context
import com.learneverywhere.app.data.db.AppDatabase
import com.learneverywhere.app.data.repository.DictionaryRepository
import com.learneverywhere.app.data.repository.DictionaryRepositoryImpl
import com.learneverywhere.app.settings.SettingsRepository
import com.learneverywhere.app.settings.SettingsRepositoryImpl
import com.learneverywhere.app.settings.settingsDataStore

/**
 * Простий сервіс-локатор (свідоме рішення проти Hilt/Dagger — spec.md
 * §«Ін'єкція залежностей», п.8 брифа: не ускладнювати). Один інстанс
 * тримається в `LearnEverywhereApplication` і живе весь процес.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val database: AppDatabase = AppDatabase.build(appContext)

    val dictionaryRepository: DictionaryRepository =
        DictionaryRepositoryImpl(database.dictionaryDao(), database.wordEntryDao(), appContext)

    val settingsRepository: SettingsRepository =
        SettingsRepositoryImpl(context.settingsDataStore)
}
