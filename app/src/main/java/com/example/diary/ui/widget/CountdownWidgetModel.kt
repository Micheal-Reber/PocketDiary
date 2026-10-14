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
}
