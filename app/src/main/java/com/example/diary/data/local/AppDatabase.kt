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
 *
 * Migrations:
 *  - v9 与 v11 的 todo_items 结构等价 → MIGRATION_9_11 为空（保数据升级）。
 *  - v10 → v11 删两列 → 表重建（兼容 API 26–33，不用 DROP COLUMN）。
 *  - v8 及更早无手写迁移 → 仍走 fallbackToDestructiveMigration（清库）。
 */
@Database(
    entities = [DiaryEntry::class, Habit::class, HabitRecord::class, CountdownEvent::class, TodoItem::class],
    version = 11,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun habitDao(): HabitDao
    abstract fun countdownDao(): CountdownDao
    abstract fun todoDao(): TodoDao

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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pocket_diary.db"
                )
                    .addMigrations(MIGRATION_9_11, MIGRATION_10_11)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
