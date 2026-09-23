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

    /**
     * 与通知 id 一致的 64-bit 混合：直接 todoId.toInt() 会在 id≥2³¹ 时截断撞号。
     */
    fun requestCodeFor(todoId: Long): Int = (todoId xor (todoId ushr 32)).toInt()

    private fun pendingIntent(context: Context, todoId: Long): PendingIntent {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra("todo_id", todoId)
        }
        return PendingIntent.getBroadcast(
            context, requestCodeFor(todoId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
