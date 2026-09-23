package com.example.diary.data.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * TodoReminderScheduler 纯函数单测：requestCode 防截断 + 重复提醒日历日滚动
 */
class TodoReminderSchedulerTest {

    @Test
    fun `requestCode does not truncate high ids`() {
        val small = TodoReminderScheduler.requestCodeFor(1L)
        val large = TodoReminderScheduler.requestCodeFor(1L shl 40)
        assertEquals(TodoReminderScheduler.requestCodeFor(1L), small)
        assertTrue(small != large)
        // 同一 id 稳定
        assertEquals(TodoReminderScheduler.requestCodeFor(1L shl 40), large)
        // 高位 id 不与低位撞号（旧实现 toInt() 会把 1L shl 32 截成 0）
        assertTrue(TodoReminderScheduler.requestCodeFor(1L shl 32) != TodoReminderScheduler.requestCodeFor(0L))
    }

    @Test
    fun `nextDailyOccurrence advances one calendar day in system zone`() {
        val zone = ZoneId.systemDefault()
        val base = LocalDateTime.of(2026, 3, 1, 9, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val now = base - 60_000L // 1 分钟后才到点
        val next = TodoReminderScheduler.nextDailyOccurrence(fromMillis = base, nowMillis = now)
        // 仍是“明天 09:00”的墙上时间（DST 切换日也按本地日历日 +1）
        val nextLocal = java.time.Instant.ofEpochMilli(next).atZone(zone).toLocalDateTime()
        val baseLocal = java.time.Instant.ofEpochMilli(base).atZone(zone).toLocalDateTime()
        assertEquals(baseLocal.toLocalDate().plusDays(1), nextLocal.toLocalDate())
        assertEquals(baseLocal.toLocalTime(), nextLocal.toLocalTime())
        assertTrue(next > now)
    }

    @Test
    fun `nextDailyOccurrence skips past occurrences after long downtime`() {
        val zone = ZoneId.systemDefault()
        val base = LocalDateTime.of(2026, 1, 1, 8, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val now = LocalDateTime.of(2026, 1, 10, 12, 0)
            .atZone(zone).toInstant().toEpochMilli()
        val next = TodoReminderScheduler.nextDailyOccurrence(fromMillis = base, nowMillis = now)
        val nextLocal = java.time.Instant.ofEpochMilli(next).atZone(zone)
        assertTrue(next > now)
        assertEquals(java.time.LocalTime.of(8, 0), nextLocal.toLocalTime())
    }
}
