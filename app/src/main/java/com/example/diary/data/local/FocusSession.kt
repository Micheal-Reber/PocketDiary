package com.example.diary.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 一段专注/休息的事实记录。
 * date 取「开始那一刻」的日历日（yyyy-MM-dd）：23:50 起、00:15 结束的专注记在前一天，
 * 与 habit_records「记在打卡那一刻」的语义一致。
 */
@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(
            entity = FocusTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["date"]),
        Index(value = ["date", "phase"])
    ]
)
@Serializable
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long?,           // 可空：自由专注（未绑定任务）或任务已归档
    val date: String,            // yyyy-MM-dd
    val startedAt: Long,         // 墙钟，可跨重启
    val endedAt: Long,
    val plannedMs: Long,         // 计划段长
    val actualMs: Long,          // 实际时长（暂停已扣）—— 统计只用这个
    val phase: Int,
    val completed: Boolean,      // 是否走完整段（提前 skip = false，但时长仍计入）
    val cycleIndex: Int          // 已完成的专注段数
) {
    companion object {
        const val PHASE_FOCUS = 0
        const val PHASE_SHORT_BREAK = 1
        const val PHASE_LONG_BREAK = 2
    }
}

// Statistics data classes —— 与 HabitDao 的 MonthlyStat/DailyStat 同构

data class FocusTotalByDate(
    val date: String,
    val total: Long
)

data class FocusDailyStat(
    val day: Int,
    val total: Long
)

data class FocusMonthlyStat(
    val month: Int,
    val total: Long
)