package com.example.diary.data.countdown

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.diary.data.local.CountdownEvent
import com.example.diary.receiver.CountdownAlarmReceiver

object CountdownReminderScheduler {

    /**
     * 与待办同思路的 64-bit 混合：直接 toInt() 会在 id>=2^31 时截断撞号。
     * requestCode 只由 eventId 决定（不随提醒点数变化），
     * 因此 cancel 永远精确命中，不会像多槽位方案那样漏删旧闹钟。
     */
    fun requestCodeFor(eventId: Long): Int = (eventId xor (eventId ushr 32)).toInt()

    private fun pendingIntent(context: Context, eventId: Long): PendingIntent {
        val intent = Intent(context, CountdownAlarmReceiver::class.java).apply {
            putExtra(CountdownAlarmReceiver.EXTRA_EVENT_ID, eventId)
        }
        return PendingIntent.getBroadcast(
            context, requestCodeFor(eventId), intent,
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

    /** 保存后统一入口：有关闭提醒、时刻已过、事件已过期则取消，否则排闹钟。 */
    fun sync(context: Context, event: CountdownEvent) {
        val at = CountdownReminder.remindAt(event)
        if (at == null || at <= System.currentTimeMillis()) {
            cancel(context, event.id)
            return
        }
        scheduleAt(context, event, at)
    }

    /** 重复事件发出后按已确定的下一轮锚点直接排，不重新解析今天所处的锚点。 */
    fun scheduleAt(context: Context, event: CountdownEvent, at: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        setExactOrInexact(am, at, pendingIntent(context, event.id))
    }

    fun cancel(context: Context, eventId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, eventId))
    }

    fun rescheduleAll(context: Context, events: List<CountdownEvent>) {
        events.forEach { sync(context, it) }
    }
}