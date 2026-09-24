package com.example.diary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {
    @Query("SELECT * FROM diary_entries ORDER BY date DESC, createdAt DESC")
    fun getAllEntries(): Flow<List<DiaryEntry>>

    /** One-shot read for backup export (safe inside withTransaction). */
    @Query("SELECT * FROM diary_entries ORDER BY date DESC, createdAt DESC")
    suspend fun getAllEntriesOnce(): List<DiaryEntry>

    // Search all user-visible diary fields. LIKE keeps Chinese matching simple
    // (per-character) and avoids adding an FTS table for this small local DB.
    @Query("""
        SELECT * FROM diary_entries
          WHERE content LIKE '%' || :query || '%' ESCAPE '\\'
              OR date LIKE '%' || :query || '%' ESCAPE '\\'
              OR COALESCE(mood, '') LIKE '%' || :query || '%' ESCAPE '\\'
              OR COALESCE(weather, '') LIKE '%' || :query || '%' ESCAPE '\\'
              OR COALESCE(locationName, '') LIKE '%' || :query || '%' ESCAPE '\\'
        ORDER BY date DESC, createdAt DESC
    """)
    fun searchEntries(query: String): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary_entries WHERE date = :date")
    suspend fun getEntryByDate(date: String): DiaryEntry?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: DiaryEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DiaryEntry>)

    @Update
    suspend fun update(entry: DiaryEntry)

    @Delete
    suspend fun delete(entry: DiaryEntry)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM diary_entries")
    suspend fun deleteAll()
}
