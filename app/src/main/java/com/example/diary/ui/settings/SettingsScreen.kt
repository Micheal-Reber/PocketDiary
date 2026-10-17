package com.example.diary.ui.settings

import android.net.Uri
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.BuildConfig
import com.example.diary.R
import com.example.diary.data.backup.BackupRepository
import com.example.diary.data.backup.ImportResult
import com.example.diary.data.calendar.SystemCalendarManager
import com.example.diary.data.calendar.SystemCalendarSync
import com.example.diary.data.focus.CustomNoiseStore
import com.example.diary.data.focus.BackgroundStart
import com.example.diary.data.focus.FocusGuardService
import com.example.diary.data.focus.KeepAlive
import com.example.diary.data.focus.FocusService
import com.example.diary.data.focus.FocusRuntime
import com.example.diary.data.focus.WhiteNoise
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.preferences.AppLockPreferences
import com.example.diary.data.preferences.CalendarPreferences
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
    calendarPreferences: CalendarPreferences,
    countdownRepository: com.example.diary.data.repository.CountdownRepository,
    focusPreferences: com.example.diary.data.preferences.FocusPreferences,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val studyMode by focusPreferences.studyMode.collectAsStateWithLifecycle(initialValue = false)
    val focusMinutes by focusPreferences.focusMinutes.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_FOCUS_MINUTES
    )
    val shortBreakMinutes by focusPreferences.shortBreakMinutes.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_SHORT_BREAK_MINUTES
    )
    val longBreakMinutes by focusPreferences.longBreakMinutes.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_LONG_BREAK_MINUTES
    )
    val longBreakInterval by focusPreferences.longBreakInterval.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_LONG_BREAK_INTERVAL
    )
    val noiseKind by focusPreferences.noiseKind.collectAsStateWithLifecycle(initialValue = WhiteNoise.Kind.OFF.id)
    val noiseVolume by focusPreferences.noiseVolume.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_NOISE_VOLUME
    )
    val guardEnabled by focusPreferences.guardEnabled.collectAsStateWithLifecycle(initialValue = false)
    val guardAllowedPackages by focusPreferences.guardAllowedPackages.collectAsStateWithLifecycle(initialValue = emptySet())
    val guardAllowLauncher by focusPreferences.guardAllowLauncher.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_GUARD_ALLOW_LAUNCHER
    )
    val guardAccessibilityEnabled = remember { mutableStateOf(FocusGuardService.isEnabled(context)) }
    // 小米/红米：后台弹窗权限被关时守卫会被静默吞掉，进本页时刷一次（跳去系统设置授权后要能看到变化）
    val guardBackgroundBlocked = remember { mutableStateOf(false) }
    val guardBlockCount by focusPreferences.guardBlockCount.collectAsStateWithLifecycle(initialValue = 0)
val customNoisePath by focusPreferences.customNoisePath.collectAsStateWithLifecycle(initialValue = null)
    val customNoiseName by focusPreferences.customNoiseName.collectAsStateWithLifecycle(initialValue = null)
    // 系统文件选择器：自定义音频入口；取到 URI 后复制进 filesDir（权限被撤销也不怕）
    val audioPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = CustomNoiseStore(context).import(uri, null)
                if (ok) {
                    focusPreferences.setNoiseKind(WhiteNoise.Kind.CUSTOM.id)
                } else {
                    showToast(context, context.getString(R.string.focus_noise_pick_failed))
                }
            }
        }
    }
val notifEnhanced by focusPreferences.notificationEnhanced.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_NOTIFICATION_ENHANCED
    )
    val alarmEnabled by focusPreferences.alarmEnabled.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_ALARM_ENABLED
    )
    val alarmVolume by focusPreferences.alarmVolume.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_ALARM_VOLUME
    )
    val alarmVibrate by focusPreferences.alarmVibrate.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_ALARM_VIBRATE
    )
    val alarmVibrateMs by focusPreferences.alarmVibrateMs.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_ALARM_VIBRATE_MS
    )
    val alarmVibrateGapMs by focusPreferences.alarmVibrateGapMs.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_ALARM_VIBRATE_GAP_MS
    )
    val alarmAmplitude by focusPreferences.alarmAmplitude.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_ALARM_AMPLITUDE
    )
    val alarmScreenOn by focusPreferences.alarmScreenOn.collectAsStateWithLifecycle(
        initialValue = com.example.diary.data.preferences.FocusPreferences.DEFAULT_ALARM_SCREEN_ON
    )
    // 保活与精确闹钟状态要从系统里读，进本页（尤其刚从系统设置跳回来）刷一次
    val keepAliveOk = remember { mutableStateOf(true) }
    val exactAlarmOk = remember { mutableStateOf(true) }
    var showAlarmDialog by remember { mutableStateOf(false) }
    var showQuitStudyModeDialog by remember { mutableStateOf(false) }
    var showFocusSettingsDialog by remember { mutableStateOf(false) }
    var showNoiseDialog by remember { mutableStateOf(false) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showGuardPermissions by remember { mutableStateOf(false) }

    // 无障碍开关状态要从系统设置里读，进本页时（尤其刚从系统设置跳回来）刷一次
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                guardAccessibilityEnabled.value = FocusGuardService.isEnabled(context)
                guardBackgroundBlocked.value = !BackgroundStart.canStartActivityFromBackground(context)
                keepAliveOk.value = KeepAlive.isIgnoringBatteryOptimizations(context)
                exactAlarmOk.value = canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val dynamicColor by themePreferences.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    val floatingBar by themePreferences.floatingBar.collectAsStateWithLifecycle(initialValue = true)
    val calendarSync by calendarPreferences.syncToSystemCalendar.collectAsStateWithLifecycle(initialValue = false)
    // 开关先开、权限后授权的场景：靠这个标记在权限回调里补同步
    var pendingCalendarSync by remember { mutableStateOf(false) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        // 授权回调：拿到权限就立刻补全量对账，否则用户看不到任何反馈
        val ok = grants[android.Manifest.permission.READ_CALENDAR] == true &&
            grants[android.Manifest.permission.WRITE_CALENDAR] == true
        if (ok) {
            pendingCalendarSync = false
            SystemCalendarSync.syncNow(context, countdownRepository)
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.settings_calendar_perm_denied)
                )
            }
        }
    }
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
    var todoExpanded by remember { mutableStateOf(false) }
    var privacyExpanded by remember { mutableStateOf(false) }
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
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

            SettingsRow(
                headline = stringResource(R.string.settings_calendar_sync),
                supporting = if (calendarSync) stringResource(R.string.settings_calendar_sync_on)
                else stringResource(R.string.settings_calendar_sync_off),
                leading = { Icon(Icons.Default.Schedule, contentDescription = null) },
                trailing = {
                    Switch(
                        checked = calendarSync,
                        onCheckedChange = { checked ->
                            if (checked) {
                                // 无论当前是否已授权都先存开关：
                                // 授权走异步弹窗，结果回来后再补一次全量对账，
                                // 否则"先开开关后授权"的组合会让已存在的倒数日永远不同步。
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

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Todo / 学习模式（番茄钟）—— 与数据迁移一致，默认折叠
            CollapsibleSectionHeader(
                title = stringResource(R.string.settings_section_todo),
                expanded = todoExpanded,
                onToggle = { todoExpanded = !todoExpanded }
            )

            if (todoExpanded) {
            SettingsRow(
                headline = stringResource(R.string.settings_todo_study_mode),
                supporting = if (studyMode) stringResource(R.string.settings_todo_study_mode_on)
                else stringResource(R.string.settings_todo_study_mode_off),
                leading = { Icon(Icons.Default.Timer, contentDescription = null) },
                trailing = {
                    Switch(
                        checked = studyMode,
                        onCheckedChange = { checked ->
                            // 正在计时时关开关会让前台服务和到点闹钟失去归属，先确认
                            if (!checked && FocusRuntime.isRunning) {
                                showQuitStudyModeDialog = true
                            } else {
                                scope.launch { focusPreferences.setStudyMode(checked) }
                            }
                        }
                    )
                }
            )

            if (studyMode) {
                SettingsRow(
                    headline = stringResource(R.string.settings_focus_durations),
                    supporting = stringResource(
                        R.string.settings_focus_durations_value,
                        focusMinutes, shortBreakMinutes, longBreakMinutes, longBreakInterval
                    ),
                    leading = { Icon(Icons.Default.Schedule, contentDescription = null) },
                    modifier = Modifier.clickable { showFocusSettingsDialog = true }
                )

                SettingsRow(
                    headline = stringResource(R.string.settings_focus_noise),
                    supporting = stringResource(
                        R.string.settings_focus_noise_value,
                        stringResource(noiseKindLabel(WhiteNoise.Kind.from(noiseKind))),
                        noiseVolume
                    ),
                    leading = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    modifier = Modifier.clickable { showNoiseDialog = true }
                )

                SettingsRow(
                    headline = stringResource(R.string.settings_focus_notif_enhanced),
                    supporting = stringResource(R.string.settings_focus_notif_enhanced_desc),
                    leading = { Icon(Icons.Default.NotificationsActive, contentDescription = null) },
                    trailing = {
                        Switch(
                            checked = notifEnhanced,
                            onCheckedChange = { checked ->
                                scope.launch {
                                    focusPreferences.setNotificationEnhanced(checked)
                                    // 立刻让常驻通知重画，不然要等下一次 tick
                                    context.startService(
                                        android.content.Intent(context, FocusService::class.java).apply {
                                            action = FocusService.ACTION_NOISE
                                        }
                                    )
                                }
                            }
                        )
                    }
                )

                SettingsRow(
                    headline = stringResource(R.string.settings_focus_alarm),
                    supporting = stringResource(
                        R.string.settings_focus_alarm_value,
                        if (alarmEnabled) alarmVolume else 0
                    ),
                    leading = { Icon(Icons.Default.Alarm, contentDescription = null) },
                    modifier = Modifier.clickable { showAlarmDialog = true }
                )

                if (!exactAlarmOk.value) {
                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_exact_alarm),
                        supporting = stringResource(R.string.settings_focus_exact_alarm_desc),
                        leading = { Icon(Icons.Default.Warning, contentDescription = null) },
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            runCatching { context.startActivity(intent) }
                        }
                    )
                }

                SettingsRow(
                    headline = stringResource(R.string.settings_focus_keep_alive),
                    supporting = if (keepAliveOk.value) {
                        stringResource(R.string.settings_focus_keep_alive_ok)
                    } else {
                        stringResource(R.string.settings_focus_keep_alive_desc)
                    },
                    leading = { Icon(Icons.Default.BatteryAlert, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val opened = if (keepAliveOk.value) {
                            KeepAlive.needsAutostartHint() && KeepAlive.openAutostartSettings(context)
                        } else {
                            KeepAlive.openIgnoreBatteryOptimizations(context)
                        }
                        if (!opened) {
                            showToast(
                                context,
                                context.getString(R.string.settings_focus_guard_perm_open_failed)
                            )
                        }
                    }
                )

                SettingsRow(
                    headline = stringResource(R.string.settings_focus_guard),
                    supporting = when {
                        !guardEnabled -> stringResource(R.string.settings_focus_guard_off)
                        !guardAccessibilityEnabled.value -> stringResource(R.string.settings_focus_guard_need_a11y)
                        else -> stringResource(R.string.settings_focus_guard_on, guardBlockCount)
                    },
                    leading = { Icon(Icons.Default.Shield, contentDescription = null) },
                    trailing = {
                        Switch(
                            checked = guardEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    // 先过权限清单：无障碍没开就不让真正生效（ZenLock 的 PermissionGate 做法）
                                    showGuardPermissions = true
                                } else {
                                    scope.launch { focusPreferences.setGuardEnabled(false) }
                                }
                            }
                        )
                    },
                    modifier = Modifier.clickable {
                        // 开关已开但系统没授权，点整行直接带去无障碍设置
                        if (guardEnabled && !guardAccessibilityEnabled.value) {
                            val opened = openSystemPage(
                                context,
                                listOf(FocusGuardService.accessibilitySettingsIntent(context))
                            )
                            if (!opened) {
                                showToast(context, context.getString(R.string.settings_focus_guard_perm_open_failed))
                            }
                        }
                    }
                )

                if (guardEnabled && guardBackgroundBlocked.value) {
                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_guard_need_background),
                        supporting = stringResource(R.string.settings_focus_guard_need_background_hint),
                        leading = { Icon(Icons.Default.Warning, contentDescription = null) },
                        modifier = Modifier.clickable {
                            runCatching { context.startActivity(BackgroundStart.permissionEditorIntent(context)) }
                        }
                    )
                }

                if (guardEnabled) {
                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_guard_allow_launcher),
                        supporting = stringResource(R.string.settings_focus_guard_allow_launcher_value),
                        leading = { Icon(Icons.Default.Home, contentDescription = null) },
                        trailing = {
                            Switch(
                                checked = guardAllowLauncher,
                                onCheckedChange = { checked ->
                                    scope.launch { focusPreferences.setGuardAllowLauncher(checked) }
                                }
                            )
                        }
                    )

                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_guard_allowed),
                        supporting = stringResource(
                            R.string.settings_focus_guard_allowed_value,
                            guardAllowedPackages.size
                        ),
                        leading = { Icon(Icons.Default.Apps, contentDescription = null) },
                        modifier = Modifier.clickable { showWhitelistDialog = true }
                    )
                }
            }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Privacy section（默认折叠）
            CollapsibleSectionHeader(
                title = stringResource(R.string.settings_section_privacy),
                expanded = privacyExpanded,
                onToggle = { privacyExpanded = !privacyExpanded }
            )

            if (privacyExpanded) {
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
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Data Migration section（默认折叠，点头部展开）
            CollapsibleSectionHeader(
                title = stringResource(R.string.settings_migration),
                expanded = backupExpanded,
                onToggle = { backupExpanded = !backupExpanded }
            )

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

        // 正在计时时关闭学习模式：先确认，避免留一个没有归属的前台服务
        if (showQuitStudyModeDialog) {
            AlertDialog(
                onDismissRequest = { showQuitStudyModeDialog = false },
                title = { Text(stringResource(R.string.focus_quit_dialog_title)) },
                text = { Text(stringResource(R.string.focus_quit_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showQuitStudyModeDialog = false
                        scope.launch {
                            val intent = android.content.Intent(context, com.example.diary.data.focus.FocusService::class.java).apply {
                                action = com.example.diary.data.focus.FocusService.ACTION_STOP
                            }
                            runCatching { context.startService(intent) }
                            focusPreferences.setStudyMode(false)
                        }
                    }) {
                        Text(
                            stringResource(R.string.focus_quit_dialog_confirm),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showQuitStudyModeDialog = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            )
        }

        // 番茄钟段长设置
        if (showFocusSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showFocusSettingsDialog = false },
                title = { Text(stringResource(R.string.settings_focus_durations)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        FocusDurationRow(
                            stringResource(R.string.settings_focus_minutes), focusMinutes, 1..180
                        ) { scope.launch { focusPreferences.setFocusMinutes(it) } }
                        FocusDurationRow(
                            stringResource(R.string.settings_focus_short_break), shortBreakMinutes, 1..60
                        ) { scope.launch { focusPreferences.setShortBreakMinutes(it) } }
                        FocusDurationRow(
                            stringResource(R.string.settings_focus_long_break), longBreakMinutes, 1..90
                        ) { scope.launch { focusPreferences.setLongBreakMinutes(it) } }
                        FocusDurationRow(
                            stringResource(R.string.settings_focus_interval), longBreakInterval, 2..12
                        ) { scope.launch { focusPreferences.setLongBreakInterval(it) } }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFocusSettingsDialog = false }) {
                        Text(stringResource(R.string.common_done))
                    }
                }
            )
        }

        // 专注白噪音：音源 + 音量
        if (showAlarmDialog) {
            AlertDialog(
                onDismissRequest = { showAlarmDialog = false },
                title = { Text(stringResource(R.string.settings_focus_alarm)) },
                text = {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.focus_alarm_enabled),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = alarmEnabled,
                                onCheckedChange = { checked ->
                                    scope.launch { focusPreferences.setAlarmEnabled(checked) }
                                }
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.focus_alarm_vibrate),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = alarmVibrate,
                                onCheckedChange = { checked ->
                                    scope.launch { focusPreferences.setAlarmVibrate(checked) }
                                }
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.focus_alarm_screen_on),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = alarmScreenOn,
                                onCheckedChange = { checked ->
                                    scope.launch { focusPreferences.setAlarmScreenOn(checked) }
                                }
                            )
                        }

                        Text(
                            stringResource(R.string.focus_alarm_volume, alarmVolume),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = alarmVolume.toFloat(),
                            onValueChange = { scope.launch { focusPreferences.setAlarmVolume(it.toInt()) } },
                            valueRange = 0f..100f
                        )

                        if (alarmVibrate) {
                            Text(
                                stringResource(R.string.focus_alarm_vibrate_on, alarmVibrateMs),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = alarmVibrateMs.toFloat(),
                                onValueChange = {
                                    scope.launch { focusPreferences.setAlarmVibrateMs(it.toInt()) }
                                },
                                valueRange = 50f..2000f
                            )
                            Text(
                                stringResource(R.string.focus_alarm_vibrate_gap, alarmVibrateGapMs),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = alarmVibrateGapMs.toFloat(),
                                onValueChange = {
                                    scope.launch { focusPreferences.setAlarmVibrateGapMs(it.toInt()) }
                                },
                                valueRange = 0f..2000f
                            )
                            Text(
                                stringResource(R.string.focus_alarm_amplitude, alarmAmplitude),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = alarmAmplitude.toFloat(),
                                onValueChange = {
                                    scope.launch { focusPreferences.setAlarmAmplitude(it.toInt()) }
                                },
                                valueRange = 0f..255f
                            )
                        }

                        Text(
                            stringResource(R.string.focus_alarm_footnote),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAlarmDialog = false }) {
                        Text(stringResource(R.string.common_done))
                    }
                }
            )
        }

        if (showNoiseDialog) {
            AlertDialog(
                onDismissRequest = { showNoiseDialog = false },
                title = { Text(stringResource(R.string.settings_focus_noise)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            listOf(
                                WhiteNoise.Kind.OFF,
                                WhiteNoise.Kind.WHITE,
                                WhiteNoise.Kind.CUSTOM
                            ).forEach { kind ->
                                FilterChip(
                                    selected = WhiteNoise.Kind.from(noiseKind) == kind,
                                    onClick = { scope.launch { focusPreferences.setNoiseKind(kind.id) } },
                                    border = null,
                                    label = {
                                        Text(
                                            stringResource(noiseKindLabel(kind)),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                        if (WhiteNoise.Kind.from(noiseKind) == WhiteNoise.Kind.CUSTOM) {
                            Text(
                                customNoiseName ?: stringResource(R.string.focus_noise_none_set),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                                TextButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                                    Text(stringResource(R.string.focus_noise_pick))
                                }
                                if (customNoisePath != null) {
                                    TextButton(onClick = {
                                        scope.launch {
                                            CustomNoiseStore(context).clear()
                                            focusPreferences.setNoiseKind(WhiteNoise.Kind.OFF.id)
                                        }
                                    }) {
                                        Text(stringResource(R.string.common_clear))
                                    }
                                }
                            }
                        }
                        if (WhiteNoise.Kind.from(noiseKind) != WhiteNoise.Kind.OFF) {
                            Text(
                                stringResource(R.string.focus_noise_volume, noiseVolume),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Slider(
                                value = noiseVolume.toFloat(),
                                onValueChange = { scope.launch { focusPreferences.setNoiseVolume(it.toInt()) } },
                                valueRange = 0f..100f
                            )
                        }
                        Text(
                            stringResource(R.string.focus_noise_footnote),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNoiseDialog = false }) {
                        Text(stringResource(R.string.common_done))
                    }
                }
            )
        }

        // 专注期间允许使用的应用
        if (showWhitelistDialog) {
            FocusAllowedAppsDialog(
                selected = guardAllowedPackages,
                onToggle = { pkg ->
                    val next = if (pkg in guardAllowedPackages) guardAllowedPackages - pkg else guardAllowedPackages + pkg
                    scope.launch { focusPreferences.setGuardAllowedPackages(next) }
                },
                onDismiss = { showWhitelistDialog = false }
            )
        }

        // 开启专注锁定前的权限清单
        if (showGuardPermissions) {
            FocusGuardPermissionSheet(
                onDismiss = { showGuardPermissions = false },
                onContinue = {
                    showGuardPermissions = false
                    scope.launch { focusPreferences.setGuardEnabled(true) }
                }
            )
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

/** 折叠分组头部：点整行切换，右侧箭头指示状态（待办 / 数据迁移共用） */
@Composable
private fun CollapsibleSectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) {
                stringResource(R.string.settings_collapse)
            } else {
                stringResource(R.string.settings_expand)
            },
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/** 能不能排精确闹钟；拿不到精确闹钟时到点提醒会在 Doze 里被攒到最多 ~9 分钟才触发 */
private fun canScheduleExactAlarms(context: android.content.Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return true
    val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return true
    return runCatching { am.canScheduleExactAlarms() }.getOrDefault(true)
}

private fun showToast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
}

@androidx.annotation.StringRes
private fun noiseKindLabel(kind: WhiteNoise.Kind): Int = when (kind) {
    WhiteNoise.Kind.OFF -> R.string.focus_noise_off
    WhiteNoise.Kind.WHITE -> R.string.focus_noise_white
    WhiteNoise.Kind.CUSTOM -> R.string.focus_noise_custom
}

/** 段长微调行：− / 数值 / +，范围外按钮置灰 */
@Composable
private fun FocusDurationRow(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { if (value > range.first) onChange(value - 1) },
            enabled = value > range.first
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 36.dp)
        )
        IconButton(
            onClick = { if (value < range.last) onChange(value + 1) },
            enabled = value < range.last
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
        }
    }
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
