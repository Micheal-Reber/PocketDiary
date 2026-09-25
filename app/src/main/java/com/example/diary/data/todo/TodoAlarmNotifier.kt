package com.example.diary.data.todo

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
import com.example.diary.data.local.TodoItem
import com.example.diary.ui.todo.TodoRingActivity

/**
 * 闹钟响铃模式的通知：专属渠道（闹钟铃声+长震动+免打扰穿透），
 * 全屏 Intent 拉起 [TodoRingActivity] 持续响铃；全屏拉起失败时通知铃声兜底响一次。
 */
object TodoAlarmNotifier {
    const val CHANNEL_ID = "todo_alarm"
    const val CHANNEL_NAME = "待办闹钟"

    /** 与 TodoNotificationHelper.show 相同的 id 混合，二选一发同一条 */
    private fun notificationId(todoId: Long): Int = (todoId xor (todoId ushr 32)).toInt()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                val alarmAudio = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "待办到点闹钟响铃"
                    setSound(alarmSound, alarmAudio)
                    enableVibration(true)
                    setVibrationPattern(longArrayOf(0, 500, 500, 500, 500))
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun show(context: Context, item: TodoItem, silent: Boolean = false) {
        ensureChannel(context)
        val id = notificationId(item.id)

        val ringIntent = Intent(context, TodoRingActivity::class.java).apply {
            putExtra(TodoRingActivity.EXTRA_TODO_ID, item.id)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val ringPi = PendingIntent.getActivity(
            context, id, ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_todo_id", item.id)
        }
        val contentPi = PendingIntent.getActivity(
            context, id, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("闹钟")
            .setContentText(item.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(ringPi, true)
            .setContentIntent(contentPi)
            .setAutoCancel(false)
        if (silent) builder.setSound(null) // 回入口通知：覆盖渠道铃声，不响第二次
        val noti = builder.build()
        try {
            NotificationManagerCompat.from(context).notify(id, noti)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 未授予：全屏 Intent 仍可能拉起，响铃页自会兜底
        }
    }

    fun cancel(context: Context, todoId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(todoId))
    }
}
