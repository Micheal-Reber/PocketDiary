package com.example.diary.ui.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.time.LocalDateTime
import java.time.ZoneId

object CountdownWidgetRefresh {

    const val ACTION_NEXT = "com.example.diary.action.CD_WIDGET_NEXT"
    const val ACTION_PREV = "com.example.diary.action.CD_WIDGET_PREV"
    const val ACTION_MIDNIGHT = "com.example.diary.action.CD_WIDGET_MIDNIGHT"

    private const val REQUEST_CODE = 4200
    private const val MINUTE_AFTER_MIDNIGHT = 1L
    private const val TAG = "CountdownWidget"

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, CountdownWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val intent = Intent(context, CountdownWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        runCatching { context.sendBroadcast(intent) }
            .onFailure { Log.e(TAG, "Failed to broadcast widget update", it) }
    }

    fun scheduleMidnight(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = midnightPendingIntent(context)
        runCatching {
            if (canScheduleExact(alarmManager)) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis(), pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextMidnightMillis(), pending)
            }
        }.onFailure { Log.e(TAG, "Failed to schedule midnight refresh", it) }
    }

    fun cancelMidnight(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { alarmManager.cancel(midnightPendingIntent(context)) }
    }

    private fun midnightPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CountdownWidgetProvider::class.java).apply { action = ACTION_MIDNIGHT }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun nextMidnightMillis(): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(0, MINUTE_AFTER_MIDNIGHT.toInt())
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
