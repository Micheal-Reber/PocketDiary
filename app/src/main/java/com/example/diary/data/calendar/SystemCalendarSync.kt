package com.example.diary.data.calendar

import android.content.Context
import android.util.Log
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.preferences.CalendarPreferences
import com.example.diary.data.repository.CountdownRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * 日历同步开关的总入口。save/delete 是同步调用点，故一律切到 IO 协程，
 * 开关读取也放进协程内，绝不用 runBlocking 阻塞调用方（可能来自主线程）。
 */
object SystemCalendarSync {

    fun onEventSaved(context: Context, event: CountdownEvent) {
        if (!SystemCalendarManager.hasPermission(context)) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!CalendarPreferences(appContext).syncToSystemCalendar.first()) return@launch
                val anchor = DateMath.resolveAnchor(event.date, event.repeatRule, LocalDate.now())
                SystemCalendarManager.upsertEvent(appContext, event, anchorMillis(anchor))
            } catch (e: Exception) {
                Log.w("SystemCalendarSync", "onEventSaved failed", e)
            }
        }
    }

    /**
     * 删除不判断开关：用户刚关掉同步时，残留的系统日历事件仍需清掉，
     * 否则关开关后会留下无人管理的孤儿事件。
     */
    fun onEventDeleted(context: Context, eventId: Long) {
        if (!SystemCalendarManager.hasPermission(context)) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            SystemCalendarManager.deleteEvent(appContext, eventId)
        }
    }

    fun rescheduleAll(context: Context, repo: CountdownRepository) {
        if (!SystemCalendarManager.hasPermission(context)) return
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!CalendarPreferences(appContext).syncToSystemCalendar.first()) return@launch
                SystemCalendarManager.syncAll(appContext, repo.getAllOnce(), LocalDate.now())
            } catch (e: Exception) {
                Log.w("SystemCalendarSync", "rescheduleAll failed", e)
            }
        }
    }

    /** 设置页开关打开后立即全量对账一次。 */
    fun syncNow(context: Context, repo: CountdownRepository) = rescheduleAll(context, repo)

    /**
     * 冷启动兜底：开关开着就补一次全量对账。
     * 幂等（先查后比、无变化不写），所以每次启动调用无副作用，
     * 但能自动修好"开关先开、权限后授"或"日历被用户手动删掉"的情况。
     */
    fun catchUpOnLaunch(context: Context, repo: CountdownRepository) = rescheduleAll(context, repo)

    private fun anchorMillis(anchor: LocalDate): Long =
        anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}