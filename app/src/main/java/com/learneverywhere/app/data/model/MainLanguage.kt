package com.learneverywhere.app.data.model

/**
 * Опція «головна мова» з екрана налаштувань (історія 35 / R23).
 * NONE ("ніяка") означає, що для українських слів застосунок питає
 * користувача, у який словник додати слово (історія 5).
 */
enum class MainLanguage {
    NONE,
    GERMAN,
    ENGLISH,
}
