package com.example.diary.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.BuildConfig
import com.example.diary.R
import com.example.diary.data.backup.BackupRepository
import com.example.diary.data.backup.ImportResult
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.preferences.AppLockPreferences
import com.example.diary.data.preferences.LanguagePreferences
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.ui.lock.AppLockScreen
import com.example.diary.ui.lock.LockMode
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.theme.Spacing
import com.example.diary.util.LocaleHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePreferences: ThemePreferences,
    backupRepository: BackupRepository,
    appLockPreferences: AppLockPreferences,
    languagePreferences: LanguagePreferences,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val dynamicColor by themePreferences.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val floatingBar by themePreferences.floatingBar.collectAsStateWithLifecycle(initialValue = true)
    // Diary list background: picked photo copied into private storage; the
    // stored value is filesDir-relative (null = default color background).
    val bgPath by themePreferences.diaryBackgroundPath.collectAsStateWithLifecycle(initialValue = null)
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

    // Export/Import launchers — busy 防双击；导入前二次确认（全量覆盖）
    var backupBusy by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val lockEnabled by appLockPreferences.lockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val hasLockPassword by appLockPreferences.hasPassword.collectAsStateWithLifecycle(initialValue = false)
    val lockMode by appLockPreferences.lockMode.collectAsStateWithLifecycle(initialValue = AppLockPreferences.MODE_EVERY_APP)
    val lockPinLength by appLockPreferences.pinLength.collectAsStateWithLifecycle(initialValue = 4)
    // 全屏设置密码页（开启时若无密码 / 修改密码）
    var showLockSetup by remember { mutableStateOf(false) }
    // 关闭密码锁前的全屏验证
    var showLockVerify by remember { mutableStateOf(false) }
    var showLockModeDialog by remember { mutableStateOf(false) }
    var backupExpanded by remember { mutableStateOf(false) }
    var langDialog by remember { mutableStateOf(false) }
    val currentLang by languagePreferences.language.collectAsStateWithLifecycle(initialValue = LocaleHelper.SYSTEM)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && !backupBusy) {
            scope.launch {
                backupBusy = true
                try {
                    val result = backupRepository.export(uri)
                    result.onSuccess {
                        showToast(context, context.getString(R.string.settings_export_success))
                    }.onFailure { e ->
                        showToast(context, context.getString(R.string.settings_export_failed, e.message))
                    }
                } finally {
                    backupBusy = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !backupBusy) {
            pendingImportUri = uri
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
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
        // 导入确认弹窗（破坏性：清空现有数据）
        if (pendingImportUri != null) {
            AlertDialog(
                onDismissRequest = { pendingImportUri = null },
                title = { Text(stringResource(R.string.settings_import_confirm_title)) },
                text = { Text(stringResource(R.string.settings_import_confirm_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val uri = pendingImportUri ?: return@TextButton
                            pendingImportUri = null
                            scope.launch {
                                backupBusy = true
                                try {
                                    when (val result = backupRepository.importData(uri)) {
                                        is ImportResult.Success -> {
                                            showToast(
                                                context,
                                                context.getString(
                                                    R.string.settings_import_success,
                                                    result.diaryEntriesImported,
                                                    result.habitsImported,
                                                    result.habitRecordsImported,
                                                    result.countdownEventsImported,
                                                    result.todosImported,
                                                    result.imagesImported
                                                )
                                            )
                                        }
                                        is ImportResult.Failure -> {
                                            showToast(context, context.getString(R.string.settings_import_failed, result.message))
                                        }
                                    }
                                } finally {
                                    backupBusy = false
                                }
                            }
                        },
                        enabled = !backupBusy
                    ) { Text(stringResource(R.string.settings_import_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImportUri = null }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }

        if (langDialog) {
            AlertDialog(
                onDismissRequest = { langDialog = false },
                title = { Text(stringResource(R.string.settings_language)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
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
                                                (context as? Activity)?.recreate()
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance section
            Text(
                stringResource(R.string.settings_section_appearance),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsRow(
                headline = stringResource(R.string.settings_dark_mode),
                supporting = stringResource(R.string.settings_dark_mode_desc),
                leading = { Icon(Icons.Default.DarkMode, contentDescription = null) },
                trailing = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { checked ->
                            scope.launch { themePreferences.setDarkMode(checked) }
                        }
                    )
                }
            )

            SettingsRow(
                headline = stringResource(R.string.settings_dynamic_color),
                supporting = if (dynamicColor) stringResource(R.string.settings_dynamic_color_on)
                else stringResource(R.string.settings_dynamic_color_off),
                leading = { Icon(Icons.Default.Palette, contentDescription = null) },
                trailing = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { checked ->
                            scope.launch { themePreferences.setDynamicColor(checked) }
                        }
                    )
                }
            )

            SettingsRow(
                headline = stringResource(R.string.settings_background),
                supporting = if (bgPath != null) stringResource(R.string.settings_background_custom)
                else stringResource(R.string.settings_background_default),
                leading = { Icon(Icons.Default.Wallpaper, contentDescription = null) },
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

            SettingsRow(
                headline = stringResource(R.string.settings_language),
                supporting = when (currentLang) {
                    LocaleHelper.ZH -> stringResource(R.string.lang_zh)
                    LocaleHelper.EN -> stringResource(R.string.lang_en)
                    else -> stringResource(R.string.lang_system)
                },
                leading = { Icon(Icons.Default.Language, contentDescription = null) },
                modifier = Modifier.clickable { langDialog = true }
            )

            SettingsRow(
                headline = stringResource(R.string.settings_nav_bar),
                supporting = if (floatingBar) {
                    stringResource(R.string.settings_nav_bar_floating)
                } else {
                    stringResource(R.string.settings_nav_bar_docked)
                },
                leading = { Icon(Icons.Default.ViewCarousel, contentDescription = null) },
                trailing = {
                    Switch(
                        checked = floatingBar,
                        onCheckedChange = { checked ->
                            scope.launch { themePreferences.setFloatingBar(checked) }
                        }
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Privacy section
            Text(
                stringResource(R.string.settings_section_privacy),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsRow(
                headline = stringResource(R.string.settings_lock),
                supporting = stringResource(R.string.settings_lock_desc),
                leading = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailing = {
                    Switch(
                        checked = lockEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !hasLockPassword) {
                                showLockSetup = true
                            } else if (!checked && hasLockPassword && lockEnabled) {
                                showLockVerify = true
                            } else {
                                scope.launch { appLockPreferences.setLockEnabled(checked) }
                            }
                        }
                    )
                }
            )

            if (lockEnabled && hasLockPassword) {
                SettingsRow(
                    headline = stringResource(R.string.settings_change_pin),
                    supporting = stringResource(R.string.settings_change_pin_desc),
                    leading = { Icon(Icons.Default.Password, contentDescription = null) },
                    modifier = Modifier.clickable { showLockSetup = true }
                )

                SettingsRow(
                    headline = stringResource(R.string.settings_lock_mode),
                    supporting = when (lockMode) {
                        AppLockPreferences.MODE_EVERY_DIARY -> stringResource(R.string.settings_lock_mode_every_diary)
                        AppLockPreferences.MODE_DAILY -> stringResource(R.string.settings_lock_mode_daily)
                        else -> stringResource(R.string.settings_lock_mode_every_app)
                    },
                    leading = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    modifier = Modifier.clickable { showLockModeDialog = true }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Data Migration section（默认折叠，点头部展开）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { backupExpanded = !backupExpanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_migration),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (backupExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (backupExpanded) stringResource(R.string.settings_collapse) else stringResource(R.string.settings_expand),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            if (backupExpanded) {
                SettingsRow(
                    headline = stringResource(R.string.settings_export),
                    supporting = if (backupBusy) stringResource(R.string.settings_busy) else stringResource(R.string.settings_export_desc),
                    leading = {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !backupBusy) {
                            exportLauncher.launch(context.getString(R.string.settings_backup_filename))
                        }
                )

                SettingsRow(
                    headline = stringResource(R.string.settings_import),
                    supporting = if (backupBusy) stringResource(R.string.settings_busy) else stringResource(R.string.settings_import_desc),
                    leading = {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !backupBusy) {
                            importLauncher.launch(arrayOf("application/zip"))
                        }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // About section
            Text(
                stringResource(R.string.settings_section_about),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            SettingsRow(
                headline = "PocketDiary",
                supporting = stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME),
                leading = { Icon(Icons.Default.Info, contentDescription = null) }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(Modifier.height(32.dp))

            // Footer
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Made by Xuan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(BottomBarContentInset))
        }
        }

        // 验证时机选择
        if (showLockModeDialog) {
            AlertDialog(
                onDismissRequest = { showLockModeDialog = false },
                title = { Text(stringResource(R.string.settings_lock_mode)) },
                text = {
                    Column {
                        listOf(
                            AppLockPreferences.MODE_EVERY_APP to R.string.settings_lock_mode_every_app_full,
                            AppLockPreferences.MODE_EVERY_DIARY to R.string.settings_lock_mode_every_diary,
                            AppLockPreferences.MODE_DAILY to R.string.settings_lock_mode_daily
                        ).forEach { (mode, descRes) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch { appLockPreferences.setLockMode(mode) }
                                        showLockModeDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = lockMode == mode,
                                    onClick = {
                                        scope.launch { appLockPreferences.setLockMode(mode) }
                                        showLockModeDialog = false
                                    }
                                )
                                Text(stringResource(descRes), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showLockModeDialog = false }) { Text(stringResource(R.string.common_done)) }
                }
            )
        }

        // 全屏设置/修改密码（玻璃数字键盘）：首设 SetPin，修改 Change（先验旧密码）
        if (showLockSetup) {
            AppLockScreen(
                mode = if (hasLockPassword) LockMode.Change else LockMode.SetPin,
                lockPreferences = appLockPreferences,
                title = if (hasLockPassword) stringResource(R.string.settings_change_pin) else stringResource(R.string.settings_set_pin),
                pinLength = lockPinLength,
                onVerified = {},
                onPinSet = { pin ->
                    scope.launch {
                        appLockPreferences.setPassword(pin)
                        if (!lockEnabled) appLockPreferences.setLockEnabled(true)
                    }
                    showLockSetup = false
                    showToast(
                        context,
                        if (hasLockPassword) context.getString(R.string.settings_pin_changed)
                        else context.getString(R.string.settings_lock_enabled)
                    )
                },
                onCancel = { showLockSetup = false }
            )
        }

        // 关闭密码锁前先验证（防绕过日记锁直接进设置关锁）
        if (showLockVerify) {
            AppLockScreen(
                mode = LockMode.Unlock,
                lockPreferences = appLockPreferences,
                title = stringResource(R.string.settings_lock_verify_title),
                pinLength = lockPinLength,
                onVerified = {
                    scope.launch { appLockPreferences.setLockEnabled(false) }
                    showLockVerify = false
                    showToast(context, context.getString(R.string.settings_lock_disabled))
                },
                onPinSet = {},
                onCancel = { showLockVerify = false }
            )
        }
    }
}

private fun showToast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
}

@Composable
private fun SettingsRow(
    modifier: Modifier = Modifier,
    headline: String,
    supporting: String? = null,
    leading: @Composable () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(Spacing.l))
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.titleMedium)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.l))
            trailing()
        }
    }
}
