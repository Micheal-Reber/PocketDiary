package com.example.diary.data.repository

import com.example.diary.data.local.DiaryDao
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.photo.PhotoStore
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

    /**
     * Delete the diary row by id and return the photo file paths that were
     * referenced by that row at delete time. The caller is responsible for
     * physically deleting the files on a background dispatcher.
     *
     * Reading the entry *before* deleting the row is the point: it means we
     * delete files based on what's actually in the database, not whatever
     * the UI happened to render. This catches the case where the UI's photo
     * list is stale (e.g. user added a photo, rotated the screen, the photo
     * list state got partially restored, user then deletes the entry).
     */
    suspend fun deleteEntryAndReturnPhotoPaths(id: Long): List<String> {
        val entry = diaryDao.getEntryById(id)
        diaryDao.deleteById(id)
        return entry?.let { PhotoStore.parsePaths(it.photoPaths) } ?: emptyList()
    }
}
