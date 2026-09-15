package com.learneverywhere.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Таблиця слів. `dictionaryId` — зовнішній ключ на `DictionaryEntity` з
 * каскадним видаленням: видалення словника (історія 41) прибирає всі його
 * слова без окремого циклу видалень з боку репозиторію.
 */
@Entity(
    tableName = "word_entries",
    foreignKeys = [
        ForeignKey(
            entity = DictionaryEntity::class,
            parentColumns = ["id"],
            childColumns = ["dictionary_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("dictionary_id")],
)
data class WordEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "dictionary_id")
    val dictionaryId: Long,
    val ukrainian: String,
    val translation1: String,
    val translation2: String?,
    val example: String,
    @ColumnInfo(name = "is_example_generated")
    val isExampleGenerated: Boolean,
)
