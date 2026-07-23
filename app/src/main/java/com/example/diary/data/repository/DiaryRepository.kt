package com.example.diary.data.repository

import com.example.diary.data.local.DiaryDao
import com.example.diary.data.local.DiaryEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class DiaryRepository(private val diaryDao: DiaryDao) {

    fun getAllEntries(): Flow<List<DiaryEntry>> = diaryDao.getAllEntries()

    suspend fun getEntryByDate(date: String): DiaryEntry? = diaryDao.getEntryByDate(date)

    suspend fun getDatesWithEntries(yearMonth: YearMonth): List<LocalDate> {
        val prefix = yearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        return diaryDao.getDatesWithEntries(prefix).map { LocalDate.parse(it) }
    }

    suspend fun saveEntry(entry: DiaryEntry): Long {
        val existing = diaryDao.getEntryByDate(entry.date)
        return if (existing != null) {
            diaryDao.update(entry.copy(id = existing.id, updatedAt = System.currentTimeMillis()))
            existing.id
        } else {
            diaryDao.insert(entry)
        }
    }

    suspend fun deleteEntry(id: Long) = diaryDao.deleteById(id)
}
