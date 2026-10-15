package com.example.diary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version history:
 *  - v1: initial schema (DiaryEntry, Habit, HabitRecord without mood/photo/...).
 *  - v2: DiaryEntry gained mood/lat/lon/locationName/weather; the `date`
 *    column became UNIQUE.
 *  - v3: DiaryEntry dropped photoPaths (photo feature removed).
 *  - v4: DiaryEntry dropped title (diaries are identified by date alone).
 *  - v5: added CountdownEvent（倒数日 Days Matter）。
 *  - v6: CountdownEvent 新增卡片风格/模糊半径/字色三字段（照片卡片功能）。
 *  - v7: Vikunja 任务式扩展（已回退）。
 *  - v8: 新增 TodoItem（简易待办：文本/完成态/排序/创建时间）。
 *  - v9: TodoItem 新增 reminderAt / repeatRule（提醒时间+每天重复，贴合系统待办）。
 *  - v10: TodoItem 新增 parentId / hasSubtasks（已随小组件回退）。
 *  - v11: TodoItem 移除 parentId / hasSubtasks（小组件功能整体删除）。
 *  - v12: CountdownEvent 新增照片卡图片垂直位置。
 *  - v13: CountdownEvent 新增照片卡图片缩放比例。
 *  - v14: TodoItem 新增 alarmMode（通知提醒 / 闹钟响铃）。
 *  - v15: CountdownEvent 新增 remindDaysBefore（提前几天提醒）。
 *  - v16: 新增 FocusTask / FocusSession（待办学习模式·番茄钟）。纯新增两张表，
 *    todo_items / habit_records 等旧表一列未动 —— 学习模式开关只改 UI 呈现，
 *    普通待办的提醒调度完全不受影响。
 *
 * Migrations:
 *  - v9 与 v11 的 todo_items 结构等价 → MIGRATION_9_11 为空（保数据升级）。
 *  - v10 → v11 删两列 → 表重建（兼容 API 26–33，不用 DROP COLUMN）。
 *  - v15 → v16 只建新表与索引，不触碰任何旧表。
 *  - v8 及更早无手写迁移 → 仍走 fallbackToDestructiveMigration（清库）。
 */
@Database(
    entities = [DiaryEntry::class, Habit::class, HabitRecord::class, CountdownEvent::class, TodoItem::class, FocusTask::class, FocusSession::class],
    version = 16,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun habitDao(): HabitDao
    abstract fun countdownDao(): CountdownDao
    abstract fun todoDao(): TodoDao
    abstract fun focusDao(): FocusDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** v9 设备：todo 表结构已与 v11 一致，无 SQL 可跑。 */
        private val MIGRATION_9_11 = object : Migration(9, 11) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        /**
         * v10 → v11：移除 parentId / hasSubtasks。
         * 表重建而非 DROP COLUMN（SQLite 3.35+ 才有，Android 要 API 34）。
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `todo_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `text` TEXT NOT NULL,
                        `done` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `reminderAt` INTEGER,
                        `repeatRule` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `todo_items_new`
                        (`id`, `text`, `done`, `sortOrder`, `createdAt`, `reminderAt`, `repeatRule`)
                    SELECT `id`, `text`, `done`, `sortOrder`, `createdAt`, `reminderAt`, `repeatRule`
                    FROM `todo_items`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `todo_items`")
                db.execSQL("ALTER TABLE `todo_items_new` RENAME TO `todo_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_sortOrder` ON `todo_items` (`sortOrder`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_todo_items_reminderAt` ON `todo_items` (`reminderAt`)")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `countdown_events` ADD COLUMN `photoOffsetY` REAL NOT NULL DEFAULT 0.0"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `countdown_events` ADD COLUMN `photoScale` REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `todo_items` ADD COLUMN `alarmMode` INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `countdown_events` ADD COLUMN `remindDaysBefore` INTEGER NOT NULL DEFAULT -1"
                )
            }
        }

        /**
         * v15 → v16：只新增 focus_tasks / focus_sessions 两张表。
         * 旧表一律不碰 —— 学习模式是 UI 层开关，不该逼用户迁数据。
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_tasks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `focusMillis` INTEGER NOT NULL,
                        `sessionCount` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `archived` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_tasks_sortOrder` ON `focus_tasks` (`sortOrder`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `focus_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `taskId` INTEGER,
                        `date` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `plannedMs` INTEGER NOT NULL,
                        `actualMs` INTEGER NOT NULL,
                        `phase` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `cycleIndex` INTEGER NOT NULL,
                        FOREIGN KEY(`taskId`) REFERENCES `focus_tasks`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_sessions_taskId` ON `focus_sessions` (`taskId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_sessions_date` ON `focus_sessions` (`date`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_focus_sessions_date_phase` ON `focus_sessions` (`date`, `phase`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pocket_diary.db"
                )
                    .addMigrations(MIGRATION_9_11, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
