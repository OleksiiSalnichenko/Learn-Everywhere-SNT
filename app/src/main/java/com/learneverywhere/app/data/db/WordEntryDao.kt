package com.learneverywhere.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WordEntryDao {

    @Query("SELECT * FROM word_entries WHERE dictionary_id = :dictionaryId ORDER BY id")
    fun observeByDictionary(dictionaryId: Long): Flow<List<WordEntryEntity>>

    @Query("SELECT * FROM word_entries WHERE id = :id")
    suspend fun getById(id: Long): WordEntryEntity?

    @Insert
    suspend fun insert(entity: WordEntryEntity): Long

    @Update
    suspend fun update(entity: WordEntryEntity)

    @Query("DELETE FROM word_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
