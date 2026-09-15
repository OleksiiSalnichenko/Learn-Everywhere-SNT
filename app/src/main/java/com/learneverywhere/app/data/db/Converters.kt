package com.learneverywhere.app.data.db

import androidx.room.TypeConverter
import com.learneverywhere.app.data.model.DictionaryLanguage

/** Room зберігає enum як TEXT — назва константи, без ручного мапінгу на Int. */
class Converters {
    @TypeConverter
    fun fromDictionaryLanguage(language: DictionaryLanguage): String = language.name

    @TypeConverter
    fun toDictionaryLanguage(value: String): DictionaryLanguage = DictionaryLanguage.valueOf(value)
}
