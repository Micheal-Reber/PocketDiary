package com.example.diary.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 专注任务——学习模式下的长期投入事项。
 * 与 todo_items 平行且互不引用：没有完成态（事项不会消失，下次继续同一个），
 * 没有提醒时间（提醒由番茄钟到点负责），只累积专注时长。
 */
@Entity(
    tableName = "focus_tasks",
    indices = [
        Index(value = ["sortOrder"])
    ]
)
@Serializable
data class FocusTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                                   // 1~200 字
    val sortOrder: Int = 0,
    val focusMillis: Long = 0L,                          // 累计专注时长（由会话回写）
    val sessionCount: Int = 0,                           // 累计完成的专注段数
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false                        // 归档而非删除，保留历史统计
)