package com.example.diary.data.focus

import com.example.diary.data.local.FocusSession

/** 通知分段进度条的一段：时长 + 角色（决定颜色） */
data class FocusSegmentPlan(val durationMs: Int, val role: Role) {
    enum class Role { FOCUS, BREAK }
}

data class FocusNotificationPlan(
    /** 整个循环的段序列：[专注, 休息, 专注, 休息, ...] */
    val segments: List<FocusSegmentPlan>,
    /** 已走过的毫秒数：前面的段视为已完成，当前段按比例填充 */
    val progressMs: Int,
    /** 当前段总时长（降级成普通线性进度条时要用） */
    val currentPhaseMs: Int,
    val currentPhaseElapsedMs: Int,
    val nextPhase: Int,
    val nextPhaseMs: Long
) {
    val totalMs: Int get() = segments.sumOf { it.durationMs }
    val progressPercent: Int get() = if (totalMs <= 0) 0 else (progressMs * 100 / totalMs).coerceIn(0, 100)
}

/**
 * 把当前番茄钟状态翻译成通知要用的「分段进度条」描述。
 * 纯函数：不碰 Android API，所以能直接单测（见 FocusNotificationPlannerTest）。
 */
object FocusNotificationPlanner {

    fun plan(record: FocusRecord, nowElapsed: Long): FocusNotificationPlan {
        val segments = record.segments()
        val interval = segments.longBreakInterval.coerceAtLeast(2)
        val phaseLength = FocusTimer.phaseLengthMs(record.phase, segments)
        val remaining = FocusTimer.remainingMs(record.toSession(), nowElapsed, segments)
        val elapsed = (phaseLength - remaining).coerceIn(0L, phaseLength)
        val elapsedSeconds = elapsed / 1000L

        val focusSec = (segments.focusMs / 1000L).toInt().coerceAtLeast(1)
        val shortSec = (segments.shortBreakMs / 1000L).toInt().coerceAtLeast(1)
        val longSec = (segments.longBreakMs / 1000L).toInt().coerceAtLeast(1)

        val sequence = mutableListOf<FocusSegmentPlan>()
        repeat(interval) { index ->
            sequence += FocusSegmentPlan(focusSec, FocusSegmentPlan.Role.FOCUS)
            sequence += FocusSegmentPlan(
                if (index == interval - 1) longSec else shortSec,
                FocusSegmentPlan.Role.BREAK
            )
        }

        /**
         * cycleIndex = 已完成的专注段数（见 FocusTimer.nextPhase）：
         *  - 专注段：cycleIndex=c 就在第 c 个番茄上，下标 2c；
         *  - 休息段：它是第 c 个番茄之后的休息，cycleIndex 已经是 c+1，下标 2c-1。
         * 最后一个长休息时 cycleIndex == interval，靠这个公式才不会绕回首段。
         */
        val currentIndex = when (record.phase) {
            FocusSession.PHASE_FOCUS -> 2 * (record.cycleIndex.coerceAtLeast(0) % interval)
            else -> 2 * record.cycleIndex.coerceAtLeast(1) - 1
        }.coerceIn(0, sequence.lastIndex.coerceAtLeast(0))

        val completedBefore = sequence.take(currentIndex).sumOf { it.durationMs }
        val (nextPhase, _) = FocusTimer.nextPhase(record.phase, record.cycleIndex, interval)

        return FocusNotificationPlan(
            segments = sequence,
            progressMs = (completedBefore + elapsedSeconds).toInt().coerceAtLeast(0),
            currentPhaseMs = (phaseLength / 1000L).toInt().coerceAtLeast(1),
            currentPhaseElapsedMs = elapsedSeconds.toInt(),
            nextPhase = nextPhase,
            nextPhaseMs = FocusTimer.phaseLengthMs(nextPhase, segments)
        )
    }
}