package com.example.diary.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration15To16Test {

    private lateinit var context: Context
    private var database: AppDatabase? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        clearSingleton()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        closeMigrated()
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migration 15 to 16 keeps legacy tables and rows`() {
        createV15Database()
        val db = openMigrated()

        val todos = bg { db.todoDao().getAll() }
        assertEquals(1, todos.size)
        assertEquals("买牛奶", todos[0].text)
        assertTrue(todos[0].done)
        assertEquals(1, todos[0].alarmMode)
        assertEquals(1700000600000L, todos[0].reminderAt)
        assertEquals(2, todos[0].repeatRule)

        val habits = bg { db.habitDao().getAllHabits() }
        assertEquals(1, habits.size)
        assertEquals("喝水", habits[0].name)
        assertEquals(2, habits[0].colorIndex)

        val records = bg { db.habitDao().getRecordsForDate("2026-10-10") }
        assertEquals(1, records.size)
        assertEquals(1L, records[0].habitId)

        val diaries = bg { db.diaryDao().getAllEntriesOnce() }
        assertEquals(1, diaries.size)
        assertEquals("旧日记", diaries[0].content)
        assertEquals("2026-10-09", diaries[0].date)

        val countdowns = bg { db.countdownDao().getAllOnce() }
        assertEquals(1, countdowns.size)
        assertEquals("生日", countdowns[0].name)
        assertEquals(-1, countdowns[0].remindDaysBefore)

        closeMigrated()
        assertEquals(16, rawValues("PRAGMA user_version").first().toInt())
    }

    @Test
    fun `migration 15 to 16 creates focus schema matching v16`() {
        createV15Database()
        val db = openMigrated()
        bg { db.todoDao().getAll() }
        closeMigrated()

        assertEquals(listOf("16"), rawValues("PRAGMA user_version"))
        assertEquals(
            listOf("focus_sessions", "focus_tasks"),
            rawValues("SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'focus%' ORDER BY name")
        )
        assertEquals(
            listOf(
                "index_focus_sessions_date",
                "index_focus_sessions_date_phase",
                "index_focus_sessions_taskId",
                "index_focus_tasks_sortOrder"
            ),
            rawValues("SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'index_focus%' ORDER BY name")
        )
        assertEquals(
            listOf("focus_tasks|taskId|id|NO ACTION|SET NULL|NONE"),
            rawRows("PRAGMA foreign_key_list(focus_sessions)")
                .map { parts -> parts.drop(2).joinToString("|") }
                .filter { it.startsWith("focus_tasks") }
        )
    }

    @Test
    fun `migrated database accepts focus writes and legacy writes`() {
        createV15Database()
        val db = openMigrated()

        bg {
            val taskId = db.focusDao().insertTask(FocusTask(title = "日语"))
            val session = FocusSession(
                taskId = taskId,
                date = "2026-10-15",
                startedAt = 1000L,
                endedAt = 2000L,
                plannedMs = 1_500_000L,
                actualMs = 1_500_000L,
                phase = FocusSession.PHASE_FOCUS,
                completed = true,
                cycleIndex = 1
            )
            db.focusDao().recordSessionWithCredit(session, taskId, 1_500_000L, true)
        }

        bg {
            val task = db.focusDao().getAllTasks().single()
            assertEquals(1_500_000L, task.focusMillis)
            assertEquals(1, task.sessionCount)

            val session = db.focusDao().getRecentSessions(10).single()
            assertEquals("2026-10-15", session.date)
            assertEquals(FocusSession.PHASE_FOCUS, session.phase)

            val totals = db.focusDao().getFocusTotalsForMonth("2026-10", FocusSession.PHASE_FOCUS)
            assertEquals(1, totals.size)
            assertEquals("2026-10-15", totals[0].date)
            assertEquals(1_500_000L, totals[0].total)

            assertEquals(1_500_000L, db.focusDao().getFocusTotalForDate("2026-10-15", FocusSession.PHASE_FOCUS))
            assertEquals(0L, db.focusDao().getFocusTotalForDate("2026-10-15", FocusSession.PHASE_SHORT_BREAK))
            assertNotNull(db.focusDao().getSessionsForTask(task.id, 10).first())
        }

        bg {
            val taskId = db.focusDao().getAllTasks().single().id
            db.focusDao().deleteTaskById(taskId)
            val session = db.focusDao().getRecentSessions(10).single()
            assertNull(session.taskId)
        }

        bg {
            db.todoDao().getAll()
            val id = db.diaryDao().insert(
                DiaryEntry(content = "迁移后新日记", date = "2026-10-15", createdAt = 1L, updatedAt = 2L)
            )
            assertTrue(id > 0)
            assertEquals("迁移后新日记", db.diaryDao().getEntryByDate("2026-10-15")?.content)
        }
    }

    private fun openMigrated(): AppDatabase {
        val db = AppDatabase.getInstance(context)
        database = db
        return db
    }

    private fun closeMigrated() {
        database?.close()
        database = null
        clearSingleton()
    }

    private fun clearSingleton() {
        val field = AppDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun <T> bg(block: suspend () -> T): T = runBlocking(Dispatchers.IO) { block() }

    private fun rawValues(sql: String): List<String> = rawRows(sql).map { it.joinToString("|") }

    private fun rawRows(sql: String): List<List<String>> {
        val path = context.getDatabasePath(DB_NAME).path
        val db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY)
        val out = mutableListOf<List<String>>()
        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                out.add((0 until cursor.columnCount).map { cursor.getString(it) ?: "NULL" })
            }
        }
        db.close()
        return out
    }

    private fun createV15Database() {
        val db = context.openOrCreateDatabase(DB_NAME, Context.MODE_PRIVATE, null)
        V15_SCHEMA.forEach { db.execSQL(it) }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `room_master_table` (id INTEGER PRIMARY KEY,identity_hash TEXT)"
        )
        db.execSQL("INSERT INTO `room_master_table` (id, identity_hash) VALUES (42, '$V15_IDENTITY_HASH')")
        V15_FIXTURES.forEach { db.execSQL(it) }
        db.version = 15
        db.close()
    }

    private companion object {
        const val DB_NAME = "pocket_diary.db"
        const val V15_IDENTITY_HASH = "eaf91a6fc2662575678fa8c273e74183"

        val V15_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `diary_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT NOT NULL, `date` TEXT NOT NULL, `mood` TEXT, `latitude` REAL, `longitude` REAL, `locationName` TEXT, `weather` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_diary_entries_date` ON `diary_entries` (`date`)",
            "CREATE TABLE IF NOT EXISTS `habits` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `emoji` TEXT NOT NULL, `colorIndex` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `isArchived` INTEGER NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `habit_records` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `habitId` INTEGER NOT NULL, `date` TEXT NOT NULL, FOREIGN KEY(`habitId`) REFERENCES `habits`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_records_habitId_date` ON `habit_records` (`habitId`, `date`)",
            "CREATE TABLE IF NOT EXISTS `countdown_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `date` TEXT NOT NULL, `pinned` INTEGER NOT NULL, `repeatRule` INTEGER NOT NULL, `plusOne` INTEGER NOT NULL, `colorIndex` INTEGER NOT NULL, `highlighted` INTEGER NOT NULL, `endDate` TEXT, `time` TEXT, `textureIndex` INTEGER NOT NULL, `cardStyle` INTEGER NOT NULL, `blurRadius` INTEGER NOT NULL, `fontDark` INTEGER NOT NULL, `photoOffsetY` REAL NOT NULL, `photoScale` REAL NOT NULL, `remindDaysBefore` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_countdown_events_date` ON `countdown_events` (`date`)",
            "CREATE TABLE IF NOT EXISTS `todo_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `text` TEXT NOT NULL, `done` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `reminderAt` INTEGER, `repeatRule` INTEGER NOT NULL, `alarmMode` INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_todo_items_sortOrder` ON `todo_items` (`sortOrder`)",
            "CREATE INDEX IF NOT EXISTS `index_todo_items_reminderAt` ON `todo_items` (`reminderAt`)"
        )

        val V15_FIXTURES = listOf(
            "INSERT INTO `todo_items` (`id`, `text`, `done`, `sortOrder`, `createdAt`, `reminderAt`, `repeatRule`, `alarmMode`) VALUES (1, '买牛奶', 1, 0, 1700000000000, 1700000600000, 2, 1)",
            "INSERT INTO `habits` (`id`, `name`, `emoji`, `colorIndex`, `sortOrder`, `createdAt`, `isArchived`) VALUES (1, '喝水', '💧', 2, 0, 1700000000000, 0)",
            "INSERT INTO `habit_records` (`id`, `habitId`, `date`) VALUES (1, 1, '2026-10-10')",
            "INSERT INTO `diary_entries` (`id`, `content`, `date`, `mood`, `latitude`, `longitude`, `locationName`, `weather`, `createdAt`, `updatedAt`) VALUES (1, '旧日记', '2026-10-09', 'happy', NULL, NULL, NULL, NULL, 1700000000000, 1700000000000)",
            "INSERT INTO `countdown_events` (`id`, `name`, `date`, `pinned`, `repeatRule`, `plusOne`, `colorIndex`, `highlighted`, `endDate`, `time`, `textureIndex`, `cardStyle`, `blurRadius`, `fontDark`, `photoOffsetY`, `photoScale`, `remindDaysBefore`) VALUES (1, '生日', '2026-12-01', 0, 0, 0, 3, 0, NULL, NULL, 0, 0, 16, 1, 0.0, 1.0, -1)"
        )
    }
}
