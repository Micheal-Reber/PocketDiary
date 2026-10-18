package com.example.diary.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.example.diary.R
import com.example.diary.data.focus.CustomNoiseStore
import com.example.diary.data.focus.FocusRuntime
import com.example.diary.data.focus.FocusService
import com.example.diary.data.focus.WhiteNoise
import com.example.diary.data.focus.BackgroundStart
import com.example.diary.data.focus.FocusGuardService
import com.example.diary.data.focus.KeepAlive
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.launch

@Composable
fun FocusSettingsScreen(
    focusPreferences: FocusPreferences,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val studyMode by focusPreferences.studyMode.collectAsStateWithLifecycle(initialValue = false)
    val focusMinutes by focusPreferences.focusMinutes.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_FOCUS_MINUTES
    )
    val shortBreakMinutes by focusPreferences.shortBreakMinutes.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_SHORT_BREAK_MINUTES
    )
    val longBreakMinutes by focusPreferences.longBreakMinutes.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_LONG_BREAK_MINUTES
    )
    val longBreakInterval by focusPreferences.longBreakInterval.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_LONG_BREAK_INTERVAL
    )
    val noiseKind by focusPreferences.noiseKind.collectAsStateWithLifecycle(initialValue = WhiteNoise.Kind.OFF.id)
    val noiseVolume by focusPreferences.noiseVolume.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_NOISE_VOLUME
    )
    val guardEnabled by focusPreferences.guardEnabled.collectAsStateWithLifecycle(initialValue = false)
    val guardAllowedPackages by focusPreferences.guardAllowedPackages.collectAsStateWithLifecycle(initialValue = emptySet())
    val guardAllowLauncher by focusPreferences.guardAllowLauncher.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_GUARD_ALLOW_LAUNCHER
    )
    val guardAccessibilityEnabled = remember { mutableStateOf(FocusGuardService.isEnabled(context)) }
    // 小米/红米没有「后台弹出界面」权限时系统会静默拦截拉回，先授权再打开页面时也会因授权弹窗态漏刷一次
    val guardBackgroundBlocked = remember { mutableStateOf(false) }
    val guardBlockCount by focusPreferences.guardBlockCount.collectAsStateWithLifecycle(initialValue = 0)
    val customNoisePath by focusPreferences.customNoisePath.collectAsStateWithLifecycle(initialValue = null)
    val customNoiseName by focusPreferences.customNoiseName.collectAsStateWithLifecycle(initialValue = null)
    val notifEnhanced by focusPreferences.notificationEnhanced.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_NOTIFICATION_ENHANCED
    )
    val alarmEnabled by focusPreferences.alarmEnabled.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_ALARM_ENABLED
    )
    val alarmVolume by focusPreferences.alarmVolume.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_ALARM_VOLUME
    )
    val alarmVibrate by focusPreferences.alarmVibrate.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_ALARM_VIBRATE
    )
    val alarmVibrateMs by focusPreferences.alarmVibrateMs.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_ALARM_VIBRATE_MS
    )
    val alarmVibrateGapMs by focusPreferences.alarmVibrateGapMs.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_ALARM_VIBRATE_GAP_MS
    )
    val alarmAmplitude by focusPreferences.alarmAmplitude.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_ALARM_AMPLITUDE
    )
    val alarmScreenOn by focusPreferences.alarmScreenOn.collectAsStateWithLifecycle(
        initialValue = FocusPreferences.DEFAULT_ALARM_SCREEN_ON
    )
    // 到点提醒与精确闹钟状态要问系统：去授权页再回来时刷新；本应用没权限也影响不大
    val keepAliveOk = remember { mutableStateOf(true) }
    val exactAlarmOk = remember { mutableStateOf(true) }

    var showAlarmDialog by remember { mutableStateOf(false) }
    var showQuitStudyModeDialog by remember { mutableStateOf(false) }
    var showFocusSettingsDialog by remember { mutableStateOf(false) }
    var showNoiseDialog by remember { mutableStateOf(false) }
    var showWhitelistDialog by remember { mutableStateOf(false) }
    var showGuardPermissions by remember { mutableStateOf(false) }

    // 自定义音频选到 URI 后复制进应用目录，权限失效也能播（拷贝只需授予 URI，不必持久权限）
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
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

    // 权限类行的实时状态：去系统设置页授权后再回来要刷新
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                guardAccessibilityEnabled.value = FocusGuardService.isEnabled(context)
                guardBackgroundBlocked.value = !BackgroundStart.canStartActivityFromBackground(context)
                keepAliveOk.value = KeepAlive.isIgnoringBatteryOptimizations(context)
                exactAlarmOk.value = canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_section_todo),
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroupCard {
                SettingsRow(
                    headline = stringResource(R.string.settings_todo_study_mode),
                    supporting = if (studyMode) stringResource(R.string.settings_todo_study_mode_on)
                    else stringResource(R.string.settings_todo_study_mode_off),
                    leading = {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
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
            }

            if (studyMode) {
                Spacer(Modifier.height(Spacing.m))

                SettingsGroupCard {
                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_durations),
                        supporting = stringResource(
                            R.string.settings_focus_durations_value,
                            focusMinutes, shortBreakMinutes, longBreakMinutes, longBreakInterval
                        ),
                        leading = {
                            Icon(
                                Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { showFocusSettingsDialog = true }
                    )

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_noise),
                        supporting = stringResource(
                            R.string.settings_focus_noise_value,
                            stringResource(noiseKindLabel(WhiteNoise.Kind.from(noiseKind))),
                            noiseVolume
                        ),
                        leading = {
                            Icon(
                                Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { showNoiseDialog = true }
                    )

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_notif_enhanced),
                        supporting = stringResource(R.string.settings_focus_notif_enhanced_desc),
                        leading = {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
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

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_alarm),
                        supporting = stringResource(
                            R.string.settings_focus_alarm_value,
                            if (alarmEnabled) alarmVolume else 0
                        ),
                        leading = {
                            Icon(
                                Icons.Default.Alarm,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.clickable { showAlarmDialog = true }
                    )

                    if (!exactAlarmOk.value) {
                        GroupDivider()

                        SettingsRow(
                            headline = stringResource(R.string.settings_focus_exact_alarm),
                            supporting = stringResource(R.string.settings_focus_exact_alarm_desc),
                            leading = {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable {
                                val intent = android.content.Intent(
                                    android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    android.net.Uri.parse("package:${context.packageName}")
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(intent) }
                            }
                        )
                    }

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_keep_alive),
                        supporting = if (keepAliveOk.value) {
                            stringResource(R.string.settings_focus_keep_alive_ok)
                        } else {
                            stringResource(R.string.settings_focus_keep_alive_desc)
                        },
                        leading = {
                            Icon(
                                Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
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

                    GroupDivider()

                    SettingsRow(
                        headline = stringResource(R.string.settings_focus_guard),
                        supporting = when {
                            !guardEnabled -> stringResource(R.string.settings_focus_guard_off)
                            !guardAccessibilityEnabled.value -> stringResource(R.string.settings_focus_guard_need_a11y)
                            else -> stringResource(R.string.settings_focus_guard_on, guardBlockCount)
                        },
                        leading = {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
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
                                    showToast(
                                        context,
                                        context.getString(R.string.settings_focus_guard_perm_open_failed)
                                    )
                                }
                            }
                        }
                    )

                    if (guardEnabled && guardBackgroundBlocked.value) {
                        GroupDivider()

                        SettingsRow(
                            headline = stringResource(R.string.settings_focus_guard_need_background),
                            supporting = stringResource(R.string.settings_focus_guard_need_background_hint),
                            leading = {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable {
                                runCatching { context.startActivity(BackgroundStart.permissionEditorIntent(context)) }
                            }
                        )
                    }

                    if (guardEnabled) {
                        GroupDivider()

                        SettingsRow(
                            headline = stringResource(R.string.settings_focus_guard_allow_launcher),
                            supporting = stringResource(R.string.settings_focus_guard_allow_launcher_value),
                            leading = {
                                Icon(
                                    Icons.Default.Home,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailing = {
                                Switch(
                                    checked = guardAllowLauncher,
                                    onCheckedChange = { checked ->
                                        scope.launch { focusPreferences.setGuardAllowLauncher(checked) }
                                    }
                                )
                            }
                        )

                        GroupDivider()

                        SettingsRow(
                            headline = stringResource(R.string.settings_focus_guard_allowed),
                            supporting = stringResource(
                                R.string.settings_focus_guard_allowed_value,
                                guardAllowedPackages.size
                            ),
                            leading = {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            modifier = Modifier.clickable { showWhitelistDialog = true }
                        )
                    }
                }
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
                        val intent = android.content.Intent(context, FocusService::class.java).apply {
                            action = FocusService.ACTION_STOP
                        }
                        runCatching { context.startService(intent) }
                        focusPreferences.setStudyMode(false)
                    }
                }) {
                    Text(
                        stringResource(R.string.focus_quit_dialog_confirm),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
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
                            overflow = TextOverflow.Ellipsis
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
}