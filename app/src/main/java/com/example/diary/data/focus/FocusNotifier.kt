package com.example.diary.data.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
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
 *
 * 常驻通知支持两档：
 *  - 增强档（默认）：通知里直接给「暂停/放弃/跳过」三个按钮、分段彩色进度条
 *    （一个番茄 + 若干休息各占一段），并申请 Live Activity 让状态栏出现倒计时芯片；
 *  - 精简档：只有文字，时间靠展开后的系统时间。
 *
 * 思路参考开源项目 nsh07/Tomato 的常驻通知，但这里是按本项目数据结构独立实现的。
 */
object FocusNotifier {

    const val CHANNEL_ID = "focus_timer"
    const val CHANNEL_ALERT_ID = "focus_alert"

    private const val ONGOING_ID = 7101
    private const val DONE_ID = 7102

    // 三个动作按钮的 requestCode 各自独立，且与 TodoReminderScheduler 的哈希空间不重叠
    private const val REQ_PAUSE = 0x5F1C0001
    private const val REQ_STOP = 0x5F1C0002
    private const val REQ_SKIP = 0x5F1C0003

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

    private fun actionIntent(context: Context, requestCode: Int, action: String): PendingIntent =
        PendingIntent.getService(
            context, requestCode,
            Intent(context, FocusService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** 前台常驻通知：标题=段名 + 剩余时间，正文=下一段预告 */
    fun buildOngoing(
        context: Context,
        record: FocusRecord,
        remainingMs: Long,
        enhanced: Boolean
    ): Notification {
        val ctx = LocaleHelper.wrap(context)
        val phaseLabel = phaseLabel(ctx, record.phase)
        val remaining = FocusTimer.formatDuration(remainingMs)
        val title = if (record.running) {
            ctx.getString(R.string.focus_notif_running, phaseLabel, remaining)
        } else {
            ctx.getString(R.string.focus_notif_paused, phaseLabel, remaining)
        }
        val plan = FocusNotificationPlanner.plan(record, SystemClock.elapsedRealtime())
        val text = ctx.getString(
            R.string.focus_notif_next,
            phaseLabel(ctx, plan.nextPhase),
            FocusTimer.formatDuration(plan.nextPhaseMs)
        )

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_focus_live)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(record.running)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(FocusColors.phaseFocus)
            .setContentIntent(openIntent(ctx, record.taskId))

        // 计时中把「结束时刻」交给系统：状态栏 Live Activity 芯片靠它倒计时
        if (record.running) {
            builder.setWhen(System.currentTimeMillis() + remainingMs.coerceAtLeast(0L))
        }

        if (enhanced) {
            addActions(builder, ctx, record)
            applyProgress(builder, plan, record)
        }

        return builder.build()
    }

    private fun addActions(
        builder: NotificationCompat.Builder,
        ctx: Context,
        record: FocusRecord
    ) {
        builder.clearActions()
        val toggleLabel = if (record.running) ctx.getString(R.string.focus_pause) else ctx.getString(R.string.focus_resume)
        val toggleAction = if (record.running) FocusService.ACTION_PAUSE else FocusService.ACTION_RESUME
        builder.addAction(
            android.R.drawable.ic_media_pause, toggleLabel,
            actionIntent(ctx, REQ_PAUSE, toggleAction)
        )
        builder.addAction(
            android.R.drawable.ic_menu_close_clear_cancel, ctx.getString(R.string.focus_abandon),
            actionIntent(ctx, REQ_STOP, FocusService.ACTION_STOP)
        )
        builder.addAction(
            android.R.drawable.ic_media_next, ctx.getString(R.string.focus_skip),
            actionIntent(ctx, REQ_SKIP, FocusService.ACTION_SKIP)
        )
    }

    /**
     * 进度条：Android 16+ 用 ProgressStyle 的**分段**样式（专注段与休息段各占一格，
     * 颜色跟 App 主题走）；更低版本退化成普通线性进度条。
     */
    private fun applyProgress(
        builder: NotificationCompat.Builder,
        plan: FocusNotificationPlan,
        record: FocusRecord
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            builder.setProgress(100, plan.progressPercent, false)
            return
        }
        val style = NotificationCompat.ProgressStyle()
        plan.segments.forEach { segment ->
            style.addProgressSegment(
                NotificationCompat.ProgressStyle.Segment(segment.durationMs).setColor(
                    if (segment.role == FocusSegmentPlan.Role.FOCUS) {
                        FocusColors.phaseFocus
                    } else {
                        FocusColors.phaseBreak
                    }
                )
            )
        }
        builder.setStyle(style.setProgress(plan.progressMs))
        builder.setRequestPromotedOngoing(true)
        if (record.running) {
            val segments = record.segments()
            builder.setShortCriticalText(
                FocusTimer.formatDuration(
                    FocusTimer.remainingMs(record.toSession(), SystemClock.elapsedRealtime(), segments)
                        .coerceAtLeast(0L)
                )
            )
        }
    }

    fun updateOngoing(context: Context, record: FocusRecord, remainingMs: Long, enhanced: Boolean) {
        ensureChannel(context)
        try {
            NotificationManagerCompat.from(context)
                .notify(ONGOING_ID, buildOngoing(context, record, remainingMs, enhanced))
        } catch (_: SecurityException) {
        }
    }

    private fun phaseLabel(ctx: Context, phase: Int): String = when (phase) {
        FocusSession.PHASE_SHORT_BREAK -> ctx.getString(R.string.focus_phase_short_break)
        FocusSession.PHASE_LONG_BREAK -> ctx.getString(R.string.focus_phase_long_break)
        else -> ctx.getString(R.string.focus_phase_focus)
    }

    /** 本段结束：高优先级提示一次 */
    fun showSegmentDone(context: Context, record: FocusRecord, nextLabel: String) {
        val ctx = LocaleHelper.wrap(context)
        ensureChannel(ctx)
        val noti = NotificationCompat.Builder(ctx, CHANNEL_ALERT_ID)
            .setSmallIcon(R.drawable.ic_focus_live)
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