package com.example.diary.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
        val DAILY_GOAL_MINUTES_KEY = intPreferencesKey("focus_daily_goal_minutes")
        val GUARD_ENABLED_KEY = booleanPreferencesKey("focus_guard_enabled")
        val GUARD_ALLOWED_KEY = stringSetPreferencesKey("focus_guard_allowed_packages")
        val GUARD_BLOCK_COUNT_KEY = intPreferencesKey("focus_guard_block_count")
        val NOISE_KIND_KEY = stringPreferencesKey("focus_noise_kind")
        val NOISE_VOLUME_KEY = intPreferencesKey("focus_noise_volume")

        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_SHORT_BREAK_MINUTES = 5
        const val DEFAULT_LONG_BREAK_MINUTES = 15
        const val DEFAULT_LONG_BREAK_INTERVAL = 4
        const val DEFAULT_DAILY_GOAL_MINUTES = 100
        const val DEFAULT_NOISE_VOLUME = 40

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

    /** 统计页「今日目标」表盘的每日目标分钟数 */
    val dailyGoalMinutes: Flow<Int> = appContext.dataStore.data.map {
        (it[DAILY_GOAL_MINUTES_KEY] ?: DEFAULT_DAILY_GOAL_MINUTES).coerceIn(10, 720)
    }

    suspend fun setDailyGoalMinutes(minutes: Int) =
        putInt(DAILY_GOAL_MINUTES_KEY, minutes.coerceIn(10, 720), "daily goal minutes")

    /** 专注时用无障碍把离开的用户拉回（详见 FocusGuardService） */
    val guardEnabled: Flow<Boolean> = appContext.dataStore.data.map { it[GUARD_ENABLED_KEY] ?: false }

    suspend fun setGuardEnabled(enabled: Boolean) = putBoolean(GUARD_ENABLED_KEY, enabled, "guard enabled")

    /** 用户在设置里额外勾选的白名单包名（系统关键包走 FocusGuard.DEFAULT_ALLOWED_PACKAGES） */
    val guardAllowedPackages: Flow<Set<String>> = appContext.dataStore.data.map {
        it[GUARD_ALLOWED_KEY] ?: emptySet()
    }

    suspend fun setGuardAllowedPackages(packages: Set<String>) =
        putStringSet(GUARD_ALLOWED_KEY, packages, "guard allowed packages")

    val guardBlockCount: Flow<Int> = appContext.dataStore.data.map { it[GUARD_BLOCK_COUNT_KEY] ?: 0 }

    suspend fun noteGuardBlock() {
        try {
            appContext.dataStore.edit { prefs ->
                prefs[GUARD_BLOCK_COUNT_KEY] = (prefs[GUARD_BLOCK_COUNT_KEY] ?: 0) + 1
            }
        } catch (e: java.io.IOException) {
            Log.w("FocusPreferences", "Failed to persist guard block", e)
        }
    }

    suspend fun resetGuardBlockCount() = putInt(GUARD_BLOCK_COUNT_KEY, 0, "guard block count")

    val noiseKind: Flow<String> = appContext.dataStore.data.map {
        it[NOISE_KIND_KEY] ?: com.example.diary.data.focus.WhiteNoise.Kind.OFF.id
    }

    suspend fun setNoiseKind(kind: String) = putString(NOISE_KIND_KEY, kind, "noise kind")

    val noiseVolume: Flow<Int> = appContext.dataStore.data.map {
        (it[NOISE_VOLUME_KEY] ?: DEFAULT_NOISE_VOLUME).coerceIn(0, 100)
    }

    suspend fun setNoiseVolume(volume: Int) =
        putInt(NOISE_VOLUME_KEY, volume.coerceIn(0, 100), "noise volume")

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

    private suspend fun putString(key: Preferences.Key<String>, value: String, label: String) {
        try {
            appContext.dataStore.edit { it[key] = value }
        } catch (e: java.io.IOException) {
            Log.w("FocusPreferences", "Failed to persist $label", e)
        }
    }

    private suspend fun putStringSet(key: Preferences.Key<Set<String>>, value: Set<String>, label: String) {
        try {
            appContext.dataStore.edit { it[key] = value }
        } catch (e: java.io.IOException) {
            Log.w("FocusPreferences", "Failed to persist $label", e)
        }
    }
}