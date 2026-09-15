package com.learneverywhere.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.learneverywhere.app.data.model.InterfaceLanguage
import com.learneverywhere.app.data.model.MainLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Ключі DataStore — деталь реалізації, схована за `SettingsRepository`
 * (жоден інший модуль не бачить цей об'єкт).
 */
private object Keys {
    val MAIN_LANGUAGE = stringPreferencesKey("main_language")
    val INTERFACE_LANGUAGE = stringPreferencesKey("interface_language")
    val LOOP = booleanPreferencesKey("loop")
    val SHUFFLE = booleanPreferencesKey("shuffle")
    val UKRAINIAN_REPEAT_COUNT = intPreferencesKey("ukrainian_repeat_count")
    val UKRAINIAN_REPEAT_PAUSE = intPreferencesKey("ukrainian_repeat_pause")
    val TRANSLATION_DELAY = intPreferencesKey("translation_delay")
    val TRANSLATION_REPEAT_COUNT = intPreferencesKey("translation_repeat_count")
    val TRANSLATION_REPEAT_PAUSE = intPreferencesKey("translation_repeat_pause")
    val EXAMPLE_PAUSE = intPreferencesKey("example_pause")
    val INCLUDE_EXAMPLE_IN_PLAYBACK = booleanPreferencesKey("include_example_in_playback")
    val NEXT_WORD_PAUSE = intPreferencesKey("next_word_pause")
    val SHOW_CARD_DURING_PLAYBACK = booleanPreferencesKey("show_card_during_playback")
}

class SettingsRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private val defaults = AppSettings()

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs -> prefs.toAppSettings() }

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = settings.first()
        val updated = transform(current)
        dataStore.edit { prefs ->
            prefs[Keys.MAIN_LANGUAGE] = updated.mainLanguage.name
            prefs[Keys.INTERFACE_LANGUAGE] = updated.interfaceLanguage.name
            prefs[Keys.LOOP] = updated.loop
            prefs[Keys.SHUFFLE] = updated.shuffle
            prefs[Keys.UKRAINIAN_REPEAT_COUNT] = updated.ukrainianRepeatCount
            prefs[Keys.UKRAINIAN_REPEAT_PAUSE] = updated.ukrainianRepeatPause
            prefs[Keys.TRANSLATION_DELAY] = updated.translationDelay
            prefs[Keys.TRANSLATION_REPEAT_COUNT] = updated.translationRepeatCount
            prefs[Keys.TRANSLATION_REPEAT_PAUSE] = updated.translationRepeatPause
            prefs[Keys.EXAMPLE_PAUSE] = updated.examplePause
            prefs[Keys.INCLUDE_EXAMPLE_IN_PLAYBACK] = updated.includeExampleInPlayback
            prefs[Keys.NEXT_WORD_PAUSE] = updated.nextWordPause
            prefs[Keys.SHOW_CARD_DURING_PLAYBACK] = updated.showCardDuringPlayback
        }
    }

    override suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }

    private fun Preferences.toAppSettings() = AppSettings(
        mainLanguage = this[Keys.MAIN_LANGUAGE]?.let { runCatching { MainLanguage.valueOf(it) }.getOrNull() }
            ?: defaults.mainLanguage,
        interfaceLanguage = this[Keys.INTERFACE_LANGUAGE]?.let { runCatching { InterfaceLanguage.valueOf(it) }.getOrNull() }
            ?: defaults.interfaceLanguage,
        loop = this[Keys.LOOP] ?: defaults.loop,
        shuffle = this[Keys.SHUFFLE] ?: defaults.shuffle,
        ukrainianRepeatCount = this[Keys.UKRAINIAN_REPEAT_COUNT] ?: defaults.ukrainianRepeatCount,
        ukrainianRepeatPause = this[Keys.UKRAINIAN_REPEAT_PAUSE] ?: defaults.ukrainianRepeatPause,
        translationDelay = this[Keys.TRANSLATION_DELAY] ?: defaults.translationDelay,
        translationRepeatCount = this[Keys.TRANSLATION_REPEAT_COUNT] ?: defaults.translationRepeatCount,
        translationRepeatPause = this[Keys.TRANSLATION_REPEAT_PAUSE] ?: defaults.translationRepeatPause,
        examplePause = this[Keys.EXAMPLE_PAUSE] ?: defaults.examplePause,
        includeExampleInPlayback = this[Keys.INCLUDE_EXAMPLE_IN_PLAYBACK] ?: defaults.includeExampleInPlayback,
        nextWordPause = this[Keys.NEXT_WORD_PAUSE] ?: defaults.nextWordPause,
        showCardDuringPlayback = this[Keys.SHOW_CARD_DURING_PLAYBACK] ?: defaults.showCardDuringPlayback,
    )
}
