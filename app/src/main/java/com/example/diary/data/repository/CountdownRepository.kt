package com.example.diary.data.repository

import com.example.diary.data.local.CountdownDao
import com.example.diary.data.local.CountdownEvent
import kotlinx.coroutines.flow.Flow

/** 倒数日薄仓库——与 DiaryRepository 同风格，DAO 直通 + 少量语义。 */
class CountdownRepository(private val dao: CountdownDao) {

    fun observeAll(): Flow<List<CountdownEvent>> = dao.observeAll()

    fun observeById(id: Long): Flow<CountdownEvent?> = dao.observeById(id)

    suspend fun get(id: Long): CountdownEvent? = dao.getById(id)

    /** id == 0 插入并返回新 id；否则按主键整条更新。 */
    suspend fun save(event: CountdownEvent): Long {
        return if (event.id == 0L) dao.insert(event)
        else {
            dao.update(event)
            event.id
        }
    }

    /** 删除事件行；调用方负责同步清理其背景图文件（EventImageStore）。 */
    suspend fun delete(id: Long) = dao.deleteById(id)
}
