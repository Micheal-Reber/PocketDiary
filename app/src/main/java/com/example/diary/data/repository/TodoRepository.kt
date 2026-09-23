package com.example.diary.data.repository

import android.content.Context
import com.example.diary.data.local.TodoDao
import com.example.diary.data.local.TodoItem
import com.example.diary.data.todo.TodoReminderScheduler
import kotlinx.coroutines.flow.Flow

/**
 * 待办薄仓库——DAO 直通 + 提醒调度（save/delete 内同步闹钟，UI 不再直接碰 Scheduler）。
 * [context] 为 null 时跳过调度（纯 CRUD 单测）。
 */
class TodoRepository(
    private val dao: TodoDao,
    private val context: Context? = null,
) {

    fun observeAll(): Flow<List<TodoItem>> = dao.observeAll()

    suspend fun get(id: Long): TodoItem? = dao.getById(id)

    suspend fun getAll(): List<TodoItem> = dao.getAll()

    suspend fun getAllWithReminder(): List<TodoItem> = dao.getAllWithReminder()

    suspend fun getDueReminders(now: Long): List<TodoItem> = dao.getDueReminders(now)

    /** id == 0 插入并返回新 id；否则按主键整条更新。落库后按结果同步闹钟。 */
    suspend fun save(item: TodoItem): Long {
        val id = if (item.id == 0L) {
            dao.insert(item)
        } else {
            dao.update(item)
            item.id
        }
        syncReminder(if (item.id == 0L) item.copy(id = id) else item)
        return id
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
        context?.let { TodoReminderScheduler.cancel(it, id) }
    }

    /** 批量更新排序（仅改 sortOrder，不动提醒） */
    suspend fun updateSortOrders(items: List<TodoItem>) = dao.updateSortOrders(items)

    private fun syncReminder(saved: TodoItem) {
        val ctx = context ?: return
        if (saved.reminderAt != null && !saved.done) {
            TodoReminderScheduler.schedule(ctx, saved)
        } else {
            TodoReminderScheduler.cancel(ctx, saved.id)
        }
    }
}
