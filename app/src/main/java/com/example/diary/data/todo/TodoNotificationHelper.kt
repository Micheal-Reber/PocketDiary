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
import com.example.diary.util.LocaleHelper
// 运行时权限申请入口在 ui/todo/TodoListScreen（保存带提醒的待办时触发）

object TodoNotificationHelper {
    const val CHANNEL_ID = "todo_reminder"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ctx = LocaleHelper.wrap(context)
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.notif_channel_todo), NotificationManager.IMPORTANCE_HIGH).apply {
                description = ctx.getString(R.string.notif_channel_todo_desc)
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun show(context: Context, todoId: Long, text: String, title: String? = null) {
        val context = LocaleHelper.wrap(context)
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
            .setContentTitle(title ?: context.getString(R.string.notif_default_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, noti)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS 未授予时的兜底（运行时申请见 TodoListScreen）
        }
    }
}
