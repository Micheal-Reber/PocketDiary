package com.example.diary.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.R
import com.example.diary.data.calendar.SystemCalendarManager
import com.example.diary.data.calendar.SystemCalendarSync
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.preferences.CalendarPreferences
import com.example.diary.data.preferences.LanguagePreferences
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.theme.Spacing
import com.example.diary.util.LocaleHelper
import kotlinx.coroutines.launch

@Composable
fun AppearanceSettingsScreen(
    themePreferences: ThemePreferences,
    languagePreferences: LanguagePreferences,
    calendarPreferences: CalendarPreferences,
    countdownRepository: CountdownRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val dynamicColor by themePreferences.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val floatingBar by themePreferences.floatingBar.collectAsStateWithLifecycle(initialValue = true)
    val calendarSync by calendarPreferences.syncToSystemCalendar.collectAsStateWithLifecycle(initialValue = false)
    val bgPath by themePreferences.diaryBackgroundPath.collectAsStateWithLifecycle(initialValue = null)
    val currentLang by languagePreferences.language.collectAsStateWithLifecycle(initialValue = LocaleHelper.SYSTEM)

    // 日历没授权时先存开关，授权结果回来后再补一次全量对账，
    // 否则"先开开关后授权"的组合会让已存在的倒数日永远不同步。
    var pendingCalendarSync by remember { mutableStateOf(false) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[android.Manifest.permission.READ_CALENDAR] == true &&
            grants[android.Manifest.permission.WRITE_CALENDAR] == true
        if (ok) {
            pendingCalendarSync = false
            SystemCalendarSync.syncNow(context, countdownRepository)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.settings_calendar_perm_denied))
            }
        }
    }

    // 日记背景：选中的照片复制进私有目录，存的是 filesDir 相对路径（null = 默认纯色背景）
    val pickBackground = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val file = BackgroundImageStore.importFromUri(context, uri)
                if (file != null) {
                    themePreferences.setDiaryBackgroundPath(BackgroundImageStore.RELATIVE_PATH)
                }
            }
        }
    }

    var langDialog by remember { mutableStateOf(false) }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_section_appearance),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
                SettingsGroupCard {
                    SettingsRow(
                        headline = stringResource(R.string.settings_dark_mode),
                        supporting = stringResource(R.string.settings_dark_mode_desc),
                        leading = {
                            Icon(
                                Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = {
                            Switch(
                                checked = isDarkMode,
                                onCheckedChange = { checked ->
                                    scope.launch { themePreferences.setDarkMode(checked) }
                                }
                            )
                        }
                    )

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_dynamic_color),
                        supporting = if (dynamicColor) stringResource(R.string.settings_dynamic_color_on)
                        else stringResource(R.string.settings_dynamic_color_off),
                        leading = {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = {
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { checked ->
                                    scope.launch { themePreferences.setDynamicColor(checked) }
                                }
                            )
                        }
                    )

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_nav_bar),
                        supporting = if (floatingBar) {
                            stringResource(R.string.settings_nav_bar_floating)
                        } else {
                            stringResource(R.string.settings_nav_bar_docked)
                        },
                        leading = {
                            Icon(
                                Icons.Default.ViewCarousel,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = {
                            Switch(
                                checked = floatingBar,
                                onCheckedChange = { checked ->
                                    scope.launch { themePreferences.setFloatingBar(checked) }
                                }
                            )
                        }
                    )

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_calendar_sync),
                        supporting = if (calendarSync) stringResource(R.string.settings_calendar_sync_on)
                        else stringResource(R.string.settings_calendar_sync_off),
                        leading = {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = {
                            Switch(
                                checked = calendarSync,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        scope.launch {
                                            calendarPreferences.setSyncToSystemCalendar(true)
                                        }
                                        if (SystemCalendarManager.hasPermission(context)) {
                                            SystemCalendarSync.syncNow(context, countdownRepository)
                                        } else {
                                            pendingCalendarSync = true
                                            calendarPermissionLauncher.launch(
                                                arrayOf(
                                                    android.Manifest.permission.READ_CALENDAR,
                                                    android.Manifest.permission.WRITE_CALENDAR
                                                )
                                            )
                                        }
                                    } else {
                                        scope.launch { calendarPreferences.setSyncToSystemCalendar(false) }
                                    }
                                }
                            )
                        }
                    )

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_background),
                        supporting = if (bgPath != null) stringResource(R.string.settings_background_custom)
                        else stringResource(R.string.settings_background_default),
                        leading = {
                            Icon(
                                Icons.Default.Wallpaper,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailing = {
                            if (bgPath != null) {
                                TextButton(onClick = {
                                    scope.launch {
                                        BackgroundImageStore.clear(context)
                                        themePreferences.setDiaryBackgroundPath(null)
                                    }
                                }) { Text(stringResource(R.string.settings_restore_default)) }
                            }
                        },
                        modifier = Modifier.clickable {
                            pickBackground.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_language),
                        supporting = when (currentLang) {
                            LocaleHelper.ZH -> stringResource(R.string.lang_zh)
                            LocaleHelper.EN -> stringResource(R.string.lang_en)
                            else -> stringResource(R.string.lang_system)
                        },
                        leading = {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { langDialog = true }
                    )
                }

                Spacer(Modifier.height(BottomBarContentInset))
        }
    }

    if (langDialog) {
        AlertDialog(
            onDismissRequest = { langDialog = false },
            title = { Text(stringResource(R.string.settings_language)) },
            text = {
                Column {
                    listOf(
                        LocaleHelper.SYSTEM to R.string.lang_system,
                        LocaleHelper.ZH to R.string.lang_zh,
                        LocaleHelper.EN to R.string.lang_en,
                    ).forEach { (value, labelRes) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    langDialog = false
                                    if (value != currentLang) {
                                        scope.launch {
                                            languagePreferences.setLanguage(value)
                                            (context as? android.app.Activity)?.recreate()
                                        }
                                    }
                                }
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = value == currentLang, onClick = null)
                            Spacer(Modifier.width(Spacing.s))
                            Text(stringResource(labelRes))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { langDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}