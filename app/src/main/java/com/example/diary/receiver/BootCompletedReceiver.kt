package com.example.diary.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val repo = TodoRepository(db.todoDao())
                TodoReminderScheduler.rescheduleAll(context, repo)
            } finally { pending.finish() }
        }
    }
}
