package com.example.diary.data.focus

import com.example.diary.data.local.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusNotificationPlannerTest {

    private fun record(
        phase: Int,
        cycleIndex: Int,
        focusMinutes: Int = 25,
        shortBreakMinutes: Int = 5,
        longBreakMinutes: Int = 15,
        interval: Int = 4,
        startElapsed: Long = 1_000_000L,
        pausedAccumMs: Long = 0L
    ) = FocusRecord(
        phase = phase,
        cycleIndex = cycleIndex,
        startElapsed = startElapsed,
        pausedAccumMs = pausedAccumMs,
        running = true,
        focusMinutes = focusMinutes,
        shortBreakMinutes = shortBreakMinutes,
        longBreakMinutes = longBreakMinutes,
        longBreakInterval = interval
    )

    /** 第 elapsedSeconds 秒时的运行态 */
    private fun at(record: FocusRecord, elapsedSeconds: Int) =
        record.copy(startElapsed = 1_000_000L - elapsedSeconds * 1000L)

    @Test
    fun `sequence is focus and break alternating with a long break at the end`() {
        val plan = FocusNotificationPlanner.plan(
            record(FocusSession.PHASE_FOCUS, 0, focusMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 15, interval = 4),
            nowElapsed = 1_000_000L
        )
        assertEquals(8, plan.segments.size)
        assertEquals(listOf(true, false, true, false, true, false, true, false), plan.segments.map { it.role == FocusSegmentPlan.Role.FOCUS })
        // ProgressStyle.Segment 的单位是**秒**
        assertEquals(25 * 60, plan.segments[0].durationMs)
        assertEquals(5 * 60, plan.segments[1].durationMs)
        assertEquals(15 * 60, plan.segments.last().durationMs)
    }

    @Test
    fun `first focus segment starts at zero progress`() {
        val plan = FocusNotificationPlanner.plan(at(record(FocusSession.PHASE_FOCUS, 0), 0), nowElapsed = 1_000_000L)
        assertEquals(0, plan.progressMs)
        assertEquals(0, plan.progressPercent)
        assertEquals(FocusSession.PHASE_SHORT_BREAK, plan.nextPhase)
        assertEquals(5 * 60_000L, plan.nextPhaseMs)
    }

    @Test
    fun `progress accumulates previous segments plus part of current`() {
        // 专注第 1 段跑到一半：前面没有段，当前段过了 25 分钟的一半
        val plan = FocusNotificationPlanner.plan(
            at(record(FocusSession.PHASE_FOCUS, 0, focusMinutes = 20), 600),
            nowElapsed = 1_000_000L
        )
        assertEquals(600, plan.progressMs)
        assertEquals(600, plan.currentPhaseElapsedMs)
        assertEquals(20 * 60, plan.currentPhaseMs)
    }

    @Test
    fun `break phase reports the break length as current phase`() {
        val plan = FocusNotificationPlanner.plan(
            at(record(FocusSession.PHASE_SHORT_BREAK, 1, shortBreakMinutes = 5), 30),
            nowElapsed = 1_000_000L
        )
        assertEquals(5 * 60, plan.currentPhaseMs)
        assertEquals(30, plan.currentPhaseElapsedMs)
        // 已完成 25 分钟专注 + 走过 30 秒休息
        assertEquals(25 * 60 + 30, plan.progressMs)
        assertEquals(FocusSession.PHASE_FOCUS, plan.nextPhase)
    }

    @Test
    fun `final long break stays on the last segment instead of wrapping to the first`() {
        // cycleIndex == interval 表示刚做完第 4 个番茄，接下来是长休息（序列最后一段）
        val plan = FocusNotificationPlanner.plan(
            record(FocusSession.PHASE_LONG_BREAK, 4, interval = 4, startElapsed = 1_000_000L),
            nowElapsed = 1_000_000L
        )
        val completed = plan.segments.dropLast(1).sumOf { it.durationMs }
        assertEquals(completed, plan.progressMs)
        assertEquals(15 * 60, plan.currentPhaseMs)
        assertEquals(FocusSession.PHASE_FOCUS, plan.nextPhase)
    }

    @Test
    fun `long break is chosen when the interval divides completed focus count`() {
        val plan = FocusNotificationPlanner.plan(
            record(FocusSession.PHASE_FOCUS, 3, interval = 4),
            nowElapsed = 1_000_000L
        )
        assertEquals(FocusSession.PHASE_LONG_BREAK, plan.nextPhase)
        assertEquals(15 * 60_000L, plan.nextPhaseMs)
    }

    @Test
    fun `paused time does not count towards progress`() {
        val base = record(FocusSession.PHASE_FOCUS, 0, focusMinutes = 20)
        val running = FocusNotificationPlanner.plan(at(base, 600), nowElapsed = 1_000_000L)
        val paused = FocusNotificationPlanner.plan(
            base.copy(
                // 400s 开跑 → 跑 300s 后暂停 → 暂停期间又累计 300s（pauseElapsed 停在 now），真实流逝 300s
                startElapsed = 1_000_000L - 600_000L,
                pauseElapsed = 1_000_000L,
                pausedAccumMs = 300_000L
            ),
            nowElapsed = 1_000_000L
        )
        assertEquals(300, paused.progressMs)
        assertTrue(paused.progressMs < running.progressMs)
    }

    @Test
    fun `percent stays inside zero to one hundred`() {
        listOf(0, 1, 2, 3, 4).forEach { cycle ->
            listOf(FocusSession.PHASE_FOCUS, FocusSession.PHASE_SHORT_BREAK, FocusSession.PHASE_LONG_BREAK)
                .forEach { phase ->
                    val plan = FocusNotificationPlanner.plan(
                        record(phase, cycle, startElapsed = 1_000_000L - 10_000_000L),
                        nowElapsed = 1_000_000L
                    )
                    assertTrue(plan.progressPercent in 0..100)
                }
        }
    }
}