package com.example.diary.data.todo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.diary.data.local.TodoItem
import com.example.diary.receiver.TodoAlarmReceiver

object TodoReminderScheduler {

    private fun pendingIntent(context: Context, todoId: Long): PendingIntent {
        val intent = Intent(context, TodoAlarmReceiver::class.java).apply {
            putExtra("todo_id", todoId)
        }
        return PendingIntent.getBroadcast(
            context, todoId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, item: TodoItem) {
        val at = item.reminderAt ?: return
        if (item.done) { cancel(context, item.id); return }
        if (at <= System.currentTimeMillis() && item.repeatRule == TodoItem.REPEAT_NONE) {
            // 已过期且不重复，不排
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
}
