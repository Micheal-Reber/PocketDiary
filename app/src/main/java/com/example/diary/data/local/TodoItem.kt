package com.example.diary.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 待办事项——极简本地版（对标系统提醒）
 * 核心：文本/完成态/排序 + 提醒时间 + 重复规则（不重复/每天）
 */
@Entity(
    tableName = "todo_items",
    indices = [Index(value = ["sortOrder"]), Index(value = ["reminderAt"])]
)
@Serializable
data class TodoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,                    // 1~200 字
    val done: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val reminderAt: Long? = null,        // 提醒时间戳（毫秒），null=无提醒
    val repeatRule: Int = REPEAT_NONE    // 重复规则
) {
    companion object {
        const val REPEAT_NONE = 0
        const val REPEAT_DAILY = 1
    }
}