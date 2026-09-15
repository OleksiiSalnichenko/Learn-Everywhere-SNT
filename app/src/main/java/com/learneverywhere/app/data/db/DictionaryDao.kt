package com.learneverywhere.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.learneverywhere.app.data.model.DictionaryLanguage
import kotlinx.coroutines.flow.Flow

@Dao
interface DictionaryDao {

    @Query("SELECT * FROM dictionaries WHERE language = :language ORDER BY name")
    fun observeByLanguage(language: DictionaryLanguage): Flow<List<DictionaryEntity>>

    @Query("SELECT * FROM dictionaries WHERE language = :language AND is_default = 1 LIMIT 1")
    fun observeDefault(language: DictionaryLanguage): Flow<DictionaryEntity?>

    @Query("SELECT * FROM dictionaries WHERE id = :id")
    suspend fun getById(id: Long): DictionaryEntity?

    @Query("SELECT COUNT(*) FROM dictionaries WHERE language = :language")
    suspend fun countByLanguage(language: DictionaryLanguage): Int

    @Insert
    suspend fun insert(entity: DictionaryEntity): Long

    @Update
    suspend fun update(entity: DictionaryEntity)

    @Delete
    suspend fun delete(entity: DictionaryEntity)

    @Query("UPDATE dictionaries SET is_default = 0 WHERE language = :language")
    suspend fun clearDefaultFlag(language: DictionaryLanguage)

    @Query("UPDATE dictionaries SET is_default = 1 WHERE id = :id")
    suspend fun setDefaultFlag(id: Long)

    @Query("UPDATE dictionaries SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)
}
