package com.example.diary.data.todo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.diary.data.local.TodoItem
import com.example.diary.receiver.TodoAlarmReceiver
import java.time.Instant
import java.time.ZoneId

object TodoReminderScheduler {

    /** 稍后提醒默认延后 5 分钟 */
    const val DEFAULT_SNOOZE_MILLIS = 5 * 60 * 1000L

    /**
     * 与通知 id 一致的 64-bit 混合：直接 todoId.toInt() 会在 id≥2³¹ 时截断撞号。
     */
    fun requestCodeFor(todoId: Long): Int = (todoId xor (todoId ushr 32)).toInt()

    private fun pendingIntent(context: Context, todoId: Long, snoozed: Boolean = false): PendingIntent {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra("todo_id", todoId)
            if (snoozed) putExtra("snoozed", true)
        }
        return PendingIntent.getBroadcast(
            context, requestCodeFor(todoId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** AlarmClockInfo.showIntent：系统闹钟状态/权限界面点开 → 待办 Tab。 */
    private fun showPendingIntent(context: Context, todoId: Long): PendingIntent {
        val intent = Intent(context, com.example.diary.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("open_todo_id", todoId)
        }
        return PendingIntent.getActivity(
            context, requestCodeFor(todoId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun setExactOrInexact(am: AlarmManager, at: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) } catch (_: Exception) {}
        }
    }

    fun schedule(context: Context, item: TodoItem) {
        val at = item.reminderAt ?: return
        if (item.done) { cancel(context, item.id); return }
        if (at <= System.currentTimeMillis() && item.repeatRule == TodoItem.REPEAT_NONE) {
            // 已过期且不重复，不排（补发错过提醒见 deliverMissed）
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, item.id)
        // 闹钟响铃模式：setAlarmClock 享有闹钟豁免（Doze/省电不延迟），系统状态栏显示闹钟图标
        if (item.alarmMode == TodoItem.MODE_RING) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showPendingIntent(context, item.id)), pi)
                return
            } catch (_: SecurityException) { /* 精确闹钟被拒 → 走下方降级 */ }
        }
        setExactOrInexact(am, at, pi)
    }

    /** 稍后提醒：短延时重排一次（snoozed 标记让接收器只响铃、不推进重复规则）。 */
    fun snooze(context: Context, item: TodoItem, delayMillis: Long = DEFAULT_SNOOZE_MILLIS) {
        if (item.done) return
        val at = System.currentTimeMillis() + delayMillis
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, item.id, snoozed = true)
        if (item.alarmMode == TodoItem.MODE_RING) {
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, showPendingIntent(context, item.id)), pi)
                return
            } catch (_: SecurityException) { /* fall through */ }
        }
        setExactOrInexact(am, at, pi)
    }

    fun cancel(context: Context, todoId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, todoId))
    }

    suspend fun rescheduleAll(context: Context, repo: com.example.diary.data.repository.TodoRepository) {
        val list = repo.getAllWithReminder()
        list.forEach { schedule(context, it) }
    }

    /**
     * 每天重复的下一次触发时刻：在系统时区按日历日 +1（DST 安全），
     * 若仍已过则继续逐日推进（关机错过），上限 365 天。
     */
    fun nextDailyOccurrence(fromMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
        val zone = ZoneId.systemDefault()
        var next = Instant.ofEpochMilli(fromMillis).atZone(zone).plusDays(1)
        val maxFuture = Instant.ofEpochMilli(nowMillis).atZone(zone).plusDays(365)
        while (next.toInstant().toEpochMilli() <= nowMillis && next < maxFuture) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }
}
