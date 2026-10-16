package com.example.diary.data.focus

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class FocusRange { WEEK, MONTH, YEAR }

/**
 * 专注统计页的全部纯计算：日期窗口、热力图分档、目标进度、期间均值与环比。
 * 与 UI 解耦是为了能直接单测（见 FocusStatsTest）。
 */
object FocusStats {

    const val HEATMAP_WEEKS = 26
    const val RECENT_DAYS = 7

    /** 热力图窗口起点：今天所在周的周一往前推 weeks-1 周（周一为首列） */
    fun heatmapStart(today: LocalDate, weeks: Int = HEATMAP_WEEKS): LocalDate {
        val thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return thisMonday.minusWeeks((weeks - 1).toLong())
    }

    /** 热力图格子数（含今天，按周一对齐） */
    fun heatmapCellCount(today: LocalDate, weeks: Int = HEATMAP_WEEKS): Int =
        (ChronoDays.between(heatmapStart(today, weeks), today) + 1).toInt()

    /** 按当月峰值分 4 档（0 = 无数据），与参考实现一致的 lerp 阶梯 */
    fun heatLevel(millis: Long, windowMax: Long): Int {
        if (millis <= 0L || windowMax <= 0L) return 0
        val ratio = millis.toDouble() / windowMax.toDouble()
        return when {
            ratio <= 0.25 -> 1
            ratio <= 0.5 -> 2
            ratio <= 0.75 -> 3
            else -> 4
        }
    }

    /** 今日目标表盘的进度（0..1，超出即封顶） */
    fun goalProgress(todayMillis: Long, goalMinutes: Int): Float {
        if (goalMinutes <= 0) return 0f
        val goalMillis = goalMinutes * 60_000L
        return (todayMillis.toDouble() / goalMillis.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /** 当前期间起点（周=今天往前 6 天，月=当月 1 日，年=当年 1 月 1 日） */
    fun currentStart(range: FocusRange, today: LocalDate): LocalDate = when (range) {
        FocusRange.WEEK -> today.minusDays((RECENT_DAYS - 1).toLong())
        FocusRange.MONTH -> today.withDayOfMonth(1)
        FocusRange.YEAR -> LocalDate.of(today.year, 1, 1)
    }

    /** 当前期间终点一律是今天 */
    fun currentEnd(range: FocusRange, today: LocalDate): LocalDate = today

    /** 上一期间窗口（周=再往前 7 天，月=上月，年=去年） */
    fun previousStart(range: FocusRange, today: LocalDate): LocalDate = when (range) {
        FocusRange.WEEK -> today.minusDays((RECENT_DAYS * 2 - 1).toLong())
        FocusRange.MONTH -> YearMonth.from(today).minusMonths(1).atDay(1)
        FocusRange.YEAR -> LocalDate.of(today.year - 1, 1, 1)
    }

    fun previousEnd(range: FocusRange, today: LocalDate): LocalDate = when (range) {
        FocusRange.WEEK -> today.minusDays(RECENT_DAYS.toLong())
        FocusRange.MONTH -> YearMonth.from(today).minusMonths(1).atEndOfMonth()
        FocusRange.YEAR -> LocalDate.of(today.year - 1, 12, 31)
    }

    /** 一次查询要覆盖到的最早日期：热力图窗口与期间对比窗口取更早的那个 */
    fun queryStart(range: FocusRange, today: LocalDate): LocalDate {
        val heatStart = heatmapStart(today)
        val periodStart = previousStart(range, today)
        return if (periodStart.isBefore(heatStart)) periodStart else heatStart
    }

    /** 趋势柱数：周=7 天，月=当月已过天数，年=12 个月 */
    fun bucketCount(range: FocusRange, today: LocalDate): Int = when (range) {
        FocusRange.WEEK -> RECENT_DAYS
        FocusRange.MONTH -> today.dayOfMonth
        FocusRange.YEAR -> 12
    }

    fun averagePerBucket(totalMillis: Long, buckets: Int): Long =
        if (buckets <= 0) 0L else totalMillis / buckets

    /** 环比百分比；上一期间为 0 时返回 null（不谎报 +∞） */
    fun changePercent(current: Long, previous: Long): Float? {
        if (previous <= 0L) return null
        return ((current - previous).toDouble() / previous.toDouble() * 100.0).toFloat()
    }

    fun formatPercent(percent: Float): Int = kotlin.math.abs(percent).toInt()

    private object ChronoDays {
        fun between(start: LocalDate, end: LocalDate): Long =
            java.time.temporal.ChronoUnit.DAYS.between(start, end)
    }
}