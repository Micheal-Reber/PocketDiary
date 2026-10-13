package com.example.diary.ui.widget

import com.example.diary.data.countdown.DateMath
import com.example.diary.data.local.CountdownEvent
import java.time.LocalDate
import kotlin.math.abs

object CountdownWidgetModel {

    fun stateOf(event: CountdownEvent, today: LocalDate): DateMath.CountState =
        DateMath.compute(event.date, event.repeatRule, event.plusOne, today)

    /** 相对今天的有向天数：未来为正、过去为负、今天为 0。 */
    fun daysFromToday(event: CountdownEvent, today: LocalDate): Int = when (val s = stateOf(event, today)) {
        is DateMath.CountState.Today -> 0
        is DateMath.CountState.Countdown -> s.days
        is DateMath.CountState.Countup -> -s.days
    }

    /**
     * 单事件展示：置顶优先，同组内取离今天最近的那个。
     * 不按日期升序取第一个，否则「有未来倒数日却展示了几年前的事件」会很反直觉。
     */
    fun featuredEvent(events: List<CountdownEvent>, today: LocalDate): CountdownEvent? {
        if (events.isEmpty()) return null
        val pinned = events.filter { it.pinned }
        val pool = if (pinned.isNotEmpty()) pinned else events
        return pool.minByOrNull { abs(daysFromToday(it, today)) }
    }

    fun resolveEvent(events: List<CountdownEvent>, today: LocalDate, selectedId: Long): CountdownEvent? {
        if (selectedId > 0L) events.firstOrNull { it.id == selectedId }?.let { return it }
        return featuredEvent(events, today)
    }

    /**
     * 数字字号随小组件可用空间与位数自适应，算法本地化自 ybhgl/Reminder 的 WidgetUpdateHelper。
     * 单位「天」与数字同行（基线对齐），故宽度预算要把单位算进来，
     * 高度预算则不再为单位预留独立行。
     * 纯函数便于单测：density/scaledDensity 由调用方传入。
     */
    fun responsiveNumberSp(
        numberLen: Int,
        widthDp: Int,
        heightDp: Int,
        density: Float,
        scaledDensity: Float
    ): Float {
        if (density <= 0f || scaledDensity <= 0f) return DEFAULT_NUMBER_SP
        if (widthDp <= 0 || heightDp <= 0) return DEFAULT_NUMBER_SP

        val widthPx = widthDp * density
        val heightPx = heightDp * density

        val labelPx = 14f * scaledDensity + 10f * density
        val footerPx = 2f * (12f * scaledDensity * 1.35f) + 2f * density
        val contentPaddingPx = 12f * density * 2

        val availableHeightPx = heightPx - labelPx - footerPx - contentPaddingPx
        val availableWidthPx = widthPx - contentPaddingPx
        if (availableHeightPx <= 0f || availableWidthPx <= 0f) return DEFAULT_NUMBER_SP

        // 同行：数字按字符宽估算，单位占一个「字符」宽度（= 字号 × 比例）
        val widthFactor = numberLen.coerceAtLeast(1) * CHAR_WIDTH_FACTOR + UNIT_SP_RATIO
        val maxByHeightSp = availableHeightPx / (1.15f * scaledDensity)
        val maxByWidthSp = availableWidthPx / (widthFactor * scaledDensity)
        return (minOf(maxByHeightSp, maxByWidthSp) * FILL_FACTOR)
            .coerceIn(MIN_NUMBER_SP, MAX_NUMBER_SP)
    }

    const val DEFAULT_NUMBER_SP = 56f
    const val UNIT_SP_RATIO = 0.34f
    private const val CHAR_WIDTH_FACTOR = 0.62f
    private const val FILL_FACTOR = 0.82f
    internal const val MIN_NUMBER_SP = 16f
    internal const val MAX_NUMBER_SP = 60f
}
