package com.learneverywhere.app.data.repository

/**
 * Файл, обраний для імпорту (тікет 09, історія 22/R10.2), не відповідає
 * JSON-схемі з `interfaces.md` — не JSON, або відсутнє обов'язкове поле.
 * Кидається до будь-якого запису в базу ([DictionaryRepositoryImpl.importFromJson]
 * спершу повністю розбирає і валідує файл і лише тоді створює словник/слова),
 * тому стан бази лишається незмінним (критерій приймання тікета 09).
 */
class InvalidDictionaryFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
