package com.example.diary.data.backup

import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitRecord
import kotlinx.serialization.Serializable

/**
 * Export format for data migration when switching phones.
 * Contains all user data that needs to be transferred to a new device.
 */
@Serializable
data class BackupData(
    /** App version that created this backup */
    val version: Int = 1,
    /** Timestamp when backup was created (millis since epoch) */
    val timestamp: Long = System.currentTimeMillis(),
    /** All diary entries */
    val diaryEntries: List<DiaryEntry> = emptyList(),
    /** All habits */
    val habits: List<Habit> = emptyList(),
    /** All habit records */
    val habitRecords: List<HabitRecord> = emptyList(),
    /** All countdown events */
    val countdownEvents: List<CountdownEvent> = emptyList(),
    /** User preferences from DataStore */
    val preferences: PreferencesData = PreferencesData(),
    /** Relative paths of image files included in the backup zip */
    val imageFiles: List<ImageFileInfo> = emptyList(),
)

/** User preferences from DataStore */
@Serializable
data class PreferencesData(
    /** Dark mode enabled */
    val darkMode: Boolean = false,
    /** Path to custom diary background image (relative to filesDir) */
    val diaryBackgroundPath: String? = null,
    /** Dynamic color (Material You) enabled */
    val dynamicColor: Boolean = true,
    /** Editor markdown preview enabled */
    val editorPreview: Boolean = false,
)

/** Information about an image file included in the backup */
@Serializable
data class ImageFileInfo(
    /** Relative path within the backup zip (e.g., "images/diary_photos/123/img_123.jpg") */
    val relativePath: String,
    /** Original file size in bytes */
    val sizeBytes: Long,
    /** Which entity this image belongs to (diary_entry, countdown_event, diary_background) */
    val entityType: String,
    /** Entity ID this image belongs to (diary entry ID, countdown event ID, or 0 for diary background) */
    val entityId: Long,
    /** For diary photos: index in the entry's photo list */
    val photoIndex: Int = -1,
)

/**
 * Result of an import operation
 */
sealed interface ImportResult {
    data class Success(
        val diaryEntriesImported: Int,
        val habitsImported: Int,
        val habitRecordsImported: Int,
        val countdownEventsImported: Int,
        val imagesImported: Int,
    ) : ImportResult

    data class Failure(
        val message: String,
        val details: String? = null,
    ) : ImportResult
}