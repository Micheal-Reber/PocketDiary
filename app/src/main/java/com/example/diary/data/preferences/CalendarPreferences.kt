package com.example.diary.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.calendarDataStore: DataStore<Preferences> by preferencesDataStore(name = "calendar_settings")

class CalendarPreferences(context: Context) {

    private val appContext: Context = context.applicationContext

    val syncToSystemCalendar: Flow<Boolean> = appContext.calendarDataStore.data.map { prefs ->
        prefs[SYNC_KEY] ?: false
    }

    suspend fun setSyncToSystemCalendar(enabled: Boolean) {
        try {
            appContext.calendarDataStore.edit { it[SYNC_KEY] = enabled }
        } catch (e: java.io.IOException) {
            Log.w("CalendarPreferences", "Failed to persist calendar sync", e)
        }
    }

    companion object {
        val SYNC_KEY = booleanPreferencesKey("sync_to_system_calendar")
    }
}