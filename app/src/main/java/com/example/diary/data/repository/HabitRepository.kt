package com.example.diary.data.repository

import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitDao
import com.example.diary.data.local.HabitRecord
import com.example.diary.data.local.MonthlyStat
import com.example.diary.data.local.DailyStat
import com.example.diary.data.local.RecentWeeklyStat
import com.example.diary.data.local.YearlyStat
import kotlinx.coroutines.flow.Flow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

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

    /**
     * Check-in counts for the last 10 Monday-based calendar weeks ending at the
     * current week (weekIndex 1..10, 10 = 本周). The window is anchored to the
     * current week's Monday minus 9 weeks; records are fetched raw and bucketed
     * here because a SQL GROUP BY on week-of-year can't express that window.
     */
    suspend fun getRecentWeeklyStats(today: LocalDate = LocalDate.now()): List<RecentWeeklyStat> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val windowStart = monday.minusWeeks(9)
        val records = habitDao.getRecordsSince(windowStart.toString())
        return records.groupBy { it.habitId }.flatMap { (habitId, recs) ->
            val counts = IntArray(10)
            recs.forEach { r ->
                // windowStart is a Monday, so WEEKS.between floors any date in
                // week k of the window to exactly k.
                val idx = ChronoUnit.WEEKS.between(windowStart, LocalDate.parse(r.date)).toInt()
                if (idx in 0..9) counts[idx]++
            }
            (0..9).filter { counts[it] > 0 }.map { RecentWeeklyStat(it + 1, habitId, counts[it]) }
        }
    }

    suspend fun getDailyStats(yearMonth: String): List<DailyStat> =
        habitDao.getDailyStats(yearMonth)

    suspend fun getCheckInDates(habitId: Long, yearMonth: String): List<String> =
        habitDao.getCheckInDates(habitId, yearMonth)
}
