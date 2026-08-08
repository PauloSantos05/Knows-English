package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY searchedAt DESC")
    fun getSearchHistory(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE isBookmarked = 1 ORDER BY searchedAt DESC")
    fun getBookmarkedWords(): Flow<List<WordEntity>>

    @Query("SELECT * FROM words WHERE LOWER(word) = LOWER(:word) LIMIT 1")
    suspend fun getWord(word: String): WordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(wordEntity: WordEntity)

    @Query("UPDATE words SET isBookmarked = :isBookmarked WHERE LOWER(word) = LOWER(:word)")
    suspend fun updateBookmark(word: String, isBookmarked: Boolean)

    @Query("DELETE FROM words WHERE LOWER(word) = LOWER(:word)")
    suspend fun deleteWord(word: String)

    @Query("DELETE FROM words WHERE isBookmarked = 0")
    suspend fun clearHistory()
}
