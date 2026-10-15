package com.example.diary.data.focus

import com.example.diary.data.local.FocusSession
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs

/**
 * 番茄钟纯逻辑：段长、剩余时间差值、阶段推进、日期口径、重启哨兵。
 * 不持有时间源 —— elapsedRealtime / wallClock 由调用方注入，因此全部可单测。
 *
 * 计时真值是「单调时钟基准的差值」，不是「每秒 -1」：
 * UI 循环、进程被杀、服务重启都不会让剩余时间漂移。
 */
object FocusTimer {

    data class Segments(
        val focusMs: Long,
        val shortBreakMs: Long,
        val longBreakMs: Long,
        val longBreakInterval: Int
    ) {
        companion object {
            fun of(
                focusMinutes: Int,
                shortBreakMinutes: Int,
                longBreakMinutes: Int,
                longBreakInterval: Int
            ) = Segments(
                focusMs = focusMinutes.coerceAtLeast(1) * 60_000L,
                shortBreakMs = shortBreakMinutes.coerceAtLeast(1) * 60_000L,
                longBreakMs = longBreakMinutes.coerceAtLeast(1) * 60_000L,
                longBreakInterval = longBreakInterval.coerceAtLeast(2)
            )
        }
    }

    /**
     * 当前段的计时快照。pauseElapsed == 0 表示未暂停。
     * cycleIndex = 已完成的专注段数（第一个专注段进行中时为 0）。
     */
    data class Session(
        val startElapsed: Long,
        val pauseElapsed: Long = 0L,
        val pausedAccumMs: Long = 0L,
        val phase: Int,
        val cycleIndex: Int,
        val running: Boolean
    )

    /** 本段已流逝的真实时长（暂停时段已扣除） */
    fun elapsedInSession(session: Session, nowElapsed: Long): Long {
        if (session.startElapsed <= 0L) return 0L
        val end = if (session.pauseElapsed != 0L) session.pauseElapsed else nowElapsed
        return (end - session.startElapsed - session.pausedAccumMs).coerceAtLeast(0L)
    }

    fun phaseLengthMs(phase: Int, segments: Segments): Long = when (phase) {
        FocusSession.PHASE_FOCUS -> segments.focusMs
        FocusSession.PHASE_LONG_BREAK -> segments.longBreakMs
        else -> segments.shortBreakMs
    }

    /** 剩余毫秒；<= 0 表示本段已走完 */
    fun remainingMs(session: Session, nowElapsed: Long, segments: Segments): Long =
        phaseLengthMs(session.phase, segments) - elapsedInSession(session, nowElapsed)

    /** 进度 0f..1f，用于环形进度 */
    fun progress(remainingMs: Long, phaseLengthMs: Long): Float {
        if (phaseLengthMs <= 0L) return 0f
        return (1f - remainingMs.toFloat() / phaseLengthMs).coerceIn(0f, 1f)
    }

    /**
     * 推进到下一段。
     * 专注 → 休息：第 cycleIndex+1 个番茄若是长休息间隔的整数倍走长休息，否则短休息；
     * 休息 → 专注：cycleIndex 不变（已经计入完成的番茄数）。
     */
    fun nextPhase(phase: Int, cycleIndex: Int, longBreakInterval: Int): Pair<Int, Int> {
        val interval = longBreakInterval.coerceAtLeast(2)
        return if (phase == FocusSession.PHASE_FOCUS) {
            val completed = cycleIndex + 1
            val next = if (completed % interval == 0) {
                FocusSession.PHASE_LONG_BREAK
            } else {
                FocusSession.PHASE_SHORT_BREAK
            }
            next to completed
        } else {
            FocusSession.PHASE_FOCUS to cycleIndex
        }
    }

    /** 开始一个新段：startNow=false 表示先摆着等用户点开始 */
    fun beginSession(
        phase: Int,
        cycleIndex: Int,
        nowElapsed: Long,
        startNow: Boolean
    ) = Session(
        startElapsed = if (startNow) nowElapsed else 0L,
        pauseElapsed = 0L,
        pausedAccumMs = 0L,
        phase = phase,
        cycleIndex = cycleIndex,
        running = startNow
    )

    /** 暂停：记下暂停时刻，后续 elapsed 以此为界冻结 */
    fun pause(session: Session, nowElapsed: Long) = session.copy(
        pauseElapsed = if (session.running) nowElapsed else session.pauseElapsed,
        running = false
    )

    /** 继续：把这次暂停累计进 pausedAccumMs，startElapsed 保持原值 */
    fun resume(session: Session, nowElapsed: Long): Session {
        if (session.running) return session
        val wasNeverStarted = session.startElapsed == 0L
        if (wasNeverStarted) return session.copy(startElapsed = nowElapsed, running = true)
        val accumulated = if (session.pauseElapsed != 0L) {
            (nowElapsed - session.pauseElapsed).coerceAtLeast(0L)
        } else {
            0L
        }
        return session.copy(
            pauseElapsed = 0L,
            pausedAccumMs = session.pausedAccumMs + accumulated,
            running = true
        )
    }

    /**
     * 统计归属的日历日：按「开始那一刻」的墙钟取本地日期。
     * 跨零点（23:50 起、00:15 止）记在前一天，与 habit_records 语义一致。
     */
    fun sessionDate(startWallClockMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(startWallClockMillis).atZone(zone).toLocalDate().toString()

    /**
     * 计入统计的实际时长：不超本段长度，且至少为 0。
     * 跳过/中断时按已流逝时长入账。
     */
    fun billableMs(session: Session, nowElapsed: Long, segments: Segments): Long =
        elapsedInSession(session, nowElapsed).coerceIn(0L, phaseLengthMs(session.phase, segments))

    /**
     * 本次启动的墙钟时刻。持久化这个哨兵即可识别「设备重启过」——
     * 因为 elapsedRealtime 重启归零，重启前的时间戳全部失效。
     */
    fun bootInstant(wallMillis: Long, elapsedRealtime: Long): Long = wallMillis - elapsedRealtime

    /** 哨兵对不上（容差留给网络授时）即判定为重启前的陈旧会话 */
    fun isStaleBootInstant(
        stored: Long,
        current: Long,
        toleranceMillis: Long = BOOT_INSTANT_TOLERANCE_MS
    ): Boolean = abs(stored - current) > toleranceMillis

    const val BOOT_INSTANT_TOLERANCE_MS = 60_000L

    /** mm:ss / h:mm:ss */
    fun formatDuration(ms: Long): String {
        val total = (ms.coerceAtLeast(0L) + 500L) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0L) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%02d:%02d".format(m, s)
        }
    }

    /** 累计时长的人性化展示：1h 20m / 45m / <1m */
    fun formatAccumulated(millis: Long): String {
        val minutes = millis / 60_000L
        if (minutes < 1L) return "<1m"
        val h = minutes / 60L
        val m = minutes % 60L
        return if (h > 0L) "${h}h ${m}m" else "${m}m"
    }
}