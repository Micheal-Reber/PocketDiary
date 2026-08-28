package com.example.diary.ui.countdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 仅测算 nearestLevel 纯函数；栈模糊视觉效果需真机/截图验证。
 */
class BlurCacheTest {

    @Test
    fun `nearestLevel maps to nearest 5 multiple within 0 to 25`() {
        assertEquals(0, nearestLevel(0))
        assertEquals(0, nearestLevel(1))
        assertEquals(0, nearestLevel(-3))
        assertEquals(5, nearestLevel(3))
        assertEquals(5, nearestLevel(5))
        assertEquals(5, nearestLevel(7))
        assertEquals(10, nearestLevel(12))
        assertEquals(25, nearestLevel(25))
        assertEquals(25, nearestLevel(26))
        assertEquals(25, nearestLevel(100))
    }
}