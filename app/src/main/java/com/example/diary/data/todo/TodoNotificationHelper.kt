package com.example.diary.data.todo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.diary.MainActivity
import com.example.diary.R

object TodoNotificationHelper {
    const val CHANNEL_ID = "todo_reminder"
    const val CHANNEL_NAME = "待办提醒"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "待办到期提醒"
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun show(context: Context, todoId: Long, text: String) {
        ensureChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("open_todo_id", todoId)
        }
        // 使用 hashCode 避免 todoId.toInt() 溢出
        val notificationId = (todoId xor (todoId ushr 32)).toInt()
        val pi = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val noti = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("待办提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, noti)
        } catch (_: SecurityException) { /* POST_NOTIFICATIONS 未授予，静默 */ }
    }
}
