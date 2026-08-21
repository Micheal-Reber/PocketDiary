package com.example.diary.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version history:
 *  - v1: initial schema (DiaryEntry, Habit, HabitRecord without mood/photo/...).
 *  - v2: DiaryEntry gained mood/lat/lon/locationName/weather; the `date`
 *    column became UNIQUE.
 *  - v3: DiaryEntry dropped photoPaths (photo feature removed).
 *  - v4: DiaryEntry dropped title (diaries are identified by date alone).
 *
 * The app has never shipped publicly, so existing dev installs jump straight
 * to the latest version via destructive migration. If a real release ships,
 * switch off `fallbackToDestructiveMigration()` and supply explicit Migrations.
 */
@Database(
    entities = [DiaryEntry::class, Habit::class, HabitRecord::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao
    abstract fun habitDao(): HabitDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pocket_diary.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
