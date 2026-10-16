package com.example.diary.data.focus

import com.example.diary.data.local.FocusSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusGuardTest {

    private val own = "com.example.diary"

    @Test
    fun `blocks other apps only during a running focus phase`() {
        assertTrue(
            FocusGuard.shouldBlock(
                own, "com.tencent.mm", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.tencent.mm", emptySet(),
                guardEnabled = true, sessionActive = false, phase = FocusSession.PHASE_FOCUS
            )
        )
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.tencent.mm", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_SHORT_BREAK
            )
        )
    }

    @Test
    fun `never blocks when guard is off`() {
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.tencent.mm", emptySet(),
                guardEnabled = false, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
    }

    @Test
    fun `own package and system essentials are always allowed`() {
        assertFalse(
            FocusGuard.shouldBlock(
                own, own, emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.android.systemui", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.android.phone", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
    }

    @Test
    fun `user picked packages are allowed`() {
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.tencent.mm", setOf("com.tencent.mm"),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
    }

    @Test
    fun `null foreground package is ignored`() {
        assertFalse(
            FocusGuard.shouldBlock(
                own, null, emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
    }

    @Test
    fun `cooldown window suppresses repeat reactions`() {
        assertTrue(FocusGuard.withinCooldown(1_000L, 1_500L))
        assertFalse(FocusGuard.withinCooldown(1_000L, 9_000L))
        assertFalse(FocusGuard.shouldReact(1_000L, 1_500L))
        assertTrue(FocusGuard.shouldReact(1_000L, 9_000L))
        assertTrue(FocusGuard.shouldReact(0L, 60_000L))
    }

    @Test
    fun `is guarding requires focus phase`() {
        assertTrue(FocusGuard.isGuarding(true, FocusSession.PHASE_FOCUS))
        assertFalse(FocusGuard.isGuarding(true, FocusSession.PHASE_LONG_BREAK))
        assertFalse(FocusGuard.isGuarding(false, FocusSession.PHASE_FOCUS))
        assertFalse(FocusGuard.isGuarding(true, null))
    }
}