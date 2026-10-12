package com.example.diary.data.countdown

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.diary.MainActivity
import com.example.diary.R
import com.example.diary.data.local.CountdownEvent
import com.example.diary.receiver.CountdownAlarmReceiver
import com.example.diary.util.LocaleHelper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CountdownNotificationHelper {

    const val CHANNEL_ID = "countdown_reminder"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ctx = LocaleHelper.wrap(context)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            ctx.getString(R.string.notif_channel_countdown),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = ctx.getString(R.string.notif_channel_countdown_desc)
            enableVibration(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun notificationId(eventId: Long): Int =
        (eventId xor (eventId ushr 32)).toInt()

    fun show(
        context: Context,
        event: CountdownEvent,
        today: LocalDate = LocalDate.now(),
        titleOverride: String? = null
    ) {
        ensureChannel(context)
        val ctx = LocaleHelper.wrap(context)
        val state = DateMath.compute(event.date, event.repeatRule, event.plusOne, today)
        val subtitle = when (state) {
            is DateMath.CountState.Today -> ctx.getString(R.string.cd_state_today)
            is DateMath.CountState.Countdown ->
                ctx.getString(R.string.cd_state_remaining, state.days)
            is DateMath.CountState.Countup ->
                ctx.getString(R.string.cd_state_elapsed, state.days)
        }

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(CountdownAlarmReceiver.EXTRA_OPEN_COUNTDOWN_ID, event.id)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, notificationId(event.id), launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val text = ctx.getString(R.string.cd_notif_text, subtitle)
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titleOverride ?: event.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(notificationId(event.id), notification)
        } catch (_: SecurityException) {
        }
    }

    fun formatReminderTime(event: CountdownEvent): String =
        CountdownReminder.remindTime(event).format(DateTimeFormatter.ofPattern("HH:mm"))
}