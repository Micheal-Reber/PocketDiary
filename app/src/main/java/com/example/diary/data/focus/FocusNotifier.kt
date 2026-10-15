package com.example.diary.data.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.diary.MainActivity
import com.example.diary.R
import com.example.diary.data.local.FocusSession
import com.example.diary.util.LocaleHelper

/**
 * 番茄钟通知：独立的 focus_timer 渠道（与 todo_reminder / todo_alarm 分开，
 * 用户在系统设置里能分别关）。
 * 常驻通知用 LOW + 无声，仅「本段结束」才用高优先级提醒。
 */
object FocusNotifier {

    const val CHANNEL_ID = "focus_timer"
    const val CHANNEL_ALERT_ID = "focus_alert"

    private const val ONGOING_ID = 7101
    private const val DONE_ID = 7102

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ctx = LocaleHelper.wrap(context)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val ongoing = NotificationChannel(
            CHANNEL_ID, ctx.getString(R.string.notif_channel_focus), NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = ctx.getString(R.string.notif_channel_focus_desc)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(ongoing)

        val alert = NotificationChannel(
            CHANNEL_ALERT_ID, ctx.getString(R.string.notif_channel_focus_alert), NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.notif_channel_focus_alert_desc)
            enableVibration(true)
            setVibrationPattern(longArrayOf(0, 400, 300, 400))
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(alert)
    }

    private fun openIntent(context: Context, taskId: Long?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_FOCUS, true)
            putExtra(MainActivity.EXTRA_FOCUS_TASK_ID, taskId ?: -1L)
        }
        return PendingIntent.getActivity(
            context, ONGOING_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 前台常驻通知：标题=事项，副标题=剩余时间 + 段名 */
    fun buildOngoing(context: Context, record: FocusRecord, remainingMs: Long): Notification {
        val ctx = LocaleHelper.wrap(context)
        val phaseLabel = when (record.phase) {
            FocusSession.PHASE_FOCUS -> ctx.getString(R.string.focus_phase_focus)
            FocusSession.PHASE_LONG_BREAK -> ctx.getString(R.string.focus_phase_long_break)
            else -> ctx.getString(R.string.focus_phase_short_break)
        }
        val status = if (record.running) {
            ctx.getString(R.string.focus_status_running, FocusTimer.formatDuration(remainingMs))
        } else {
            ctx.getString(R.string.focus_status_paused, FocusTimer.formatDuration(remainingMs))
        }
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(record.taskTitle.ifBlank { ctx.getString(R.string.focus_task_fallback) })
            .setContentText("$phaseLabel · $status")
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openIntent(ctx, record.taskId))
            .build()
    }

    fun updateOngoing(context: Context, record: FocusRecord, remainingMs: Long) {
        ensureChannel(context)
        try {
            NotificationManagerCompat.from(context).notify(ONGOING_ID, buildOngoing(context, record, remainingMs))
        } catch (_: SecurityException) {
        }
    }

    /** 本段结束：高优先级提示一次 */
    fun showSegmentDone(context: Context, record: FocusRecord, nextLabel: String) {
        val ctx = LocaleHelper.wrap(context)
        ensureChannel(ctx)
        val noti = NotificationCompat.Builder(ctx, CHANNEL_ALERT_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(
                if (record.phase == FocusSession.PHASE_FOCUS) {
                    ctx.getString(R.string.focus_done_focus)
                } else {
                    ctx.getString(R.string.focus_done_break)
                }
            )
            .setContentText(nextLabel)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openIntent(ctx, record.taskId))
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(DONE_ID, noti)
        } catch (_: SecurityException) {
        }
    }

    fun cancelOngoing(context: Context) {
        NotificationManagerCompat.from(context).cancel(ONGOING_ID)
    }
}