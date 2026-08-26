package com.example.diary.data.countdown

import com.example.diary.data.countdown.DateMath.CountState
import com.example.diary.data.local.CountdownEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * DateMath 行为锁定测试——语义对齐
 * `.omo/analyses/countdateapp-countdown-countup-logic.md` 第 5/7 节 + 方案默认值。
 */
class DateMathTest {

    private val today: LocalDate = LocalDate.of(2026, 1, 15)

    // ---------- 基本三态 ----------

    @Test
    fun `future date counts down`() {
        val state = DateMath.compute("2026-01-20", CountdownEvent.REPEAT_NONE, false, today)
        assertEquals(CountState.Countdown(5), state)
    }

    @Test
    fun `future date with plusOne adds one`() {
        val state = DateMath.compute("2026-01-20", CountdownEvent.REPEAT_NONE, true, today)
        assertEquals(CountState.Countdown(6), state)
    }

    @Test
    fun `anchor equals today is Today regardless of plusOne`() {
        assertEquals(
            CountState.Today,
            DateMath.compute("2026-01-15", CountdownEvent.REPEAT_NONE, false, today)
        )
        assertEquals(
            CountState.Today,
            DateMath.compute("2026-01-15", CountdownEvent.REPEAT_NONE, true, today)
        )
    }

    @Test
    fun `past date counts up`() {
        val state = DateMath.compute("2026-01-10", CountdownEvent.REPEAT_NONE, false, today)
        assertEquals(CountState.Countup(5), state)
    }

    @Test
    fun `past date with plusOne adds one`() {
        val state = DateMath.compute("2026-01-10", CountdownEvent.REPEAT_NONE, true, today)
        assertEquals(CountState.Countup(6), state)
    }

    // ---------- 每年重复 ----------

    @Test
    fun `yearly rolls past anchor to this year's occurrence`() {
        // 锚点 2025-03-10 已过 → 滚到 2026-03-10，还有 54 天
        val state = DateMath.compute("2025-03-10", CountdownEvent.REPEAT_YEARLY, false, today)
        val expected = CountState.Countdown(
            java.time.temporal.ChronoUnit.DAYS.between(today, LocalDate.of(2026, 3, 10)).toInt()
        )
        assertEquals(expected, state)
    }

    @Test
    fun `yearly roll landing exactly on today yields Today`() {
        // 锚点去年今天 → 滚动后正好是今天
        val state = DateMath.compute("2025-01-15", CountdownEvent.REPEAT_YEARLY, false, today)
        assertEquals(CountState.Today, state)
    }

    @Test
    fun `yearly future anchor is not rolled`() {
        // 锚点今年 12-31 在未来 → 原样倒数
        val state = DateMath.compute("2026-12-31", CountdownEvent.REPEAT_YEARLY, false, today)
        assertEquals(CountState.Countdown(350), state)
    }

    // ---------- 每月重复 ----------

    @Test
    fun `monthly rolls forward possibly more than one step`() {
        // 锚点 2025-11-20，MONTHS.between=1 → 12-20 仍过期 → 再 +1 → 2026-01-20
        val state = DateMath.compute("2025-11-20", CountdownEvent.REPEAT_MONTHLY, false, today)
        assertEquals(CountState.Countdown(5), state)
    }

    @Test
    fun `monthly clamps day-of-month like LocalDate`() {
        // today=2026-04-10，锚点 2025-01-31：
        // MONTHS.between=14 → plusMonths(14)=2026-03-31 仍早于 04-10
        // → plusMonths(15)=2026-04-30（月末钳制）→ 还有 20 天
        val t = LocalDate.of(2026, 4, 10)
        val state = DateMath.compute("2025-01-31", CountdownEvent.REPEAT_MONTHLY, false, t)
        assertEquals(
            CountState.Countdown(
                java.time.temporal.ChronoUnit.DAYS.between(t, LocalDate.of(2026, 4, 30)).toInt()
            ),
            state
        )
    }

    @Test
    fun `no cumulative drift - always offsets from original anchor`() {
        // 连续多年滚动 Feb29 锚点不应漂移到别的日期：
        // today=2028-02-28，锚点 2024-02-29 → plusYears(4)=2028-02-29（闰年）→ 还有 1 天
        val t = LocalDate.of(2028, 2, 28)
        val state = DateMath.compute("2024-02-29", CountdownEvent.REPEAT_YEARLY, false, t)
        assertEquals(CountState.Countdown(1), state)
    }

    // ---------- 文案模板 ----------

    @Test
    fun `template text covers all three states`() {
        assertEquals("生日 就是今天", DateMath.templateText("生日", CountState.Today))
        assertEquals("考试 还有 3 天", DateMath.templateText("考试", CountState.Countdown(3)))
        assertEquals("恋爱 已经 100 天", DateMath.templateText("恋爱", CountState.Countup(100)))
    }
}
