package com.learneverywhere.app.settings

import com.learneverywhere.app.data.model.InterfaceLanguage
import com.learneverywhere.app.data.model.MainLanguage

/**
 * Усі налаштування програвання й інтерфейсу (екран налаштувань, тікет 04).
 * Значення за замовчуванням — рівно ті, що названі в брифі п.3.3 —
 * навмисно продубльовані тут у конструкторі за замовчуванням, а не лише
 * в `SettingsRepositoryImpl`, щоб дефолти лишались очевидні з одного місця.
 *
 * Паузи в секундах — ціле число 1..6 (брифінг п.3.3.9: "від 1 секунди до 6").
 */
data class AppSettings(
    val mainLanguage: MainLanguage = MainLanguage.GERMAN,
    val interfaceLanguage: InterfaceLanguage = InterfaceLanguage.ENGLISH,
    val loop: Boolean = false,
    val shuffle: Boolean = false,
    val ukrainianRepeatCount: Int = 1,
    val ukrainianRepeatPause: Int = 2,
    val translationDelay: Int = 3,
    val translationRepeatCount: Int = 2,
    val translationRepeatPause: Int = 2,
    val examplePause: Int = 2,
    val includeExampleInPlayback: Boolean = false,
    val nextWordPause: Int = 2,
    val showCardDuringPlayback: Boolean = true,
)
