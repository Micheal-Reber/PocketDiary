package com.example.diary.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.TodoItem
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoNotificationHelper
import com.example.diary.data.todo.TodoReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 开机 / 应用更新 / 改时间或时区 → 重排全部提醒，并补发已过期的非重复“错过提醒”。
 * （重复型过期闹钟由 schedule 的 past RTC 立即触发，无需在此发通知。）
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
        val isReplaced = action == Intent.ACTION_MY_PACKAGE_REPLACED
        val isTime = action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED
        if (!isBoot && !isReplaced && !isTime) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = TodoRepository(db.todoDao(), context)
                TodoReminderScheduler.rescheduleAll(context, repo)
                // 错过的非重复提醒：关机期间到期的，开机后补一条通知（用户仍可完成/清除）
                val now = System.currentTimeMillis()
                repo.getDueReminders(now)
                    .filter { it.repeatRule == TodoItem.REPEAT_NONE }
                    .forEach { item ->
                        TodoNotificationHelper.show(context, item.id, item.text, title = "错过的提醒")
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BootCompletedReceiver", "Failed to reschedule reminders", e)
            } finally { pending.finish() }
        }
    }
}
