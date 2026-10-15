package com.example.diary.data.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 番茄钟到点唤醒。
 *
 * 用 ELAPSED_REALTIME_WAKEUP 而非 RTC：与 SystemClock.elapsedRealtime() 同一时钟域，
 * 用户改时区/改时间不会让正在跑的计时漂移。
 * 用 PendingIntent.getForegroundService：精确闹钟允许从后台拉起前台服务（合法路径），
 * 普通广播则会被后台启动限制拦掉。
 */
object FocusAlarmScheduler {

    /**
     * 与 TodoReminderScheduler.requestCodeFor 的哈希空间刻意不重叠 ——
     * 否则专注到点会覆盖同 id 的待办闹钟，反之亦然。
     */
    private const val FOCUS_REQUEST_CODE_BASE = 0x5F0C0000
    private const val REQUEST_CODE_MASK = 0xFFFF

    private fun requestCodeFor(taskId: Long?): Int =
        FOCUS_REQUEST_CODE_BASE + ((taskId ?: 0L).hashCode() and REQUEST_CODE_MASK)

    private fun pendingIntent(context: Context, taskId: Long?): PendingIntent {
        val intent = Intent(context, FocusService::class.java).apply {
            action = FocusService.ACTION_EXPIRE
            putExtra(FocusService.EXTRA_TASK_ID, taskId ?: -1L)
        }
        return PendingIntent.getForegroundService(
            context,
            requestCodeFor(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 排到点唤醒；taskId 变化时用不同 requestCode，旧的那次要显式取消 */
    fun schedule(context: Context, taskId: Long?, triggerAtElapsedRealtime: Long, previousTaskId: Long? = null) {
        if (previousTaskId != null && previousTaskId != taskId) {
            cancel(context, previousTaskId)
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, taskId)
        try {
            if (canScheduleExact(am)) {
                am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtElapsedRealtime, pi)
            } else {
                setInexact(am, triggerAtElapsedRealtime, pi)
            }
        } catch (e: SecurityException) {
            // 精确闹钟权限可能在 canScheduleExact 检查与本次调用之间被撤销
            Log.w("FocusAlarmScheduler", "Exact alarm rejected, degrading", e)
            setInexact(am, triggerAtElapsedRealtime, pi)
        }
    }

    fun cancel(context: Context, taskId: Long?) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, taskId))
    }

    /** Doze 会把非精确闹钟攒批（约 9 分钟一批），所以降级路径可能晚触发 */
    private fun setInexact(am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        try {
            am.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        } catch (e: SecurityException) {
            Log.w("FocusAlarmScheduler", "Inexact alarm also rejected", e)
        }
    }

    private fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
}