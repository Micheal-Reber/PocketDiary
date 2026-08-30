package com.example.diary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /** 按排序顺序观察所有待办 */
    @Query("SELECT * FROM todo_items ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<TodoItem>>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getById(id: Long): TodoItem?

    @Query("SELECT * FROM todo_items ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAll(): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE reminderAt IS NOT NULL AND done = 0")
    suspend fun getAllWithReminder(): List<TodoItem>

    @Query("SELECT * FROM todo_items WHERE reminderAt IS NOT NULL AND reminderAt <= :now AND done = 0")
    suspend fun getDueReminders(now: Long): List<TodoItem>

    @Query("UPDATE todo_items SET reminderAt = :nextAt WHERE id = :id")
    suspend fun updateReminder(id: Long, nextAt: Long?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TodoItem): Long

    @Update
    suspend fun update(item: TodoItem)

    @Query("DELETE FROM todo_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 批量更新排序（拖拽后调用） */
    @Transaction
    suspend fun updateSortOrders(items: List<TodoItem>) {
        items.forEach { update(it) }
    }
}