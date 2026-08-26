package com.example.diary.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diary_entries",
    // One diary entry per date — the editor's saveEntry() flow looks up by date
    // and assumes uniqueness. Without this index, concurrent saves (or an
    // existingId race during navigation) could produce two rows for the same date.
    indices = [Index(value = ["date"], unique = true)]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val date: String,  // yyyy-MM-dd
    val mood: String? = null,           // e.g. "❤️", "🌙", null = 未选
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,   // reverse-geocoded or manually entered
    val weather: String? = null,        // e.g. "☀️", "🌧", null = 未取
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✅",
    val colorIndex: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Entity(
    tableName = "habit_records",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String  // yyyy-MM-dd
)

/**
 * 倒数日（Days Matter）事件。
 *
 * 正数/倒数不是存储字段——渲染时由 `DateMath` 用「今天 vs 目标日」动态判定，
 * 事件日期一过自动从「还有」迁移为「已经」（对齐 countdateapp 语义分析 §6）。
 * 日期沿用全项目约定：yyyy-MM-dd 字符串（LocalDate.parse/toString 往返）。
 */
@Entity(tableName = "countdown_events", indices = [Index(value = ["date"])])
data class CountdownEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String,                        // yyyy-MM-dd 锚点日
    val pinned: Boolean = false,             // 置顶排最前
    val repeatRule: Int = REPEAT_NONE,       // REPEAT_*：过期后滚动到下一锚点再计数
    val plusOne: Boolean = false,            // +1日：计数整体 +1（含首尾当天）
    /** 0 = 自动（倒数蓝/正数橙）；1..N = CountdownPalette 显式色 */
    val colorIndex: Int = 0,
    val highlighted: Boolean = false,        // 高亮旗标（卡片描边强调）
    val endDate: String? = null,             // 进阶：结束日 yyyy-MM-dd（详情脚注展示）
    val time: String? = null,                // 进阶：精确时间 HH:mm（详情脚注展示）
    /** -1 = 无纹理；>=0 用过程式纹理背景（详情页），照片背景按文件存在与否优先 */
    val textureIndex: Int = -1,
    /** 0 = 经典全屏（CLASSIC）；1 = 照片卡片（PHOTO_CARD） */
    val cardStyle: Int = CARD_STYLE_CLASSIC,
    /** 照片卡模糊半径 dp（0..25），经典风格不使用 */
    val blurRadius: Int = 0,
    /** 照片卡文字色：false = 白字，true = 黑字 */
    val fontDark: Boolean = false
) {
    companion object {
        const val REPEAT_NONE = 0
        const val REPEAT_YEARLY = 1
        const val REPEAT_MONTHLY = 2
        const val CARD_STYLE_CLASSIC = 0
        const val CARD_STYLE_PHOTO_CARD = 1
        const val BLUR_MAX = 25
    }
}

// Statistics data classes
data class MonthlyStat(
    val month: Int,
    val habitId: Long,
    val count: Int
)

data class YearlyStat(
    val year: Int,
    val habitId: Long,
    val count: Int
)

// Rolling-window weekly stats: weekIndex 1..10 counts backwards from the
// current week (10 = 本周). Buckets are Monday-based calendar weeks.
data class RecentWeeklyStat(
    val weekIndex: Int,
    val habitId: Long,
    val count: Int
)

data class DailyStat(
    val day: Int,
    val habitId: Long,
    val count: Int
)
