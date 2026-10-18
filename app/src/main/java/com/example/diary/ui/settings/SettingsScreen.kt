package com.example.diary.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.BuildConfig
import com.example.diary.R
import com.example.diary.data.preferences.AppLockPreferences
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.theme.Spacing

/** 设置目录页：每项点进二级页（Hoshi 的 Profile/外观/行为/高级 结构） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePreferences: ThemePreferences,
    focusPreferences: FocusPreferences,
    appLockPreferences: AppLockPreferences,
    onOpenAppearance: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val darkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val studyMode by focusPreferences.studyMode.collectAsStateWithLifecycle(initialValue = false)
    val lockEnabled by appLockPreferences.lockEnabled.collectAsStateWithLifecycle(initialValue = false)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroupCard {
                SettingsNavRow(
                    icon = {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    title = stringResource(R.string.settings_section_appearance),
                    summary = if (darkMode) stringResource(R.string.settings_dark_mode_on)
                    else stringResource(R.string.settings_dark_mode_off),
                    onClick = onOpenAppearance
                )

                GroupDivider()

                SettingsNavRow(
                    icon = {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    title = stringResource(R.string.settings_section_todo),
                    summary = if (studyMode) stringResource(R.string.settings_enabled)
                    else stringResource(R.string.settings_disabled),
                    onClick = onOpenFocus
                )

                GroupDivider()

                SettingsNavRow(
                    icon = {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    title = stringResource(R.string.settings_section_privacy),
                    summary = if (lockEnabled) stringResource(R.string.settings_enabled)
                    else stringResource(R.string.settings_disabled),
                    onClick = onOpenPrivacy
                )

                GroupDivider()

                SettingsNavRow(
                    icon = {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    title = stringResource(R.string.settings_migration),
                    onClick = onOpenBackup
                )
            }

            Spacer(Modifier.height(Spacing.l))

            SettingsGroupCard {
                SettingsNavRow(
                    icon = {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    title = "PocketDiary",
                    summary = stringResource(R.string.settings_about_version_short, BuildConfig.VERSION_NAME),
                    onClick = onOpenAbout
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
            Spacer(Modifier.height(BottomBarContentInset))
        }
    }
}