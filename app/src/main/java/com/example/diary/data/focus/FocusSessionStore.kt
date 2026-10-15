package com.example.diary.data.focus

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.diary.data.local.FocusSession
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.preferences.dataStore
import kotlinx.coroutines.flow.first

/**
 * 当前番茄钟会话的持久化快照。
 * 整个对象序列化成一条 JSON 存进 DataStore —— 键少、可整体读写、不怕字段增删。
 */
@kotlinx.serialization.Serializable
data class FocusRecord(
    val taskId: Long? = null,
    val taskTitle: String = "",
    val phase: Int = FocusSession.PHASE_FOCUS,
    val cycleIndex: Int = 0,
    val startElapsed: Long = 0L,
    val pauseElapsed: Long = 0L,
    val pausedAccumMs: Long = 0L,
    val running: Boolean = false,
    val focusMinutes: Int = FocusPreferences.DEFAULT_FOCUS_MINUTES,
    val shortBreakMinutes: Int = FocusPreferences.DEFAULT_SHORT_BREAK_MINUTES,
    val longBreakMinutes: Int = FocusPreferences.DEFAULT_LONG_BREAK_MINUTES,
    val longBreakInterval: Int = FocusPreferences.DEFAULT_LONG_BREAK_INTERVAL,
    val startWallClock: Long = 0L,
    val bootInstant: Long = 0L,
    val autoStartNext: Boolean = true
) {
    fun segments() = FocusTimer.Segments.of(
        focusMinutes, shortBreakMinutes, longBreakMinutes, longBreakInterval
    )

    fun toSession() = FocusTimer.Session(
        startElapsed = startElapsed,
        pauseElapsed = pauseElapsed,
        pausedAccumMs = pausedAccumMs,
        phase = phase,
        cycleIndex = cycleIndex,
        running = running
    )
}

class FocusSessionStore(context: Context) {

    private val appContext: Context = context.applicationContext
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private companion object {
        val RECORD_KEY: Preferences.Key<String> = stringPreferencesKey("focus_record")
    }

    suspend fun save(record: FocusRecord) {
        try {
            val encoded = json.encodeToString(FocusRecord.serializer(), record)
            appContext.dataStore.edit { it[RECORD_KEY] = encoded }
        } catch (e: java.io.IOException) {
            Log.w("FocusSessionStore", "Failed to persist focus record", e)
        } catch (e: IllegalArgumentException) {
            Log.w("FocusSessionStore", "Failed to encode focus record", e)
        }
    }

    /**
     * 读回快照。设备重启过（哨兵对不上）或时间戳落在未来 → 视为陈旧，返回 null。
     */
    suspend fun load(): FocusRecord? {
        val raw = try {
            appContext.dataStore.data.first()[RECORD_KEY]
        } catch (e: java.io.IOException) {
            Log.w("FocusSessionStore", "Failed to read focus record", e)
            return null
        } ?: return null

        val record = try {
            json.decodeFromString(FocusRecord.serializer(), raw)
        } catch (e: IllegalArgumentException) {
            Log.w("FocusSessionStore", "Failed to decode focus record", e)
            return null
        }

        val currentBoot = FocusTimer.bootInstant(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime())
        if (record.bootInstant != 0L &&
            FocusTimer.isStaleBootInstant(record.bootInstant, currentBoot)
        ) {
            return null
        }
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        if (record.startElapsed > nowElapsed) return null
        return record
    }

    suspend fun clear() {
        try {
            appContext.dataStore.edit { it.remove(RECORD_KEY) }
        } catch (e: java.io.IOException) {
            Log.w("FocusSessionStore", "Failed to clear focus record", e)
        }
    }
}