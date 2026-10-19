package com.example.diary.data.backup

import com.example.diary.data.focus.WhiteNoise
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.local.FocusSession
import com.example.diary.data.local.FocusTask
import com.example.diary.data.local.Habit
import com.example.diary.data.local.HabitRecord
import com.example.diary.data.local.TodoItem
import com.example.diary.data.preferences.AppLockPreferences
import com.example.diary.data.preferences.FocusPreferences
import kotlinx.serialization.Serializable

/**
 * Export format for data migration when switching phones.
 * Contains all user data that needs to be transferred to a new device.
 */
@Serializable
data class BackupData(
    /** Backup format version (4 = preferences split into groups + focus audio) */
    val version: Int = CURRENT_VERSION,
    /** Timestamp when backup was created (millis since epoch) */
    val timestamp: Long = System.currentTimeMillis(),
    /** All diary entries */
    val diaryEntries: List<DiaryEntry> = emptyList(),
    /** All habits (including archived) */
    val habits: List<Habit> = emptyList(),
    /** All habit records */
    val habitRecords: List<HabitRecord> = emptyList(),
    /** All countdown events */
    val countdownEvents: List<CountdownEvent> = emptyList(),
    /** All todos */
    val todos: List<TodoItem> = emptyList(),
    val focusTasks: List<FocusTask> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    /** User preferences from DataStore */
    val preferences: PreferencesData = PreferencesData(),
    /** Relative paths of media files included in the backup zip */
    val imageFiles: List<ImageFileInfo> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 4
    }
}

/**
 * 各分组为 null 表示"这份备份里没有这一组"——导入时不覆盖本机对应偏好，
 * 这样 v1..v3 的旧包（没有 focus/lock/calendar 分组）导入时不会把用户的番茄钟设置抹成默认值。
 */
@Serializable
data class PreferencesData(
    // v1..v3 的扁平字段，仅为读旧包保留；v4 起导出只写分组字段
    val darkMode: Boolean? = null,
    val diaryBackgroundPath: String? = null,
    val dynamicColor: Boolean? = null,
    val editorPreview: Boolean? = null,
    val theme: ThemePrefs? = null,
    val focus: FocusPrefs? = null,
    val lock: LockPrefs? = null,
    val calendar: CalendarPrefs? = null,
)

@Serializable
data class ThemePrefs(
    val darkMode: Boolean = false,
    /** Path to custom diary background image (relative to filesDir) */
    val diaryBackgroundPath: String? = null,
    val dynamicColor: Boolean = true,
    val editorPreview: Boolean = false,
    val floatingBar: Boolean = true,
)

/** 语言不备份：换机后跟新机系统走更符合直觉 */
@Serializable
data class FocusPrefs(
    val studyMode: Boolean = false,
    val focusMinutes: Int = FocusPreferences.DEFAULT_FOCUS_MINUTES,
    val shortBreakMinutes: Int = FocusPreferences.DEFAULT_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = FocusPreferences.DEFAULT_LONG_BREAK_MINUTES,
    val longBreakInterval: Int = FocusPreferences.DEFAULT_LONG_BREAK_INTERVAL,
    val dailyGoalMinutes: Int = FocusPreferences.DEFAULT_DAILY_GOAL_MINUTES,
    val guardEnabled: Boolean = false,
    val guardAllowedPackages: Set<String> = emptySet(),
    val guardAllowLauncher: Boolean = FocusPreferences.DEFAULT_GUARD_ALLOW_LAUNCHER,
    val noiseKind: String = WhiteNoise.Kind.OFF.id,
    val noiseVolume: Int = FocusPreferences.DEFAULT_NOISE_VOLUME,
    /** 自定义白噪音音频在备份包内的相对路径（换机要重写成新机绝对路径） */
    val customNoiseFile: String? = null,
    val customNoiseName: String? = null,
    val notificationEnhanced: Boolean = FocusPreferences.DEFAULT_NOTIFICATION_ENHANCED,
    val alarmEnabled: Boolean = FocusPreferences.DEFAULT_ALARM_ENABLED,
    val alarmVolume: Int = FocusPreferences.DEFAULT_ALARM_VOLUME,
    val alarmVibrate: Boolean = FocusPreferences.DEFAULT_ALARM_VIBRATE,
    val alarmVibrateMs: Int = FocusPreferences.DEFAULT_ALARM_VIBRATE_MS,
    val alarmVibrateGapMs: Int = FocusPreferences.DEFAULT_ALARM_VIBRATE_GAP_MS,
    val alarmAmplitude: Int = FocusPreferences.DEFAULT_ALARM_AMPLITUDE,
    val alarmScreenOn: Boolean = FocusPreferences.DEFAULT_ALARM_SCREEN_ON,
)

/** PIN 哈希/盐/位数都不备份：拿到备份包等于拿到密码 */
@Serializable
data class LockPrefs(
    val lockMode: Int = AppLockPreferences.MODE_EVERY_APP,
)

@Serializable
data class CalendarPrefs(
    val syncToSystemCalendar: Boolean = false,
)

/** Information about a media file included in the backup */
@Serializable
data class ImageFileInfo(
    /** Relative path within the backup zip (e.g., "diary_photos/123/img_123.jpg") */
    val relativePath: String,
    /** Original file size in bytes */
    val sizeBytes: Long,
    /** Which entity this file belongs to (diary_entry, countdown_event, diary_background, focus_audio) */
    val entityType: String,
    /** Entity ID this file belongs to (diary entry ID, countdown event ID, or 0 for global files) */
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
        val todosImported: Int,
        val focusTasksImported: Int,
        val focusSessionsImported: Int,
        val imagesImported: Int,
        val audioFilesImported: Int,
    ) : ImportResult

    data class Failure(
        val message: String,
        val details: String? = null,
    ) : ImportResult
}