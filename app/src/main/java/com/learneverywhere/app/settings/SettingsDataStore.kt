package com.learneverywhere.app.settings

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Один DataStore-файл на застосунок. Delegate на `Context` — стандартний
 * спосіб отримати єдиний інстанс `DataStore` без ручного синглтона
 * (рекомендація AndroidX DataStore).
 */
val Context.settingsDataStore by preferencesDataStore(name = "settings")
