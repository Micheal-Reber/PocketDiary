package com.example.diary.data.focus

import com.example.diary.data.local.FocusSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FocusTimerTest {

    private val segments = FocusTimer.Segments.of(
        focusMinutes = 25,
        shortBreakMinutes = 5,
        longBreakMinutes = 15,
        longBreakInterval = 4
    )

    // startElapsed == 0 是「尚未开始」的哨兵值，所以基准取一个非零的开机时刻
    private val base = 1_000_000L

    private fun running(start: Long = base, phase: Int = FocusSession.PHASE_FOCUS, cycle: Int = 0) =
        FocusTimer.Session(
            startElapsed = start,
            phase = phase,
            cycleIndex = cycle,
            running = true
        )

    @Test
    fun segments_convertMinutesToMillis() {
        assertEquals(25 * 60_000L, segments.focusMs)
        assertEquals(5 * 60_000L, segments.shortBreakMs)
        assertEquals(15 * 60_000L, segments.longBreakMs)
        assertEquals(4, segments.longBreakInterval)
    }

    @Test
    fun segments_clampDegenerateInput() {
        val clamped = FocusTimer.Segments.of(0, 0, 0, 0)
        assertEquals(60_000L, clamped.focusMs)
        assertTrue("interval must be >= 2", clamped.longBreakInterval >= 2)
    }

    @Test
    fun elapsed_isDifferenceFromStart() {
        assertEquals(10_000L, FocusTimer.elapsedInSession(running(start = 1000L), nowElapsed = 11_000L))
        assertEquals(0L, FocusTimer.elapsedInSession(running(), nowElapsed = base))
        assertEquals(7_000L, FocusTimer.elapsedInSession(running(), nowElapsed = base + 7_000L))
    }

    @Test
    fun elapsed_notStarted_isZero() {
        val parked = FocusTimer.Session(startElapsed = 0L, pauseElapsed = 0L, pausedAccumMs = 0L, phase = FocusSession.PHASE_FOCUS, cycleIndex = 0, running = false)
        assertEquals(0L, FocusTimer.elapsedInSession(parked, nowElapsed = 99_999L))
    }

    @Test
    fun elapsed_whenPaused_freezesAtPauseInstant() {
        val session = running(start = 1000L).copy(pauseElapsed = 5000L, running = false)
        assertEquals(4000L, FocusTimer.elapsedInSession(session, nowElapsed = 999_999L))
    }

    @Test
    fun elapsed_neverNegative() {
        val session = FocusTimer.Session(
            startElapsed = 10_000L, pauseElapsed = 0L, pausedAccumMs = 50_000L,
            phase = FocusSession.PHASE_FOCUS, cycleIndex = 0, running = true
        )
        assertEquals(0L, FocusTimer.elapsedInSession(session, nowElapsed = 10_000L))
    }

    @Test
    fun pause_andResume_accumulatesPausedTime() {
        val started = running(start = 1000L)
        val paused = FocusTimer.pause(started, nowElapsed = 6000L)
        assertFalse(paused.running)
        assertEquals(6000L, paused.pauseElapsed)
        // 暂停期间墙钟走 100s，恢复后已流逝时间只算恢复前的那 5s
        val resumed = FocusTimer.resume(paused, nowElapsed = 106_000L)
        assertTrue(resumed.running)
        assertEquals(0L, resumed.pauseElapsed)
        assertEquals(5_000L, FocusTimer.elapsedInSession(resumed, nowElapsed = 106_000L))
    }

    @Test
    fun resume_neverStarted_startsFromNow() {
        val parked = FocusTimer.beginSession(FocusSession.PHASE_FOCUS, 0, nowElapsed = 1_000L, startNow = false)
        assertEquals(0L, parked.startElapsed)
        val resumed = FocusTimer.resume(parked, nowElapsed = 30_000L)
        assertTrue(resumed.running)
        assertEquals(30_000L, resumed.startElapsed)
        // 恢复即从恢复那一刻重新计时，之前走过的 30s 不算进本段
        assertEquals(15_000L, FocusTimer.elapsedInSession(resumed, nowElapsed = 45_000L))
    }

    @Test
    fun remaining_countsDownFromSegmentLength() {
        val session = running()
        assertEquals(25 * 60_000L, FocusTimer.remainingMs(session, base, segments))
        assertEquals(25 * 60_000L - 1000L, FocusTimer.remainingMs(session, base + 1000L, segments))
    }

    @Test
    fun remaining_goesNegativeAfterSegmentEnds() {
        val over = running()
        assertTrue(FocusTimer.remainingMs(over, base + 26 * 60_000L, segments) < 0L)
    }

    @Test
    fun phaseLength_matchesPhase() {
        assertEquals(25 * 60_000L, FocusTimer.phaseLengthMs(FocusSession.PHASE_FOCUS, segments))
        assertEquals(5 * 60_000L, FocusTimer.phaseLengthMs(FocusSession.PHASE_SHORT_BREAK, segments))
        assertEquals(15 * 60_000L, FocusTimer.phaseLengthMs(FocusSession.PHASE_LONG_BREAK, segments))
    }

    @Test
    fun progress_spansZeroToOne() {
        val len = 25 * 60_000L
        assertEquals(0f, FocusTimer.progress(len, len), 0.001f)
        assertEquals(0.5f, FocusTimer.progress(len / 2, len), 0.001f)
        assertEquals(1f, FocusTimer.progress(0L, len), 0.001f)
    }

    @Test
    fun progress_clampsAndSurvivesZeroLength() {
        assertEquals(1f, FocusTimer.progress(-5000L, 1000L), 0.001f)
        assertEquals(0f, FocusTimer.progress(1000L, 0L), 0.001f)
    }

    @Test
    fun nextPhase_focusGoesToShortBreak() {
        val (phase, cycle) = FocusTimer.nextPhase(FocusSession.PHASE_FOCUS, 0, 4)
        assertEquals(FocusSession.PHASE_SHORT_BREAK, phase)
        assertEquals(1, cycle)
    }

    @Test
    fun nextPhase_everyFourthPomodoroGoesToLongBreak() {
        val (phase, cycle) = FocusTimer.nextPhase(FocusSession.PHASE_FOCUS, 3, 4)
        assertEquals(FocusSession.PHASE_LONG_BREAK, phase)
        assertEquals(4, cycle)
    }

    @Test
    fun nextPhase_breakGoesBackToFocusWithoutBumpingCycle() {
        val (phase, cycle) = FocusTimer.nextPhase(FocusSession.PHASE_SHORT_BREAK, 2, 4)
        assertEquals(FocusSession.PHASE_FOCUS, phase)
        assertEquals(2, cycle)
    }

    @Test
    fun nextPhase_longBreakAlsoReturnsToFocus() {
        val (phase, cycle) = FocusTimer.nextPhase(FocusSession.PHASE_LONG_BREAK, 4, 4)
        assertEquals(FocusSession.PHASE_FOCUS, phase)
        assertEquals(4, cycle)
    }

    @Test
    fun nextPhase_fullCycle_hitsLongBreakExactlyOnce() {
        // 每个番茄占两次转移（专注→休息→专注），16 次转移 = 8 个番茄
        var phase = FocusSession.PHASE_FOCUS
        var cycle = 0
        var longBreaks = 0
        repeat(16) {
            val next = FocusTimer.nextPhase(phase, cycle, 4)
            phase = next.first
            cycle = next.second
            if (phase == FocusSession.PHASE_LONG_BREAK) longBreaks++
        }
        assertEquals("8 pomodoros at interval 4 → exactly 2 long breaks", 2, longBreaks)
        assertEquals(8, cycle)
    }

    @Test
    fun billable_neverExceedsSegmentLength() {
        val runaway = running()
        assertEquals(25 * 60_000L, FocusTimer.billableMs(runaway, base + 99 * 60_000L, segments))
    }

    @Test
    fun billable_countsPartialForSkip() {
        val partial = running()
        assertEquals(7 * 60_000L, FocusTimer.billableMs(partial, base + 7 * 60_000L, segments))
    }

    @Test
    fun billable_neverNegative() {
        val notStarted = FocusTimer.Session(startElapsed = 0L, pauseElapsed = 0L, pausedAccumMs = 0L, phase = FocusSession.PHASE_FOCUS, cycleIndex = 0, running = false)
        assertEquals(0L, FocusTimer.billableMs(notStarted, 500_000L, segments))
    }

    @Test
    fun sessionDate_sameDayIsThatDay() {
        val zone = ZoneId.systemDefault()
        val noon = LocalDate.of(2026, 10, 15).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("2026-10-15", FocusTimer.sessionDate(noon, zone))
    }

    @Test
    fun sessionDate_crossingMidnightChargesStartingDay() {
        val zone = ZoneId.systemDefault()
        val lateNight = LocalDate.of(2026, 10, 15).atTime(23, 50).atZone(zone).toInstant().toEpochMilli()
        assertEquals("2026-10-15", FocusTimer.sessionDate(lateNight, zone))
    }

    @Test
    fun bootInstant_derivedFromWallClockMinusElapsed() {
        assertEquals(900_000L, FocusTimer.bootInstant(1_000_000L, 100_000L))
    }

    @Test
    fun bootInstant_sameBootIsNotStale() {
        val boot = FocusTimer.bootInstant(1_000_000L, 100_000L)
        // 同一启动里经过 30 分钟，哨兵不变
        assertFalse(FocusTimer.isStaleBootInstant(boot, FocusTimer.bootInstant(2_800_000L, 1_900_000L)))
    }

    @Test
    fun bootInstant_networkClockNudgeTolerated() {
        val boot = 1_000_000L
        assertFalse(FocusTimer.isStaleBootInstant(boot, boot + 30_000L))
    }

    @Test
    fun bootInstant_rebootDetected() {
        val boot = 1_000_000L
        // 重启后 elapsedRealtime 归零，哨兵跳到新的墙钟起点
        assertTrue(FocusTimer.isStaleBootInstant(boot, boot + 10 * 60_000L))
    }

    @Test
    fun formatDuration_minutesAndSeconds() {
        assertEquals("00:00", FocusTimer.formatDuration(0L))
        assertEquals("00:01", FocusTimer.formatDuration(1_000L))
        assertEquals("25:00", FocusTimer.formatDuration(25 * 60_000L))
        assertEquals("1:00:00", FocusTimer.formatDuration(60 * 60_000L))
    }

    @Test
    fun formatDuration_neverNegative() {
        assertEquals("00:00", FocusTimer.formatDuration(-5000L))
    }

    @Test
    fun formatAccumulated_humanReadable() {
        assertEquals("<1m", FocusTimer.formatAccumulated(30_000L))
        assertEquals("45m", FocusTimer.formatAccumulated(45 * 60_000L))
        assertEquals("1h 20m", FocusTimer.formatAccumulated(80 * 60_000L))
        assertEquals("2h 0m", FocusTimer.formatAccumulated(120 * 60_000L))
    }
}