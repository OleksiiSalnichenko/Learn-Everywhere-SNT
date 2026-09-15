package com.learneverywhere.app.translation

/**
 * Доменний виняток: переклад недоступний (немає мережі, таймаут, сервіс
 * не відповів або повернув непридатну відповідь). R14.2 — верхній шар
 * (тікет 06) показує це як діалог з кнопкою «повторити», не сирий
 * `IOException`.
 */
class TranslationUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
