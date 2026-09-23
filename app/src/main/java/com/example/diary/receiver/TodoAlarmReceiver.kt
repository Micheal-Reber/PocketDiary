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

class TodoAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("todo_id", -1L)
        if (id == -1L) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = TodoRepository(db.todoDao(), context)
                val item = repo.get(id) ?: return@launch
                if (item.done) return@launch
                TodoNotificationHelper.show(context, item.id, item.text)
                if (item.repeatRule == TodoItem.REPEAT_DAILY) {
                    // 日历日 +1（系统时区），DST 不漂移；错过多天自动跳到未来
                    // save() 内会同步 schedule 下一次
                    val finalAt = TodoReminderScheduler.nextDailyOccurrence(
                        fromMillis = item.reminderAt ?: System.currentTimeMillis()
                    )
                    repo.save(item.copy(reminderAt = finalAt))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("TodoAlarmReceiver", "Failed to handle alarm for todo $id", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
