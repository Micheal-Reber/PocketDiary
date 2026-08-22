package com.example.diary.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemePreferences(context: Context) {

    // Always store the application context — holding an Activity context for
    // the lifetime of a singleton would leak the Activity on every config
    // change. The DataStore itself is process-scoped, so a single instance
    // is fine.
    private val appContext: Context = context.applicationContext

    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val DIARY_BACKGROUND_KEY = stringPreferencesKey("diary_background_path")
        val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    }

    val isDarkMode: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: false
    }

    /** Material You wallpaper-derived color; default ON, brand green when OFF. */
    val dynamicColor: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[DYNAMIC_COLOR_KEY] ?: true
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        try {
            appContext.dataStore.edit { preferences ->
                preferences[DYNAMIC_COLOR_KEY] = enabled
            }
        } catch (e: Exception) {
            Log.w("ThemePreferences", "Failed to persist dynamic color", e)
        }
    }

    /** Absolute path of the user-chosen diary list background image, or null. */
    val diaryBackgroundPath: Flow<String?> = appContext.dataStore.data.map { preferences ->
        preferences[DIARY_BACKGROUND_KEY]
    }

    suspend fun setDarkMode(enabled: Boolean) {
        // DataStore.edit can throw IOException if the underlying file is
        // unreadable. Swallow it rather than crashing the settings screen —
        // the worst case is the user's toggle didn't persist, which they'll
        // notice on the next launch and can try again.
        try {
            appContext.dataStore.edit { preferences ->
                preferences[DARK_MODE_KEY] = enabled
            }
        } catch (e: Exception) {
            Log.w("ThemePreferences", "Failed to persist dark mode", e)
        }
    }

    suspend fun setDiaryBackgroundPath(path: String?) {
        try {
            appContext.dataStore.edit { preferences ->
                if (path == null) preferences.remove(DIARY_BACKGROUND_KEY)
                else preferences[DIARY_BACKGROUND_KEY] = path
            }
        } catch (e: Exception) {
            Log.w("ThemePreferences", "Failed to persist diary background", e)
        }
    }
}
