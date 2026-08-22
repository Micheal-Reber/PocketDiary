package com.example.diary

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.SystemBarStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.data.local.AppDatabase
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.ui.navigation.AppNavigation
import com.example.diary.ui.theme.DiaryTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val database by lazy { AppDatabase.getInstance(this) }
    private val themePreferences by lazy { ThemePreferences(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The app's dark/light mode is INDEPENDENT of the system (settings
        // toggle wins). Read it synchronously once — the prefs file is tiny —
        // and drive everything from it: splash variant, window background and
        // system-bar icon styles. The Compose content keeps following the
        // live flow so in-app toggles apply immediately.
        val startDark = runBlocking {
            try {
                themePreferences.isDarkMode.first()
            } catch (e: Exception) {
                false
            }
        }

        // Splash variant: fully effective on <Android 12 where the compat
        // library draws it; on 12+ the system's first frames stay anchored to
        // the manifest default (Dark) — see themes.xml note.
        setTheme(
            if (startDark) R.style.Theme_PocketDiary_Starting_Dark
            else R.style.Theme_PocketDiary_Starting_Light
        )

        // Splash dismisses as soon as the first frame draws — no artificial
        // hold; with the transparent icon the system splash reads as a bare
        // color beat before the diary list.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // System bars follow the APP mode too, not the system one.
        if (startDark) {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
            )
        } else {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }
        // Any frame exposed between splash dismissal and Compose's first draw
        // now matches the app mode instead of whatever the system uiMode is.
        window.setBackgroundDrawable(
            ColorDrawable(if (startDark) Color.BLACK else Color.parseColor("#FBFDF9"))
        )

        setContent {
            // collectAsStateWithLifecycle suspends collection when the activity
            // is in the background (saves a tiny bit of DataStore churn) and
            // returns the latest emitted value as soon as it's available — no
            // brief "false" flash before the first read completes.
            val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = startDark)
            val dynamicColor by themePreferences.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
            DiaryTheme(darkTheme = isDarkMode, dynamicColor = dynamicColor) {
                AppNavigation(
                    themePreferences = themePreferences,
                    database = database
                )
            }
        }
    }
}
