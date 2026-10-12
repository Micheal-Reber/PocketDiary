package com.example.diary.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.diary.data.countdown.CountdownNotificationHelper
import com.example.diary.data.countdown.CountdownReminder
import com.example.diary.data.countdown.CountdownReminderScheduler
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.repository.CountdownRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class CountdownAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0L) return
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(appContext)
                val repo = CountdownRepository(db.countdownDao(), appContext)
                val event = repo.get(eventId) ?: return@launch
                if (event.remindDaysBefore < 0) return@launch

                CountdownNotificationHelper.show(appContext, event)

                // 重复事件推进到下一轮并重排；非重复仅本轮一次
                val nextAnchor = CountdownReminder.nextRepeatAnchor(event, LocalDate.now())
                if (nextAnchor != null) {
                    val at = CountdownReminder.remindAtForAnchor(
                        event, nextAnchor, ZoneId.systemDefault()
                    )
                    if (at > System.currentTimeMillis()) {
                        CountdownReminderScheduler.scheduleAt(appContext, event, at)
                    }
                } else {
                    CountdownReminderScheduler.cancel(appContext, eventId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CountdownAlarmReceiver", "countdown reminder failed for $eventId", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "countdown_event_id"
        const val EXTRA_OPEN_COUNTDOWN_ID = "open_countdown_id"
    }
}