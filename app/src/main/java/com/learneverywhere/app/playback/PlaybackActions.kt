package com.learneverywhere.app.playback

import com.learneverywhere.app.data.model.WordEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * Мінімальна підмножина публічної межі [PlaybackController] (interfaces.md
 * §playback), потрібна екрану словників (тікет 10) — плей/пауза/стоп і
 * поточне слово. Виділено окремим інтерфейсом, а не переданням самого
 * [PlaybackController] у [com.learneverywhere.app.ui.dictionaries.DictionariesViewModel]
 * лише щоб той шов лишався тестованим без `Context`/Robolectric (той самий
 * прийом, що й фейкові репозиторії тікета 05) — [PlaybackController] лишається
 * єдиною реалізацією, це не альтернативний рушій.
 */
interface PlaybackActions {
    val currentWord: StateFlow<WordEntry?>
    val isPlaying: StateFlow<Boolean>

    fun start(dictionaryId: Long)
    fun pause()
    fun resume()
    fun stop()
}
