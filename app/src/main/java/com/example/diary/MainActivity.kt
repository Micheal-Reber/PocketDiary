package com.example.diary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.ui.navigation.AppNavigation
import com.example.diary.ui.theme.DiaryTheme

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getInstance(this) }
    private val themePreferences by lazy { ThemePreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
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
}
