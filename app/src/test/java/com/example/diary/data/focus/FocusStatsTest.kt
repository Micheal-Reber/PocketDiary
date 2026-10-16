package com.example.diary.data.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class FocusStatsTest {

    private val today = LocalDate.of(2026, 10, 15)

    @Test
    fun `heatmap start aligns to monday of the window`() {
        val start = FocusStats.heatmapStart(today, 4)
        assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
        assertEquals(LocalDate.of(2026, 9, 21), start)
    }

    @Test
    fun `heatmap start for today on monday is today minus weeks`() {
        val monday = LocalDate.of(2026, 10, 12)
        assertEquals(LocalDate.of(2026, 10, 5), FocusStats.heatmapStart(monday, 2))
    }

    @Test
    fun `heat level splits by quarter of window max`() {
        val max = 100L
        assertEquals(0, FocusStats.heatLevel(0L, max))
        assertEquals(0, FocusStats.heatLevel(50L, 0L))
        assertEquals(1, FocusStats.heatLevel(25L, max))
        assertEquals(1, FocusStats.heatLevel(10L, max))
        assertEquals(2, FocusStats.heatLevel(50L, max))
        assertEquals(3, FocusStats.heatLevel(75L, max))
        assertEquals(4, FocusStats.heatLevel(100L, max))
    }

    @Test
    fun `goal progress caps at one`() {
        assertEquals(0f, FocusStats.goalProgress(0L, 100), 0.001f)
        assertEquals(0.5f, FocusStats.goalProgress(50 * 60_000L, 100), 0.001f)
        assertEquals(1f, FocusStats.goalProgress(999 * 60_000L, 100), 0.001f)
        assertEquals(0f, FocusStats.goalProgress(1000L, 0), 0.001f)
    }

    @Test
    fun `week window is last seven days and previous is the one before`() {
        assertEquals(LocalDate.of(2026, 10, 9), FocusStats.currentStart(FocusRange.WEEK, today))
        assertEquals(today, FocusStats.currentEnd(FocusRange.WEEK, today))
        assertEquals(LocalDate.of(2026, 10, 2), FocusStats.previousStart(FocusRange.WEEK, today))
        assertEquals(LocalDate.of(2026, 10, 8), FocusStats.previousEnd(FocusRange.WEEK, today))
        assertEquals(7, FocusStats.bucketCount(FocusRange.WEEK, today))
    }

    @Test
    fun `month window is current month against previous month`() {
        assertEquals(YearMonth.of(2026, 10).atDay(1), FocusStats.currentStart(FocusRange.MONTH, today))
        assertEquals(YearMonth.of(2026, 9).atDay(1), FocusStats.previousStart(FocusRange.MONTH, today))
        assertEquals(YearMonth.of(2026, 9).atEndOfMonth(), FocusStats.previousEnd(FocusRange.MONTH, today))
        assertEquals(15, FocusStats.bucketCount(FocusRange.MONTH, today))
    }

    @Test
    fun `year window is current year against last year`() {
        assertEquals(LocalDate.of(2026, 1, 1), FocusStats.currentStart(FocusRange.YEAR, today))
        assertEquals(LocalDate.of(2025, 1, 1), FocusStats.previousStart(FocusRange.YEAR, today))
        assertEquals(LocalDate.of(2025, 12, 31), FocusStats.previousEnd(FocusRange.YEAR, today))
        assertEquals(12, FocusStats.bucketCount(FocusRange.YEAR, today))
    }

    @Test
    fun `query start covers both heatmap and previous period`() {
        assertEquals(FocusStats.heatmapStart(today), FocusStats.queryStart(FocusRange.WEEK, today))
        assertEquals(FocusStats.heatmapStart(today), FocusStats.queryStart(FocusRange.MONTH, today))
        assertEquals(LocalDate.of(2025, 1, 1), FocusStats.queryStart(FocusRange.YEAR, today))
    }

    @Test
    fun `average per bucket divides by bucket count`() {
        assertEquals(600_000L, FocusStats.averagePerBucket(4_200_000L, 7))
        assertEquals(0L, FocusStats.averagePerBucket(100L, 0))
    }

    @Test
    fun `change percent is null when previous period is empty`() {
        assertNull(FocusStats.changePercent(600_000L, 0L))
        assertEquals(50f, FocusStats.changePercent(900_000L, 600_000L)!!, 0.01f)
        assertEquals(-50f, FocusStats.changePercent(300_000L, 600_000L)!!, 0.01f)
    }

    @Test
    fun `format percent drops sign`() {
        assertEquals(50, FocusStats.formatPercent(50.9f))
        assertEquals(50, FocusStats.formatPercent(-50.9f))
    }
}