package com.example.diary

import android.app.Application
import com.example.diary.data.preferences.LanguagePreferences
import com.example.diary.util.LocaleHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class DiaryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val language = runBlocking {
            try {
                LanguagePreferences(this@DiaryApplication).language.first()
            } catch (e: Exception) {
                LocaleHelper.SYSTEM
            }
        }
        LocaleHelper.prime(language)
    }
}
