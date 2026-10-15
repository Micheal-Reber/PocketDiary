package com.example.diary.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.example.diary.data.local.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 待办学习模式（番茄钟）偏好：开关 + 段长。
 * 开关只影响待办 Tab 的呈现形态，不影响普通待办的提醒调度。
 */
class FocusPreferences(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        val STUDY_MODE_KEY = booleanPreferencesKey("focus_study_mode")
        val FOCUS_MINUTES_KEY = intPreferencesKey("focus_minutes")
        val SHORT_BREAK_MINUTES_KEY = intPreferencesKey("focus_short_break_minutes")
        val LONG_BREAK_MINUTES_KEY = intPreferencesKey("focus_long_break_minutes")
        val LONG_BREAK_INTERVAL_KEY = intPreferencesKey("focus_long_break_interval")

        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_SHORT_BREAK_MINUTES = 5
        const val DEFAULT_LONG_BREAK_MINUTES = 15
        const val DEFAULT_LONG_BREAK_INTERVAL = 4

        /** 统计只认专注段，休息段不计入「专注时长」 */
        const val STAT_PHASE = FocusSession.PHASE_FOCUS
    }

    /** 待办 Tab 是否呈现为学习模式（专注任务 + 折叠的普通待办） */
    val studyMode: Flow<Boolean> = appContext.dataStore.data.map { it[STUDY_MODE_KEY] ?: false }

    suspend fun setStudyMode(enabled: Boolean) = putBoolean(STUDY_MODE_KEY, enabled, "study mode")

    val focusMinutes: Flow<Int> = appContext.dataStore.data.map {
        (it[FOCUS_MINUTES_KEY] ?: DEFAULT_FOCUS_MINUTES).coerceIn(1, 180)
    }

    suspend fun setFocusMinutes(minutes: Int) =
        putInt(FOCUS_MINUTES_KEY, minutes.coerceIn(1, 180), "focus minutes")

    val shortBreakMinutes: Flow<Int> = appContext.dataStore.data.map {
        (it[SHORT_BREAK_MINUTES_KEY] ?: DEFAULT_SHORT_BREAK_MINUTES).coerceIn(1, 60)
    }

    suspend fun setShortBreakMinutes(minutes: Int) =
        putInt(SHORT_BREAK_MINUTES_KEY, minutes.coerceIn(1, 60), "short break minutes")

    val longBreakMinutes: Flow<Int> = appContext.dataStore.data.map {
        (it[LONG_BREAK_MINUTES_KEY] ?: DEFAULT_LONG_BREAK_MINUTES).coerceIn(1, 90)
    }

    suspend fun setLongBreakMinutes(minutes: Int) =
        putInt(LONG_BREAK_MINUTES_KEY, minutes.coerceIn(1, 90), "long break minutes")

    val longBreakInterval: Flow<Int> = appContext.dataStore.data.map {
        (it[LONG_BREAK_INTERVAL_KEY] ?: DEFAULT_LONG_BREAK_INTERVAL).coerceIn(2, 12)
    }

    suspend fun setLongBreakInterval(count: Int) =
        putInt(LONG_BREAK_INTERVAL_KEY, count.coerceIn(2, 12), "long break interval")

    private suspend fun putBoolean(key: Preferences.Key<Boolean>, value: Boolean, label: String) {
        try {
            appContext.dataStore.edit { it[key] = value }
        } catch (e: java.io.IOException) {
            Log.w("FocusPreferences", "Failed to persist $label", e)
        }
    }

    private suspend fun putInt(key: Preferences.Key<Int>, value: Int, label: String) {
        try {
            appContext.dataStore.edit { it[key] = value }
        } catch (e: java.io.IOException) {
            Log.w("FocusPreferences", "Failed to persist $label", e)
        }
    }
}