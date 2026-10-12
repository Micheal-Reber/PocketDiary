package com.example.diary.data.countdown

import com.example.diary.data.local.CountdownEvent
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object CountdownReminder {

    const val DEFAULT_HOUR = CountdownEvent.REMIND_DEFAULT_HOUR
    const val DEFAULT_MINUTE = 0

    /**
     * 提醒触发时刻（epoch 毫秒）。关闭提醒返回 null。
     * 重复事件经 resolveAnchor 滚到当前这一轮，再按提前天数回退。
     * 用 LocalDateTime + 系统时区换算，跨 DST 安全（与待办 nextDailyOccurrence 同思路）。
     */
    fun remindAt(
        event: CountdownEvent,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (event.remindDaysBefore < 0) return null
        val anchor = DateMath.resolveAnchor(event.date, event.repeatRule, today)
        return remindAtForAnchor(event, anchor, zone)
    }

    /** 指定锚点日直接换算触发时刻：重复事件发出后按 nextRepeatAnchor 排下一轮时用。 */
    fun remindAtForAnchor(
        event: CountdownEvent,
        anchor: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        val fireDate = anchor.minusDays(event.remindDaysBefore.toLong())
        val fireTime = parseTime(event.time)
        return LocalDateTime.of(fireDate, fireTime)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    fun remindTime(event: CountdownEvent): LocalTime = parseTime(event.time)

    private fun parseTime(raw: String?): LocalTime {
        if (raw.isNullOrBlank()) return LocalTime.of(DEFAULT_HOUR, DEFAULT_MINUTE)
        val parts = raw.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()
        val minute = parts.getOrNull(1)?.toIntOrNull()
        if (hour == null || minute == null || hour !in 0..23 || minute !in 0..59) {
            return LocalTime.of(DEFAULT_HOUR, DEFAULT_MINUTE)
        }
        return LocalTime.of(hour, minute)
    }

    /**
     * 重复事件的下一轮：提醒发出后推进到下一个未过期锚点，无重复则返回 null。
     * 传 today 的次日作为基准，避免同一轮内重复触发。
     */
    fun nextRepeatAnchor(event: CountdownEvent, today: LocalDate): LocalDate? {
        if (event.repeatRule == CountdownEvent.REPEAT_NONE) return null
        val from = today.plusDays(1)
        val next = DateMath.resolveAnchor(event.date, event.repeatRule, from)
        return next.takeIf { it.isAfter(today) }
    }

    fun isDue(atMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean =
        atMillis <= nowMillis

    fun toLocalDateTime(atMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
        Instant.ofEpochMilli(atMillis).atZone(zone).toLocalDateTime()
}