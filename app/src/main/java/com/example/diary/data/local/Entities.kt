package com.example.diary.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diary_entries",
    // One diary entry per date — the editor's saveEntry() flow looks up by date
    // and assumes uniqueness. Without this index, concurrent saves (or an
    // existingId race during navigation) could produce two rows for the same date.
    indices = [Index(value = ["date"], unique = true)]
)
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val date: String,  // yyyy-MM-dd
    val mood: String? = null,           // e.g. "❤️", "🌙", null = 未选
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationName: String? = null,   // reverse-geocoded or manually entered
    val weather: String? = null,        // e.g. "☀️", "🌧", null = 未取
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✅",
    val colorIndex: Int = 0,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Entity(
    tableName = "habit_records",
    foreignKeys = [
        ForeignKey(
            entity = Habit::class,
            parentColumns = ["id"],
            childColumns = ["habitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val date: String  // yyyy-MM-dd
)

// Statistics data classes
data class MonthlyStat(
    val month: Int,
    val habitId: Long,
    val count: Int
)

data class YearlyStat(
    val year: Int,
    val habitId: Long,
    val count: Int
)

// Rolling-window weekly stats: weekIndex 1..10 counts backwards from the
// current week (10 = 本周). Buckets are Monday-based calendar weeks.
data class RecentWeeklyStat(
    val weekIndex: Int,
    val habitId: Long,
    val count: Int
)

data class DailyStat(
    val day: Int,
    val habitId: Long,
    val count: Int
)
