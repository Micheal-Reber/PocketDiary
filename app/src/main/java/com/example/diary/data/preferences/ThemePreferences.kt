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

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class ThemePreferences(context: Context) {

    // Always store the application context — holding an Activity context for
    // the lifetime of a singleton would leak the Activity on every config
    // change. The DataStore itself is process-scoped, so a single instance
    // is fine.
    private val appContext: Context = context.applicationContext

    companion object {
        val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
        val IMAGE_BELOW_TITLE_KEY = booleanPreferencesKey("image_below_title")
    }

    val isDarkMode: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[DARK_MODE_KEY] ?: false
    }

    val imageBelowTitle: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        preferences[IMAGE_BELOW_TITLE_KEY] ?: true
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

    suspend fun setImageBelowTitle(enabled: Boolean) {
        try {
            appContext.dataStore.edit { preferences ->
                preferences[IMAGE_BELOW_TITLE_KEY] = enabled
            }
        } catch (e: Exception) {
            Log.w("ThemePreferences", "Failed to persist image mode", e)
        }
    }
}
