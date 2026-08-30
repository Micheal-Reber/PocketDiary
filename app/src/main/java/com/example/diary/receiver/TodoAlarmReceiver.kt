package com.example.diary.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.TodoItem
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoNotificationHelper
import com.example.diary.data.todo.TodoReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("todo_id", -1L)
        if (id == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = TodoRepository(db.todoDao())
                val item = repo.get(id) ?: return@launch
                if (item.done) return@launch
                TodoNotificationHelper.show(context, item.id, item.text)
                if (item.repeatRule == TodoItem.REPEAT_DAILY) {
                    val next = (item.reminderAt ?: System.currentTimeMillis()) + 24 * 60 * 60 * 1000L
                    // 保证 next 在未来（若关机错过多天，逐日 + 直到未来）
                    var n = next
                    val now = System.currentTimeMillis()
                    // 安全上限：最多向前推算 365 天，防止极端情况死循环
                    val maxFuture = now + 365L * 24 * 60 * 60 * 1000L
                    while (n <= now && n < maxFuture) n += 24 * 60 * 60 * 1000L
                    val finalAt = if (n < maxFuture) n else maxFuture
                    repo.save(item.copy(reminderAt = finalAt))
                    TodoReminderScheduler.schedule(context, item.copy(reminderAt = finalAt))
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
