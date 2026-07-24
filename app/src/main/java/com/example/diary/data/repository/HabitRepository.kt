package com.example.diary.data.repository

import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitDao
import com.example.diary.data.local.HabitRecord
import com.example.diary.data.local.MonthlyStat
import com.example.diary.data.local.YearlyStat
import kotlinx.coroutines.flow.Flow

class HabitRepository(private val habitDao: HabitDao) {

    fun getActiveHabits(): Flow<List<Habit>> = habitDao.getActiveHabits()

    suspend fun addHabit(name: String, emoji: String, colorIndex: Int): Long {
        return habitDao.insert(Habit(name = name, emoji = emoji, colorIndex = colorIndex))
    }

    suspend fun deleteHabit(id: Long) = habitDao.deleteById(id)

    suspend fun getRecordsForDate(date: String) = habitDao.getRecordsForDate(date)

    suspend fun toggleCheckIn(habitId: Long, date: String) {
        val existing = habitDao.getRecord(habitId, date)
        if (existing != null) {
            habitDao.deleteRecord(habitId, date)
        } else {
            habitDao.insertRecord(HabitRecord(habitId = habitId, date = date))
        }
    }

    suspend fun getMonthlyStats(year: Int): List<MonthlyStat> =
        habitDao.getMonthlyStats(year)

    suspend fun getYearlyStats(): List<YearlyStat> =
        habitDao.getYearlyStats()

    suspend fun getCheckInDates(habitId: Long, yearMonth: String): List<String> =
        habitDao.getCheckInDates(habitId, yearMonth)
}
