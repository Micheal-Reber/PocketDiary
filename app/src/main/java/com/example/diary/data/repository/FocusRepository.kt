package com.example.diary.data.repository

import android.content.Context
import com.example.diary.data.focus.FocusRecord
import com.example.diary.data.focus.FocusTimer
import com.example.diary.data.local.FocusDao
import com.example.diary.data.local.FocusSession
import com.example.diary.data.local.FocusTask
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.preferences.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * 专注任务薄仓库——DAO 直通 + 会话入账（对齐 TodoRepository 的薄仓库定位）。
 * 会话落库与任务累计时长写回放在同一事务里，避免两者漂移。
 */
class FocusRepository(
    private val dao: FocusDao,
    private val context: Context? = null,
) {

    fun observeActiveTasks(): Flow<List<FocusTask>> = dao.observeActiveTasks()

    suspend fun getTask(id: Long): FocusTask? = dao.getTask(id)

    suspend fun getAllTasks(): List<FocusTask> = dao.getAllTasks()

    suspend fun saveTask(task: FocusTask): Long =
        if (task.id == 0L) dao.insertTask(task) else { dao.updateTask(task); task.id }

    /** 归档而非删除：历史专注时长仍留在统计里 */
    suspend fun archiveTask(id: Long) {
        dao.getTask(id)?.let { dao.updateTask(it.copy(archived = true)) }
    }

    suspend fun deleteTask(id: Long) = dao.deleteTaskById(id)

    suspend fun addTask(title: String, sortOrder: Int): Long =
        dao.insertTask(FocusTask(title = title, sortOrder = sortOrder))

    /**
     * 结束一段专注并入账。会话行与任务累计时长在同一个事务里提交，
     * 否则进程被杀在两步之间会造成漂移。
     * 只对专注段回写累计时长，休息段只留历史。
     */
    suspend fun recordSession(record: FocusRecord, billableMs: Long, completed: Boolean, endedAt: Long) {
        val segments = record.segments()
        val session = FocusSession(
            taskId = record.taskId,
            date = FocusTimer.sessionDate(record.startWallClock),
            startedAt = record.startWallClock,
            endedAt = endedAt,
            plannedMs = FocusTimer.phaseLengthMs(record.phase, segments),
            actualMs = billableMs,
            phase = record.phase,
            completed = completed,
            cycleIndex = record.cycleIndex
        )
        val creditTaskId = record.taskId.takeIf { record.phase == FocusSession.PHASE_FOCUS }
        dao.recordSessionWithCredit(session, creditTaskId, billableMs, completed)
    }

    // ---- 统计（全部只认专注段，休息段不计入「专注时长」）----

    suspend fun focusMinutesForMonth(yearMonth: YearMonth): Map<LocalDate, Long> =
        dao.getFocusTotalsForMonth(yearMonth.toString(), FocusPreferences.STAT_PHASE)
            .associate { LocalDate.parse(it.date) to it.total }

    suspend fun dailyFocusMillis(yearMonth: YearMonth): List<Pair<Int, Long>> =
        dao.getFocusDailyStats(yearMonth.toString(), FocusPreferences.STAT_PHASE)
            .map { it.day to it.total }

    suspend fun monthlyFocusMillis(year: Int): List<Pair<Int, Long>> =
        dao.getFocusMonthlyStats(year, FocusPreferences.STAT_PHASE)
            .map { it.month to it.total }

    /**
     * 滚动 10 个周一基准自然周的专注毫秒，weekIndex 1..10（10 = 本周）。
     * SQL 的 GROUP BY 表达不了「以本周为终点向后滚」的窗口，故取原始行在 Kotlin 分桶
     * ——与 HabitRepository.getRecentWeeklyStats() 完全同构。
     */
    suspend fun recentWeeklyFocusMillis(today: LocalDate = LocalDate.now()): List<Pair<Int, Long>> {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val windowStart = monday.minusWeeks(9)
        val sessions = dao.getFocusSessionsSince(windowStart.toString(), FocusPreferences.STAT_PHASE)
        val totals = LongArray(10)
        sessions.forEach { session ->
            val idx = ChronoUnit.WEEKS.between(windowStart, LocalDate.parse(session.date)).toInt()
            if (idx in 0..9) totals[idx] += session.actualMs
        }
        return (0..9).filter { totals[it] > 0L }.map { (it + 1) to totals[it] }
    }

    suspend fun focusTotalSince(days: Int): Long {
        val since = LocalDate.now().minusDays(days.toLong())
        return dao.getFocusSessionsSince(since.toString(), FocusPreferences.STAT_PHASE)
            .sumOf { it.actualMs }
    }

    suspend fun focusTotalForDate(date: LocalDate): Long =
        dao.getFocusTotalForDate(date.toString(), FocusPreferences.STAT_PHASE)

    suspend fun recentSessions(limit: Int): List<FocusSession> = dao.getRecentSessions(limit)

    suspend fun sessionsForTask(taskId: Long, limit: Int): List<FocusSession> =
        dao.getSessionsForTask(taskId, limit)

    /** 当前生效的番茄钟段长（读 DataStore，缺省用内置默认） */
    suspend fun currentSegments(context: Context): FocusTimer.Segments {
        val prefs = context.dataStore.data.first()
        return FocusTimer.Segments.of(
            focusMinutes = prefs[FocusPreferences.FOCUS_MINUTES_KEY] ?: FocusPreferences.DEFAULT_FOCUS_MINUTES,
            shortBreakMinutes = prefs[FocusPreferences.SHORT_BREAK_MINUTES_KEY] ?: FocusPreferences.DEFAULT_SHORT_BREAK_MINUTES,
            longBreakMinutes = prefs[FocusPreferences.LONG_BREAK_MINUTES_KEY] ?: FocusPreferences.DEFAULT_LONG_BREAK_MINUTES,
            longBreakInterval = prefs[FocusPreferences.LONG_BREAK_INTERVAL_KEY] ?: FocusPreferences.DEFAULT_LONG_BREAK_INTERVAL
        )
    }

    fun formatMillis(millis: Long): String =
        if (millis >= 3_600_000L) String.format(Locale.getDefault(), "%.1fh", millis / 3_600_000.0)
        else "${millis / 60_000L}m"
}