package com.example.diary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY date DESC, createdAt DESC")
    fun getAllEntries(): Flow<List<DiaryEntry>>

    // Full-text search over entry content. LIKE keeps Chinese matching simple
    // (per-character); user-supplied % and _ act as wildcards — acceptable for
    // a personal diary.
    @Query("SELECT * FROM diary_entries WHERE content LIKE '%' || :query || '%' ORDER BY date DESC, createdAt DESC")
    fun searchEntries(query: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE date = :date")
    suspend fun getEntryByDate(date: String): DiaryEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DiaryEntry): Long

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}
