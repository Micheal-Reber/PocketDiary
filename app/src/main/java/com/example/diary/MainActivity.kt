package com.example.diary

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.ui.navigation.AppNavigation
import com.example.diary.ui.theme.DiaryTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getInstance(this) }
    private val themePreferences by lazy { ThemePreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // System-managed splash (black bg + logo): covers the cold-start flash.
        // Dismisses on first frame, but never sooner than MIN_SPLASH_MS so the
        // logo is actually visible on fast devices instead of strobing by.
        val splashScreen = installSplashScreen()
        val startedAt = SystemClock.elapsedRealtime()
        splashScreen.setKeepOnScreenCondition {
            SystemClock.elapsedRealtime() - startedAt < MIN_SPLASH_MS
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // collectAsStateWithLifecycle suspends collection when the activity
            // is in the background (saves a tiny bit of DataStore churn) and
            // returns the latest emitted value as soon as it's available — no
            // brief "false" flash before the first read completes.
            val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
            DiaryTheme(darkTheme = isDarkMode) {
                AppNavigation(
                    themePreferences = themePreferences,
                    database = database
                )
            }
        }
    }

    private companion object {
        // Industry norm: visible splash 0.4-1.2s; system dismisses on first
        // frame, this floor just keeps it readable instead of a sub-frame strobe.
        const val MIN_SPLASH_MS = 400L
    }
}
