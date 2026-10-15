package com.example.diary.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.os.SystemClock
import com.example.diary.R
import com.example.diary.data.calendar.SystemCalendarSync
import com.example.diary.data.countdown.CountdownNotificationHelper
import com.example.diary.data.focus.FocusAlarmScheduler
import com.example.diary.data.focus.FocusSessionStore
import com.example.diary.data.focus.FocusTimer
import com.example.diary.data.countdown.CountdownReminder
import com.example.diary.data.countdown.CountdownReminderScheduler
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.local.TodoItem
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoNotificationHelper
import com.example.diary.data.todo.TodoReminderScheduler
import com.example.diary.ui.widget.CountdownWidgetRefresh
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
                CountdownWidgetRefresh.scheduleMidnight(context)
                val db = AppDatabase.getInstance(context)
                val repo = TodoRepository(db.todoDao(), context)
                TodoReminderScheduler.rescheduleAll(context, repo)

                val countdownRepo = CountdownRepository(db.countdownDao(), context)
                val now = System.currentTimeMillis()
                countdownRepo.getAllWithReminder().forEach { event ->
                    val at = CountdownReminder.remindAt(event)
                    when {
                        at == null -> CountdownReminderScheduler.cancel(context, event.id)
                        at <= now && event.repeatRule == CountdownEvent.REPEAT_NONE ->
                            CountdownNotificationHelper.show(
                                context, event,
                                titleOverride = context.getString(R.string.notif_missed_title)
                            )
                        else -> CountdownReminderScheduler.sync(context, event)
                    }
                }
                SystemCalendarSync.rescheduleAll(context, countdownRepo)

                // 番茄钟：重启后 elapsedRealtime 归零，旧时间戳全部作废 ——
                // FocusSessionStore.load() 靠 bootInstant 哨兵识别并丢弃陈旧会话，
                // 所以这里只需在还剩有效会话时把到点闹钟重新排上。
                val focusStore = FocusSessionStore(context)
                focusStore.load()?.let { record ->
                    if (record.running && record.startElapsed > 0L) {
                        val remaining = FocusTimer.remainingMs(
                            record.toSession(), SystemClock.elapsedRealtime(), record.segments()
                        )
                        FocusAlarmScheduler.schedule(
                            context, record.taskId,
                            SystemClock.elapsedRealtime() + remaining.coerceAtLeast(0L)
                        )
                    }
                }

                // 错过的非重复提醒：关机期间到期的，开机后补一条通知（用户仍可完成/清除）
                repo.getDueReminders(now)
                    .filter { it.repeatRule == TodoItem.REPEAT_NONE }
                    .forEach { item ->
                        TodoNotificationHelper.show(context, item.id, item.text, title = context.getString(R.string.notif_missed_title))
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("BootCompletedReceiver", "Failed to reschedule reminders", e)
            } finally { pending.finish() }
        }
    }
}
