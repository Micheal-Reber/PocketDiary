package com.example.diary.data.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.local.TodoItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TodoRepository 单元测试：覆盖 CRUD + 排序更新
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class TodoRepositoryTest {

    @get:Rule
    var instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var database: AppDatabase
    private lateinit var repository: TodoRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = TodoRepository(database.todoDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `insert returns new id`() = runBlockingTest {
        val id = repository.save(TodoItem(text = "测试待办"))
        assertTrue(id > 0)
        val item = repository.get(id)
        assertNotNull(item)
        assertEquals("测试待办", item!!.text)
        assertFalse(item.done)
        assertEquals(0, item.sortOrder)
    }

    @Test
    fun `update existing item by id`() = runBlockingTest {
        val id = repository.save(TodoItem(text = "原始"))
        val original = repository.get(id)!!
        repository.save(original.copy(text = "已修改", done = true))
        val updated = repository.get(id)!!
        assertEquals("已修改", updated.text)
        assertTrue(updated.done)
    }

    @Test
    fun `delete removes item`() = runBlockingTest {
        val id = repository.save(TodoItem(text = "待删除"))
        assertNotNull(repository.get(id))
        repository.delete(id)
        assertNull(repository.get(id))
    }

    @Test
    fun `toggle done updates item`() = runBlockingTest {
        val id = repository.save(TodoItem(text = "切换完成"))
        val item = repository.get(id)!!
        repository.save(item.copy(done = true))
        assertTrue(repository.get(id)!!.done)
        repository.save(item.copy(done = false))
        assertFalse(repository.get(id)!!.done)
    }

    @Test
    fun `updateSortOrders reorders items correctly`() = runBlockingTest {
        val id1 = repository.save(TodoItem(text = "第一项", sortOrder = 0))
        repository.save(TodoItem(text = "第二项", sortOrder = 1))
        val id3 = repository.save(TodoItem(text = "第三项", sortOrder = 2))
        var itemsBefore = getAllItems()
        assertEquals("第一项", itemsBefore[0].text)
        assertEquals("第二项", itemsBefore[1].text)
        assertEquals("第三项", itemsBefore[2].text)
        val item3 = repository.get(id3)!!
        val item1 = repository.get(id1)!!
        repository.updateSortOrders(listOf(
            item3.copy(sortOrder = item1.sortOrder),
            item1.copy(sortOrder = item3.sortOrder)
        ))
        val itemsAfter = getAllItems()
        assertEquals("第三项", itemsAfter[0].text)
        assertEquals("第二项", itemsAfter[1].text)
        assertEquals("第一项", itemsAfter[2].text)
    }

    @Test
    fun `items sorted by sortOrder then createdAt`() = runBlockingTest {
        repository.save(TodoItem(text = "C", sortOrder = 2, createdAt = 3000))
        repository.save(TodoItem(text = "A", sortOrder = 0, createdAt = 1000))
        repository.save(TodoItem(text = "B", sortOrder = 1, createdAt = 2000))
        val items = getAllItems()
        assertEquals("A", items[0].text)
        assertEquals("B", items[1].text)
        assertEquals("C", items[2].text)
    }

    @Test
    fun `save with id0 inserts new id0 updates`() = runBlockingTest {
        val id1 = repository.save(TodoItem(text = "新建"))
        assertTrue(id1 > 0)
        repository.save(TodoItem(id = id1, text = "更新后", done = true, sortOrder = 5, createdAt = System.currentTimeMillis()))
        val updated = repository.get(id1)!!
        assertEquals("更新后", updated.text)
        assertTrue(updated.done)
        assertEquals(5, updated.sortOrder)
    }

    @Test
    fun `empty text can be saved`() = runBlockingTest {
        val id = repository.save(TodoItem(text = ""))
        assertTrue(id >= 0)
    }

    private suspend fun getAllItems(): List<TodoItem> = repository.getAll()
}
