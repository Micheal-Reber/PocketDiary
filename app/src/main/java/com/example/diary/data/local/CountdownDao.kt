package com.example.diary.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CountdownDao {

    /** 置顶优先，其余按锚点日升序（越近的未来排越前，正数事件按发生日先后）。 */
    @Query("SELECT * FROM countdown_events ORDER BY pinned DESC, date ASC")
    fun observeAll(): Flow<List<CountdownEvent>>

    /** One-shot read for backup export (safe inside withTransaction). */
    @Query("SELECT * FROM countdown_events ORDER BY pinned DESC, date ASC")
    suspend fun getAllOnce(): List<CountdownEvent>

    @Query("SELECT * FROM countdown_events WHERE id = :id")
    fun observeById(id: Long): Flow<CountdownEvent?>

    @Query("SELECT * FROM countdown_events WHERE id = :id")
    suspend fun getById(id: Long): CountdownEvent?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CountdownEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<CountdownEvent>)

    @Update
    suspend fun update(event: CountdownEvent)

    @Query("DELETE FROM countdown_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM countdown_events")
    suspend fun deleteAll()
}
