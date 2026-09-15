package com.learneverywhere.app.settings

import kotlinx.coroutines.flow.Flow

/** Публічна межа модуля `settings` (interfaces.md) — ховає ключі DataStore. */
interface SettingsRepository {

    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun resetToDefaults()
}
