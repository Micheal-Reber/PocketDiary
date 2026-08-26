package com.example.diary.data.countdown

import com.example.diary.data.local.CountdownEvent
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 倒数日日期数学——纯 Kotlin 无 Android 依赖（JUnit 可直接测）。
 *
 * 行为对齐 `.omo/analyses/countdateapp-countdown-countup-logic.md`：
 *  - 正/倒不存储，由「今天 vs 锚点日」动态判定，过期自动迁移；
 *  - 天数按日历日差（LocalDate），事件当天 = 特殊「就是今天」态；
 *  - +1日：计数整体 +1（含首尾当天），「就是今天」不受影响；
 *  - 重复规则（每年/每月）：锚点已过则向前滚动到下一个 ≥ 今天的锚点再计数。
 *
 * 与 countdateapp 的差异（有意为之）：该项目用毫秒 floor + 23:59:00 锚定，
 * 我们用 LocalDate 日历差——语义等价（满一天进位）且无时区/DST 边界问题，
 * 也符合本项目「日期一律 yyyy-MM-dd 字符串」的全局约定。
 */
object DateMath {

    /** 卡片/详情页渲染用的三态结果。days 恒为正。 */
    sealed interface CountState {
        /** 目标日就是今天。 */
        data object Today : CountState

        /** 还有 [days] 天（目标在未来）。 */
        data class Countdown(val days: Int) : CountState

        /** 已经 [days] 天（目标在过去）。 */
        data class Countup(val days: Int) : CountState
    }

    /**
     * 解析生效锚点：无重复规则原样返回；有重复规则且已过期则滚动到
     * 下一个 ≥ [today] 的同月日/同年日。始终从**原始**锚点加偏移量，
     * 避免 Jan31→Feb28→Mar28 这类逐次累加的漂移（每次都从源点算）。
     * LocalDate.plusMonths/plusYears 自带月末钳制（2/29 → 平年 2/28）。
     */
    fun resolveAnchor(dateStr: String, repeatRule: Int, today: LocalDate): LocalDate {
        val anchor = LocalDate.parse(dateStr)
        if (repeatRule == CountdownEvent.REPEAT_NONE || !anchor.isBefore(today)) return anchor
        return when (repeatRule) {
            CountdownEvent.REPEAT_YEARLY -> {
                val years = ChronoUnit.YEARS.between(anchor, today)
                var rolled = anchor.plusYears(years)
                if (rolled.isBefore(today)) rolled = anchor.plusYears(years + 1)
                rolled
            }
            CountdownEvent.REPEAT_MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(anchor, today)
                var rolled = anchor.plusMonths(months)
                if (rolled.isBefore(today)) rolled = anchor.plusMonths(months + 1)
                rolled
            }
            else -> anchor
        }
    }

    /**
     * 三态判定主入口。[plusOne] 启用时天数整体 +1（含首尾当天），
     * 但「就是今天」保持 Today 不加。
     */
    fun compute(
        dateStr: String,
        repeatRule: Int,
        plusOne: Boolean,
        today: LocalDate = LocalDate.now()
    ): CountState {
        val anchor = resolveAnchor(dateStr, repeatRule, today)
        val diff = ChronoUnit.DAYS.between(today, anchor).toInt()
        return when {
            diff == 0 -> CountState.Today
            diff > 0 -> CountState.Countdown(days = if (plusOne) diff + 1 else diff)
            else -> CountState.Countup(days = -diff + if (plusOne) 1 else 0)
        }
    }

    /** 文案模板：「{名}还有 N 天」「{名}已经 N 天」「{名}就是今天」。 */
    fun templateText(name: String, state: CountState): String = when (state) {
        is CountState.Today -> "$name 就是今天"
        is CountState.Countdown -> "$name 还有 ${state.days} 天"
        is CountState.Countup -> "$name 已经 ${state.days} 天"
    }
}
