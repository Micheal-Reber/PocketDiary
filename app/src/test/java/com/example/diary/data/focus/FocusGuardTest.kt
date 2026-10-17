package com.example.diary.data.focus

import com.example.diary.data.local.FocusSession
import org.junit.Assert.assertEquals
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

    @Test
    fun `home screen is allowed only while the launcher switch is on`() {
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.miui.home", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS,
                allowLauncher = true, isLauncher = true
            )
        )
        assertTrue(
            FocusGuard.shouldBlock(
                own, "com.miui.home", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS,
                allowLauncher = false, isLauncher = true
            )
        )
        assertTrue(FocusGuard.isKnownLauncher("com.miui.home"))
        assertFalse(FocusGuard.isKnownLauncher("com.tencent.mm"))
    }

    @Test
    fun `a non launcher app stays blocked even when the launcher switch is on`() {
        assertTrue(
            FocusGuard.shouldBlock(
                own, "com.tencent.mm", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS,
                allowLauncher = true, isLauncher = false
            )
        )
    }

    @Test
    fun `launcher is not silently allowed when flagged as a normal app`() {
        assertTrue(
            FocusGuard.shouldBlock(
                own, "com.miui.home", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS,
                allowLauncher = true, isLauncher = false
            )
        )
    }

    @Test
    fun `user whitelist still wins over the launcher switch`() {
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.tencent.mm", setOf("com.tencent.mm"),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS,
                allowLauncher = false, isLauncher = false
            )
        )
    }

    @Test
    fun `settle and verify delays leave room for a second attempt`() {
        assertEquals(100L, FocusGuard.SETTLE_MS)
        assertEquals(1500L, FocusGuard.VERIFY_MS)
    }

    @Test
    fun `enabled keyboard is always allowed so typing keeps working`() {
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.google.android.inputmethod.latin", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS,
                allowLauncher = false, isLauncher = false, isKeyboard = true
            )
        )
    }

    @Test
    fun `system settings is not auto allowed since it can force stop the guard`() {
        assertFalse(FocusGuard.DEFAULT_ALLOWED_PACKAGES.contains("com.android.settings"))
        assertTrue(
            FocusGuard.shouldBlock(
                own, "com.android.settings", emptySet(),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
        // 用户仍可在「系统」标签页手动把它加回白名单
        assertFalse(
            FocusGuard.shouldBlock(
                own, "com.android.settings", setOf("com.android.settings"),
                guardEnabled = true, sessionActive = true, phase = FocusSession.PHASE_FOCUS
            )
        )
    }
}