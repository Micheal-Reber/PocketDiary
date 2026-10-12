package com.example.diary.data.calendar

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.local.CountdownEvent
import java.time.LocalDate

/**
 * 直接读写系统日历（CalendarContract），与 App 内通知完全独立。
 * 无权限 / 无可写日历时静默降级，任何异常都不允许冒泡到 UI。
 */
object SystemCalendarManager {

    private const val ACCOUNT_NAME = "PocketDiary"

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 找一张本应用可写的本地日历。
     *
     * 刻意不新建日历：CalendarProvider 会对写 ACCOUNT_NAME 抛
     * IllegalArgumentException("Only sync adapters may write to account_name")——
     * 普通应用无 WRITE_SYNC_SETTINGS 也无法伪装同步适配器，唯一合法路径是复用既有日历。
     * 依次尝试：自己上次建过的 → 任意可写的本地日历。
     */
    private fun getOrCreateCalendarId(context: Context): Long? {
        if (!hasPermission(context)) return null
        return try {
            val resolver = context.contentResolver
            findOwnCalendarId(resolver)
                ?: resolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars._ID),
                    "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND " +
                        "${CalendarContract.Calendars.VISIBLE} = 1",
                    arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                    "${CalendarContract.Calendars.IS_PRIMARY} DESC"
                )?.use { if (it.moveToFirst()) return it.getLong(0) }
            null
        } catch (e: Exception) {
            Log.w("SystemCalendarManager", "findWritableCalendar failed", e)
            null
        }
    }

    private fun findOwnCalendarId(resolver: android.content.ContentResolver): Long? =
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.ACCOUNT_NAME} = ?",
            arrayOf(ACCOUNT_NAME),
            null
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun rrule(event: CountdownEvent): String? = when (event.repeatRule) {
        CountdownEvent.REPEAT_YEARLY -> "FREQ=YEARLY"
        CountdownEvent.REPEAT_MONTHLY -> "FREQ=MONTHLY"
        else -> null
    }

    /** 幂等写入：先查后比，无变化不写（避免每次启动都刷日历）。 */
    fun upsertEvent(context: Context, event: CountdownEvent, startMillis: Long) {
        if (!hasPermission(context)) return
        val calId = getOrCreateCalendarId(context) ?: return
        try {
            val resolver = context.contentResolver
            val expectedRrule = rrule(event)
            val selection =
                "${CalendarContract.Events.CALENDAR_ID} = ? AND " +
                    "${CalendarContract.Events.DESCRIPTION} LIKE ?"
            val args = arrayOf(calId.toString(), "$MARKER${event.id}|%")

            var match = false
            resolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(
                    CalendarContract.Events._ID,
                    CalendarContract.Events.DTSTART,
                    CalendarContract.Events.RRULE,
                    CalendarContract.Events.TITLE
                ),
                selection, args, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val sameStart = c.getLong(1) == startMillis
                    val idxRule = c.getColumnIndex(CalendarContract.Events.RRULE)
                    val sameRule = if (idxRule >= 0 && !c.isNull(idxRule)) {
                        c.getString(idxRule) == expectedRrule
                    } else {
                        expectedRrule == null
                    }
                    val idxTitle = c.getColumnIndex(CalendarContract.Events.TITLE)
                    val sameTitle = c.getString(idxTitle) == event.name
                    match = sameStart && sameRule && sameTitle
                    if (!match) {
                        resolver.delete(
                            CalendarContract.Events.CONTENT_URI,
                            "${CalendarContract.Events._ID} = ?",
                            arrayOf(c.getLong(0).toString())
                        )
                    }
                }
            }
            if (match) return

            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, event.name)
                // 所有权标记放 DESCRIPTION：sync_data1..10 与 account_name 均为
                // 「Only sync adapters may write」受限列，普通应用只能靠它识别自己的事件
                put(CalendarContract.Events.DESCRIPTION, "${MARKER}${event.id}|${event.date}")
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                put(CalendarContract.Events.DURATION, "P1D")
                expectedRrule?.let { put(CalendarContract.Events.RRULE, it) }
            }
            val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return
            val eventId = uri.lastPathSegment?.toLongOrNull() ?: return
            insertReminders(context, eventId, event)
        } catch (e: Exception) {
            Log.w("SystemCalendarManager", "upsertEvent failed for ${event.id}", e)
        }
    }

    private fun insertReminders(context: Context, eventId: Long, event: CountdownEvent) {
        if (event.remindDaysBefore < 0) return
        val minutes = event.remindDaysBefore * 24 * 60
        try {
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            context.contentResolver.insert(
                CalendarContract.Reminders.CONTENT_URI, values
            )
        } catch (e: Exception) {
            Log.w("SystemCalendarManager", "insertReminders failed", e)
        }
    }

    fun deleteEvent(context: Context, eventId: Long) {
        if (!hasPermission(context)) return
        try {
            context.contentResolver.delete(
                CalendarContract.Events.CONTENT_URI,
                "${CalendarContract.Events.DESCRIPTION} LIKE ?",
                arrayOf("$MARKER$eventId|%")
            )
        } catch (e: Exception) {
            Log.w("SystemCalendarManager", "deleteEvent failed", e)
        }
    }

    fun syncAll(context: Context, events: List<CountdownEvent>, today: LocalDate) {
        if (!hasPermission(context)) return
        events.forEach { event ->
            val anchor = DateMath.resolveAnchor(event.date, event.repeatRule, today)
            upsertEvent(context, event, midnightUtc(anchor))
        }
    }

    private fun midnightUtc(date: LocalDate): Long =
        date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

    private const val MARKER = "[PocketDiary_ID:"
}