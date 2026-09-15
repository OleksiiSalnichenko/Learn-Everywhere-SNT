package com.learneverywhere.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Єдина Room-база застосунку (однопроцесний застосунок без бекенду —
 * рішення зі spec.md §«Дані»). Один інстанс на процес, тримається в
 * `AppContainer`.
 */
@Database(
    entities = [DictionaryEntity::class, WordEntryEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun dictionaryDao(): DictionaryDao
    abstract fun wordEntryDao(): WordEntryDao

    companion object {
        private const val DATABASE_NAME = "learn_everywhere.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
