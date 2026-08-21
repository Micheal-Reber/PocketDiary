package com.example.diary.data.repository

import com.example.diary.data.local.DiaryDao
import com.example.diary.data.local.DiaryEntry
import kotlinx.coroutines.flow.Flow

class DiaryRepository(private val diaryDao: DiaryDao) {

    fun getAllEntries(): Flow<List<DiaryEntry>> = diaryDao.getAllEntries()

    suspend fun getEntryByDate(date: String): DiaryEntry? = diaryDao.getEntryByDate(date)

    suspend fun saveEntry(entry: DiaryEntry): Long {
        // Caller must always pass id = 0 for new entries, and the existing row's
        // id for updates. Lookup by date is what ties them together — the schema
        // enforces a UNIQUE index on date so two saves can't collide silently.
        val existing = diaryDao.getEntryByDate(entry.date)
        return if (existing != null) {
            diaryDao.update(entry.copy(id = existing.id, updatedAt = System.currentTimeMillis()))
            existing.id
        } else {
            // Strip any caller-provided id so the INSERT path can't accidentally
            // overwrite an unrelated row via OnConflictStrategy.REPLACE.
            diaryDao.insert(entry.copy(id = 0))
        }
    }

    suspend fun deleteEntry(id: Long) {
        diaryDao.deleteById(id)
    }
}
