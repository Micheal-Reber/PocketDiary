package com.example.diary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE isArchived = 0 ORDER BY sortOrder ASC")
    fun getActiveHabits(): Flow<List<Habit>>

    /** 全部习惯（含已归档）——备份导出用 */
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC")
    suspend fun getAllHabits(): List<Habit>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(habit: Habit): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(habits: List<Habit>)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()

    // Habit records
    @Query("SELECT * FROM habit_records WHERE date = :date")
    suspend fun getRecordsForDate(date: String): List<HabitRecord>

    @Query("SELECT * FROM habit_records WHERE habitId = :habitId AND date = :date")
    suspend fun getRecord(habitId: Long, date: String): HabitRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HabitRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllRecords(records: List<HabitRecord>)

    @Query("DELETE FROM habit_records WHERE habitId = :habitId AND date = :date")
    suspend fun deleteRecord(habitId: Long, date: String)

    @Query("DELETE FROM habit_records")
    suspend fun deleteAllRecords()

    /** 月历一次查全月打卡（habitId+date），避免每习惯一条 SQL 的 N+1。 */
    @Query("SELECT habitId, date FROM habit_records WHERE date LIKE :yearMonth || '%'")
    suspend fun getCheckInsForMonth(yearMonth: String): List<HabitCheckInRow>

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

    @Query("""
        SELECT CAST(substr(date, 9, 2) AS INTEGER) AS day, habitId, COUNT(*) AS count
        FROM habit_records
        WHERE date LIKE :yearMonth || '%'
        GROUP BY day, habitId
        ORDER BY day
    """)
    suspend fun getDailyStats(yearMonth: String): List<DailyStat>

    // Raw records from a start date onward — used for the rolling "last 10
    // weeks" window, which is bucketed in Kotlin (SQLite %W weeks don't align
    // with a backwards window ending at the current week).
    @Query("SELECT * FROM habit_records WHERE date >= :startDate")
    suspend fun getRecordsSince(startDate: String): List<HabitRecord>
}
