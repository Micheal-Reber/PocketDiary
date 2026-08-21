package com.example.diary

import android.app.Application

/**
 * Application class. The Room database singleton is owned by `MainActivity`
 * (the only consumer — passed into [com.example.diary.ui.navigation.AppNavigation]).
 * Keep this class as a hook for future process-wide initialization (e.g.,
 * crash reporting, logging).
 */
class DiaryApplication : Application()
