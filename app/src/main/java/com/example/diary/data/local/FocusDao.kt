package com.example.diary.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusDao {

    @Query("SELECT * FROM focus_tasks WHERE archived = 0 ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActiveTasks(): Flow<List<FocusTask>>

    @Query("SELECT * FROM focus_tasks ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllTasks(): List<FocusTask>

    @Query("SELECT * FROM focus_tasks WHERE id = :id")
    suspend fun getTask(id: Long): FocusTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: FocusTask): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<FocusTask>)

    @Update
    suspend fun updateTask(task: FocusTask)

    @Query("DELETE FROM focus_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("DELETE FROM focus_tasks")
    suspend fun deleteAllTasks()

    // ---- sessions ----

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSession): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<FocusSession>)

    @Query("DELETE FROM focus_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT * FROM focus_sessions WHERE taskId = :taskId ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getSessionsForTask(taskId: Long, limit: Int): List<FocusSession>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<FocusSession>

    @Query("SELECT * FROM focus_sessions ORDER BY id ASC")
    suspend fun getAllSessions(): List<FocusSession>

    @Query("SELECT * FROM focus_sessions WHERE startedAt >= :since ORDER BY startedAt ASC")
    suspend fun getSessionsSince(since: Long): List<FocusSession>

    // ---- 统计（对齐 HabitDao 的 substr + CAST + GROUP BY 模式）----

    @Query("""
        SELECT date, SUM(actualMs) AS total FROM focus_sessions
        WHERE phase = :phase AND date LIKE :yearMonth || '%'
        GROUP BY date
    """)
    suspend fun getFocusTotalsForMonth(yearMonth: String, phase: Int): List<FocusTotalByDate>

    @Query("""
        SELECT CAST(substr(date, 9, 2) AS INTEGER) AS day, SUM(actualMs) AS total FROM focus_sessions
        WHERE phase = :phase AND date LIKE :yearMonth || '%'
        GROUP BY day ORDER BY day
    """)
    suspend fun getFocusDailyStats(yearMonth: String, phase: Int): List<FocusDailyStat>

    @Query("""
        SELECT CAST(substr(date, 6, 2) AS INTEGER) AS month, SUM(actualMs) AS total FROM focus_sessions
        WHERE phase = :phase AND date LIKE :year || '%'
        GROUP BY month ORDER BY month
    """)
    suspend fun getFocusMonthlyStats(year: Int, phase: Int): List<FocusMonthlyStat>

    /** 滚动窗口的原始行（窗口起点为「本周一往前推若干周」，SQL 无法直接表达） */
    @Query("SELECT * FROM focus_sessions WHERE phase = :phase AND date >= :startDate ORDER BY date ASC")
    suspend fun getFocusSessionsSince(startDate: String, phase: Int): List<FocusSession>

    @Query("SELECT COALESCE(SUM(actualMs), 0) FROM focus_sessions WHERE phase = :phase AND date = :date")
    suspend fun getFocusTotalForDate(date: String, phase: Int): Long

    /** 任意日期区间的按日合计（热力图 / 期间趋势共用一条查询） */
    @Query("""
        SELECT date, SUM(actualMs) AS total FROM focus_sessions
        WHERE phase = :phase AND date BETWEEN :startDate AND :endDate
        GROUP BY date
    """)
    suspend fun getFocusTotalsBetween(startDate: String, endDate: String, phase: Int): List<FocusTotalByDate>

    /** 区间的段数 / 完整番茄数 / 合计时长（空集也要返回一行 0，故 COALESCE + 起别名） */
    @Query("""
        SELECT COUNT(*) AS sessionCount,
               COALESCE(SUM(completed), 0) AS completedCount,
               COALESCE(SUM(actualMs), 0) AS totalMillis
        FROM focus_sessions
        WHERE phase = :phase AND date BETWEEN :startDate AND :endDate
    """)
    suspend fun getFocusRangeStat(startDate: String, endDate: String, phase: Int): FocusRangeStat

    /** 事项分布：taskId 可空（自由专注） */
    @Query("""
        SELECT taskId, SUM(actualMs) AS total, COALESCE(SUM(completed), 0) AS completedCount
        FROM focus_sessions
        WHERE phase = :phase AND date >= :startDate
        GROUP BY taskId
    """)
    suspend fun getFocusTotalsByTask(startDate: String, phase: Int): List<FocusTaskTotal>

    /**
     * 会话落库与任务累计时长写回必须原子：否则进程被杀在两步之间，
     * 会出现「有历史记录但卡片上没时长」或反之的漂移。
     */
    @Transaction
    suspend fun recordSessionWithCredit(
        session: FocusSession,
        creditTaskId: Long?,
        billableMs: Long,
        completed: Boolean
    ) {
        insertSession(session)
        if (creditTaskId == null || billableMs <= 0L) return
        getTask(creditTaskId)?.let { task ->
            updateTask(
                task.copy(
                    focusMillis = task.focusMillis + billableMs,
                    sessionCount = task.sessionCount + if (completed) 1 else 0
                )
            )
        }
    }
}