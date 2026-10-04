package com.example.diary.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.diary.util.LocaleHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LanguagePreferences(context: Context) {

    private val appContext: Context = context.applicationContext

    companion object {
        val LANGUAGE_KEY = stringPreferencesKey("app_language")
    }

    val language: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: LocaleHelper.SYSTEM
    }

    suspend fun setLanguage(value: String) {
        try {
            appContext.dataStore.edit { preferences ->
                preferences[LANGUAGE_KEY] = value
            }
            LocaleHelper.prime(value)
        } catch (e: java.io.IOException) {
            Log.w("LanguagePreferences", "Failed to persist language", e)
        }
    }
}
