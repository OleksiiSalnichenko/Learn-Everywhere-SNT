package com.learneverywhere.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.learneverywhere.app.data.model.DictionaryLanguage

/**
 * Таблиця словників. Рівно один рядок з `isDefault = true` на кожну мову —
 * це гарантується транзакцією в `DictionaryRepositoryImpl.setDefault`,
 * а не обмеженням схеми (Room/SQLite не вміє "унікальний true на групу"
 * декларативно без часткового індексу, який ускладнив би читання схеми).
 */
@Entity(tableName = "dictionaries")
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val language: DictionaryLanguage,
    val name: String,
    @ColumnInfo(name = "is_default")
    val isDefault: Boolean,
)
