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
     * 纯函数便于单测：density/scaledDensity 由调用方传入。
     */
    fun responsiveNumberSp(
        numberLen: Int,
        unitLen: Int,
        widthDp: Int,
        heightDp: Int,
        density: Float,
        scaledDensity: Float
    ): Float {
        if (density <= 0f || scaledDensity <= 0f) return DEFAULT_NUMBER_SP
        val width = widthDp.toFloat()
        val height = heightDp.toFloat()
        if (width <= 0f || height <= 0f) return DEFAULT_NUMBER_SP

        val headerPx = 14f * scaledDensity + 14f * density
        val dividerPx = 0.6f * density
        val footerPx = 11f * scaledDensity + 8f * density
        val contentPaddingPx = 12f * density * 2

        val availableHeightPx = height - headerPx - dividerPx - footerPx - 8f * density
        val availableWidthPx = width - contentPaddingPx
        if (availableHeightPx <= 0f || availableWidthPx <= 0f) return DEFAULT_NUMBER_SP

        val chars = numberLen.coerceAtLeast(1) * 0.6f + unitLen * 1f
        if (chars <= 0f) return DEFAULT_NUMBER_SP

        val spacingPx = 4f * density
        val maxByHeightSp = availableHeightPx / (1.2f * scaledDensity)
        val maxByWidthSp = (availableWidthPx - spacingPx) / (chars * scaledDensity)
        return (minOf(maxByHeightSp, maxByWidthSp) * 0.9f).coerceIn(12f, 80f)
    }

    const val DEFAULT_NUMBER_SP = 44f
    const val UNIT_SP_RATIO = 0.32f
}
