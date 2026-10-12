package com.example.diary

import android.app.Application
import android.util.Log
import com.example.diary.data.calendar.SystemCalendarManager
import com.example.diary.data.calendar.SystemCalendarSync
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.LanguagePreferences
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.util.LocaleHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        syncCountdownCalendarOnLaunch()
    }

    private fun syncCountdownCalendarOnLaunch() {
        if (!SystemCalendarManager.hasPermission(this)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = CountdownRepository(
                    AppDatabase.getInstance(this@DiaryApplication).countdownDao(),
                    this@DiaryApplication
                )
                SystemCalendarSync.catchUpOnLaunch(this@DiaryApplication, repo)
            } catch (e: Exception) {
                Log.w("DiaryApplication", "calendar catch-up failed", e)
            }
        }
    }
}
