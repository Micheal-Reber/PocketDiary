package com.example.diary.data.repository

import com.example.diary.data.local.TodoDao
import com.example.diary.data.local.TodoItem
import kotlinx.coroutines.flow.Flow

/** 待办薄仓库——DAO 直通 + 少量语义 */
class TodoRepository(private val dao: TodoDao) {

    fun observeAll(): Flow<List<TodoItem>> = dao.observeAll()

    suspend fun get(id: Long): TodoItem? = dao.getById(id)

    suspend fun getAll(): List<TodoItem> = dao.getAll()

    suspend fun getAllWithReminder(): List<TodoItem> = dao.getAllWithReminder()

    suspend fun getDueReminders(now: Long): List<TodoItem> = dao.getDueReminders(now)

    suspend fun updateReminder(id: Long, nextAt: Long?) = dao.updateReminder(id, nextAt)

    /** id == 0 插入并返回新 id；否则按主键整条更新。 */
    suspend fun save(item: TodoItem): Long {
        return if (item.id == 0L) dao.insert(item)
        else {
            dao.update(item)
            item.id
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** 批量更新排序（拖拽后调用） */
    suspend fun updateSortOrders(items: List<TodoItem>) = dao.updateSortOrders(items)
}