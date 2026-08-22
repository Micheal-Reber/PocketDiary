package com.example.diary.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Brand seed: ink-green diary palette. Used when dynamic (wallpaper) color
// is off, and on devices below Android 12.
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A6D55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F2D1),
    onPrimaryContainer = Color(0xFF002117),
    secondary = Color(0xFF4D6358),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF0A1F17),
    tertiary = Color(0xFF3E6374),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1E8FC),
    onTertiaryContainer = Color(0xFF001F29),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFDF9),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFFBFDF9),
    onSurface = Color(0xFF191C1A),
    // Tonal layering (ReadYou-style flat cards live on Low/Highest):
    surfaceDim = Color(0xFFD8DBD7),
    surfaceBright = Color(0xFFFBFDF9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F7F4),
    surfaceContainer = Color(0xFFEFF1EE),
    surfaceContainerHigh = Color(0xFFE9EBE8),
    surfaceContainerHighest = Color(0xFFE3E5E3),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC1CCC4),
    scrim = Color.Black,
    inverseSurface = Color(0xFF2E312E),
    inverseOnSurface = Color(0xFFEFF1EE),
    inversePrimary = Color(0xFF8AD6B6)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AD6B6),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00523E),
    onPrimaryContainer = Color(0xFFA6F2D1),
    secondary = Color(0xFFB4CDBE),
    onSecondary = Color(0xFF1F352C),
    secondaryContainer = Color(0xFF364C42),
    onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = Color(0xFFA6CCE0),
    onTertiary = Color(0xFF073544),
    tertiaryContainer = Color(0xFF254C5C),
    onTertiaryContainer = Color(0xFFC1E8FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF191C1A),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF191C1A),
    onSurface = Color(0xFFE1E3DF),
    surfaceDim = Color(0xFF111413),
    surfaceBright = Color(0xFF373A38),
    surfaceContainerLowest = Color(0xFF0E0F0E),
    surfaceContainerLow = Color(0xFF1E211F),
    surfaceContainer = Color(0xFF232625),
    surfaceContainerHigh = Color(0xFF2D302E),
    surfaceContainerHighest = Color(0xFF383B39),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF3F4943),
    scrim = Color.Black,
    inverseSurface = Color(0xFFE1E3DF),
    inverseOnSurface = Color(0xFF2E312E),
    inversePrimary = Color(0xFF1A6D55)
)

/** Shape family — every screen pulls corners from here, no literals. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DiaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = AppShapes,
        content = content
    )
}
