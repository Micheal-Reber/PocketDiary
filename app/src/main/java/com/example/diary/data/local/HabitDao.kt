package com.example.diary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder ASC")
    fun getActiveHabits(): Flow<List<Habit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: Habit): Long

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Habit records
    @Query("SELECT * FROM habit_records WHERE date = :date")
    suspend fun getRecordsForDate(date: String): List<HabitRecord>

    @Query("SELECT * FROM habit_records WHERE habitId = :habitId AND date = :date")
    suspend fun getRecord(habitId: Long, date: String): HabitRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HabitRecord)

    @Query("DELETE FROM habit_records WHERE habitId = :habitId AND date = :date")
    suspend fun deleteRecord(habitId: Long, date: String)

    @Query("SELECT DISTINCT date FROM habit_records WHERE habitId = :habitId AND date LIKE :yearMonth || '%'")
    suspend fun getCheckInDates(habitId: Long, yearMonth: String): List<String>

    // Statistics
    @Query("""
        SELECT CAST(substr(date, 6, 2) AS INTEGER) AS month, habitId, COUNT(*) AS count
        FROM habit_records
        WHERE date LIKE :year || '%'
        GROUP BY month, habitId
        ORDER BY month
    """)
    suspend fun getMonthlyStats(year: Int): List<MonthlyStat>

    @Query("""
        SELECT CAST(substr(date, 1, 4) AS INTEGER) AS year, habitId, COUNT(*) AS count
        FROM habit_records
        GROUP BY year, habitId
        ORDER BY year
    """)
    suspend fun getYearlyStats(): List<YearlyStat>
}
