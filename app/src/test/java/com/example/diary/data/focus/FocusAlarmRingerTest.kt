package com.example.diary.data.focus

import com.example.diary.data.local.FocusSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusAlarmRingerTest {

    @Test
    fun `waveform alternates on off on off`() {
        val (timings, amplitudes) = FocusAlarmRinger.waveform(400L, 300L, 255)
        assertArrayEquals(longArrayOf(0L, 400L, 300L, 400L), timings)
        assertArrayEquals(intArrayOf(0, 255, 0, 255), amplitudes)
    }

    @Test
    fun `waveform clamps out of range values`() {
        val (timings, amplitudes) = FocusAlarmRinger.waveform(5L, 9_000L, 900)
        assertArrayEquals(longArrayOf(0L, 50L, 2000L, 50L), timings)
        assertArrayEquals(intArrayOf(0, 255, 0, 255), amplitudes)
    }

    @Test
    fun `gap may be zero for a continuous buzz`() {
        val (timings, _) = FocusAlarmRinger.waveform(500L, 0L, 200)
        assertArrayEquals(longArrayOf(0L, 500L, 0L, 500L), timings)
    }

    @Test
    fun `amplitude may be zero to mute vibration while keeping sound`() {
        val (_, amplitudes) = FocusAlarmRinger.waveform(400L, 300L, 0)
        assertArrayEquals(intArrayOf(0, 0, 0, 0), amplitudes)
    }

    @Test
    fun `only a finished focus segment triggers the strong alert`() {
        assertTrue(FocusAlarmRinger.shouldStrongRemind(FocusSession.PHASE_FOCUS, alarmEnabled = true))
        assertFalse(FocusAlarmRinger.shouldStrongRemind(FocusSession.PHASE_SHORT_BREAK, alarmEnabled = true))
        assertFalse(FocusAlarmRinger.shouldStrongRemind(FocusSession.PHASE_LONG_BREAK, alarmEnabled = true))
        assertFalse(FocusAlarmRinger.shouldStrongRemind(FocusSession.PHASE_FOCUS, alarmEnabled = false))
    }

    @Test
    fun `alert auto stops so it cannot ring forever`() {
        assertEquals(60_000L, FocusAlarmRinger.AUTO_STOP_MS)
    }
}