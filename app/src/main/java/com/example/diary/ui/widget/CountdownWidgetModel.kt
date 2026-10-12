package com.example.diary.ui.widget

import com.example.diary.data.countdown.DateMath
import com.example.diary.data.local.CountdownEvent
import java.time.LocalDate

object CountdownWidgetModel {

    const val MAX_PAGES = 5

    fun visibleEvents(events: List<CountdownEvent>): List<CountdownEvent> =
        if (events.size > MAX_PAGES) events.subList(0, MAX_PAGES).toList() else events

    fun resolveIndex(events: List<CountdownEvent>, savedEventId: Long, savedIndex: Int): Int {
        if (events.isEmpty()) return 0
        if (savedEventId > 0) {
            val byId = events.indexOfFirst { it.id == savedEventId }
            if (byId >= 0) return byId
        }
        return savedIndex.coerceIn(0, events.lastIndex)
    }

    fun stateOf(event: CountdownEvent, today: LocalDate): DateMath.CountState =
        DateMath.compute(event.date, event.repeatRule, event.plusOne, today)

    /**
     * 数字字号随小组件可用空间与位数自适应，算法本地化自 ybhgl/Reminder 的 WidgetUpdateHelper。
     * 单位「天」单独占一行，故宽度只按数字位数估算、额外为单位预留一行高度。
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

        val headerPx = 14f * scaledDensity + 14f * density
        val dividerPx = 0.6f * density
        val footerPx = 11f * scaledDensity + 8f * density
        val unitLinePx = 18f * scaledDensity + 2f * density
        val contentPaddingPx = 12f * density * 2

        val availableHeightPx = heightPx - headerPx - dividerPx - footerPx - unitLinePx
        val availableWidthPx = widthPx - contentPaddingPx
        if (availableHeightPx <= 0f || availableWidthPx <= 0f) return DEFAULT_NUMBER_SP

        val chars = numberLen.coerceAtLeast(1) * CHAR_WIDTH_FACTOR
        val maxByHeightSp = availableHeightPx / (1.15f * scaledDensity)
        val maxByWidthSp = availableWidthPx / (chars * scaledDensity)
        return (minOf(maxByHeightSp, maxByWidthSp) * FILL_FACTOR).coerceIn(MIN_NUMBER_SP, MAX_NUMBER_SP)
    }

    const val DEFAULT_NUMBER_SP = 56f
    const val UNIT_SP_RATIO = 0.26f
    private const val CHAR_WIDTH_FACTOR = 0.62f
    private const val FILL_FACTOR = 0.82f
    private const val MIN_NUMBER_SP = 16f
    private const val MAX_NUMBER_SP = 48f
}
