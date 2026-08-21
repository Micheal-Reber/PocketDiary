package com.example.diary.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.diary.data.local.DiaryDao
import com.example.diary.data.local.DiaryEntry
import kotlinx.coroutines.flow.Flow

/** Outcome of [DiaryRepository.saveEntry]. */
sealed interface SaveResult {
    /** Saved; [id] is the row id to remember for subsequent saves/deletes. */
    data class Success(val id: Long) : SaveResult

    /** Target date is occupied by a DIFFERENT entry (unique index on date). */
    data object DateConflict : SaveResult
}

class DiaryRepository(private val diaryDao: DiaryDao) {

    fun getAllEntries(): Flow<List<DiaryEntry>> = diaryDao.getAllEntries()

    suspend fun getEntryByDate(date: String): DiaryEntry? = diaryDao.getEntryByDate(date)

    /**
     * Insert or update an entry.
     *
     * - **id == 0** (new entry): looks up by date first so a double-tap save
     *   race updates instead of duplicating; otherwise inserts.
     * - **id != 0** (existing entry): plain UPDATE by primary key *including*
     *   the date column — i.e. "move/re-date this entry" semantics. Fails with
     *   [SaveResult.DateConflict] when the target date belongs to another row,
     *   leaving both rows untouched (unique index on `date` enforces it).
     */
    suspend fun saveEntry(entry: DiaryEntry): SaveResult {
        return if (entry.id != 0L) {
            updateOrNullConflict(entry) ?: SaveResult.DateConflict
        } else {
            val existing = diaryDao.getEntryByDate(entry.date)
            if (existing != null) {
                updateOrNullConflict(entry.copy(id = existing.id)) ?: SaveResult.DateConflict
            } else {
                try {
                    SaveResult.Success(diaryDao.insert(entry.copy(id = 0)))
                } catch (e: SQLiteConstraintException) {
                    // Race: a row landed on this date between lookup and insert.
                    val raced = diaryDao.getEntryByDate(entry.date)
                    if (raced != null) {
                        updateOrNullConflict(entry.copy(id = raced.id)) ?: SaveResult.DateConflict
                    } else {
                        SaveResult.DateConflict
                    }
                }
            }
        }
    }

    /** Returns Success, null on unique-constraint violation. */
    private suspend fun updateOrNullConflict(entry: DiaryEntry): SaveResult? {
        return try {
            diaryDao.update(entry.copy(updatedAt = System.currentTimeMillis()))
            SaveResult.Success(entry.id)
        } catch (e: SQLiteConstraintException) {
            null
        }
    }

    suspend fun deleteEntry(id: Long) {
        diaryDao.deleteById(id)
    }
}
