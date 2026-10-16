package com.example.diary.ui.todo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.R
import com.example.diary.data.focus.FocusNotifier
import com.example.diary.data.focus.FocusRecord
import com.example.diary.data.focus.FocusRuntime
import com.example.diary.data.focus.FocusService
import com.example.diary.data.focus.FocusTimer
import com.example.diary.data.local.FocusSession
import com.example.diary.data.local.FocusTask
import com.example.diary.data.local.TodoItem
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.repository.FocusRepository
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoAlarmNotifier
import com.example.diary.data.todo.TodoNotificationHelper
import com.example.diary.ui.components.SwipeDeleteCard
import com.example.diary.ui.focus.FocusTaskCard
import com.example.diary.ui.focus.FocusTaskEditSheet
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.navigation.GlassFab
import com.example.diary.ui.navigation.glassStroke
import com.example.diary.ui.navigation.glassTint
import com.example.diary.ui.navigation.rememberGlassBackdrop
import com.example.diary.ui.theme.Spacing
import com.example.diary.util.DateUtils
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.launch
import android.os.SystemClock

/**
 * 待办列表 - 贴合图一浅色整体 + 图三已完成折叠
 * 主题跟随 MaterialTheme（亮/暗自动），不再硬编码黑底/黄FAB
 *
 * 学习模式（设置页开关）打开时，本 Tab 换成专注形态：顶部番茄钟卡片 + 专注任务列表，
 * 原来的普通待办收进默认折叠的分区。折叠只影响渲染 —— observeAll() 照常订阅，
 * 所以被折叠的待办到点提醒照旧由 AlarmManager 弹出，不受任何影响。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    repository: TodoRepository,
    focusRepository: FocusRepository,
    focusPreferences: FocusPreferences,
    onOpenFocus: (Long?) -> Unit = {},
    onOpenFocusStats: () -> Unit = {},
) {
    // remember: 避免每次重组新建 Flow → collectAsState 取消重订阅
    val allItems by remember { repository.observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val studyMode by focusPreferences.studyMode.collectAsStateWithLifecycle(initialValue = false)
    val active = remember(allItems) { allItems.filter { !it.done } }
    val completed = remember(allItems) { allItems.filter { it.done } }
    var completedCollapsed by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }
    var showTaskSheet by remember { mutableStateOf(false) }
    var editingFocusTask by remember { mutableStateOf<FocusTask?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        TodoNotificationHelper.ensureChannel(context)
        TodoAlarmNotifier.ensureChannel(context)
        FocusNotifier.ensureChannel(context)
    }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // 设置页深链：按序尝试（缺 extra 秒关 / OEM 不识别该 action 时降级到应用信息页）
    fun openSettingsSafely(vararg intents: Intent) {
        for (base in intents) {
            val i = Intent(base).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(i)
                return
            } catch (_: android.content.ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    fun appDetailsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    // 保存带提醒的待办时申请通知权限；被拒不阻塞保存（闹钟照排），仅提示
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    context.getString(R.string.todo_perm_notification),
                    actionLabel = context.getString(R.string.snack_go_settings),
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    openSettingsSafely(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                        appDetailsIntent()
                    )
                }
            }
        }
    }

    fun ensureNotificationPermissionForReminder() {
        if (!hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // S+ 精确闹钟被系统/用户关闭时降级为 inexact，需告知 + 深链设置
    fun ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(android.app.AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        context.getString(R.string.todo_perm_exact),
                        actionLabel = context.getString(R.string.snack_go_settings),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        openSettingsSafely(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            appDetailsIntent()
                        )
                    }
                }
            }
        }
    }

    // 14+ 全屏 Intent 被禁时闹钟无法锁屏拉起 → 降级为响一次通知，需告知 + 深链
    fun ensureFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        context.getString(R.string.todo_perm_fullscreen),
                        actionLabel = context.getString(R.string.snack_go_settings),
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        openSettingsSafely(
                            // EXTRA_APP_PACKAGE 必带：缺了 Settings 秒开秒关（表现为点了没反应）
                            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            appDetailsIntent()
                        )
                    }
                }
            }
        }
    }

    // MIUI/HyperOS 自启动默认拒绝，拒绝时闹钟广播唤不起 App（appop 10008 无公开 API，反射检测）
    fun miuiAutoStartAllowed(): Boolean? = try {
        val am = context.getSystemService(android.app.AppOpsManager::class.java)
        val m = android.app.AppOpsManager::class.java.getMethod(
            "checkOpNoThrow", Integer.TYPE, Integer.TYPE, String::class.java
        )
        when (m.invoke(am, 10008, android.os.Process.myUid(), context.packageName) as Int) {
            android.app.AppOpsManager.MODE_ALLOWED -> true
            android.app.AppOpsManager.MODE_IGNORED, android.app.AppOpsManager.MODE_ERRORED -> false
            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    fun ensureMiuiAutoStart() {
        if (!Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)) return
        if (miuiAutoStartAllowed() != false) return
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                context.getString(R.string.todo_perm_autostart),
                actionLabel = context.getString(R.string.snack_go_settings),
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                openSettingsSafely(
                    Intent().setComponent(
                        android.content.ComponentName(
                            "com.miui.securitycenter",
                            "com.miui.permcenter.autostart.AutoStartManagementActivity"
                        )
                    ),
                    appDetailsIntent()
                )
            }
        }
    }

    val listBackdrop = rememberGlassBackdrop()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState, Modifier.padding(bottom = BottomBarContentInset)) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.todo_title), fontWeight = FontWeight.Bold) },
                actions = {
                    if (studyMode) {
                        IconButton(onClick = onOpenFocusStats) {
                            Icon(
                                Icons.AutoMirrored.Filled.ShowChart,
                                stringResource(R.string.focus_stats_entry)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            GlassFab(
                onClick = {
                    if (studyMode) {
                        editingFocusTask = null
                        showTaskSheet = true
                    } else {
                        editingItem = null
                        showEditSheet = true
                    }
                },
                backdrop = listBackdrop,
                modifier = Modifier.padding(bottom = BottomBarContentInset)
            ) {
                Icon(
                    Icons.Filled.Add,
                    stringResource(if (studyMode) R.string.focus_task_fab else R.string.todo_fab),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        if (studyMode) {
            StudyTodoTab(
                repository = repository,
                focusRepository = focusRepository,
                allItems = allItems,
                padding = padding,
                listBackdrop = listBackdrop,
                onOpenFocus = onOpenFocus,
                onEditTask = { task -> editingFocusTask = task; showTaskSheet = true },
                onStartFocus = { task ->
                    // 先同步发布初始记录再拉服务：否则跳到番茄钟页时
                    // 运行态还没到，会闪一下 00:00 且「开始」是禁用的
                    val existing = FocusRuntime.current()
                    if (existing?.taskId != task.id || !existing.running) {
                        FocusRuntime.publish(
                            existing?.copy(taskId = task.id, taskTitle = task.title)
                                ?: FocusRecord(taskId = task.id, taskTitle = task.title)
                        )
                    }
                    val intent = Intent(context, FocusService::class.java).apply {
                        action = FocusService.ACTION_START
                        putExtra(FocusService.EXTRA_TASK_ID, task.id)
                        putExtra(FocusService.EXTRA_TASK_TITLE, task.title)
                    }
                    runCatching { context.startForegroundService(intent) }
                    onOpenFocus(task.id)
                },
                onArchiveTask = { task -> scope.launch { focusRepository.archiveTask(task.id) } }
            )
        } else if (active.isEmpty() && completed.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).layerBackdrop(listBackdrop), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(Spacing.l))
                    Text(stringResource(R.string.todo_empty_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.todo_empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).layerBackdrop(listBackdrop),
            contentPadding = PaddingValues(start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = BottomBarContentInset),
            verticalArrangement = Arrangement.spacedBy(Spacing.s)
        ) {
            items(active, key = { it.id }) { item ->
                TodoCard(
                    item = item,
                    isCompleted = false,
                    onToggle = {
                        scope.launch { repository.save(item.copy(done = true)) }
                    },
                    onClick = { editingItem = item; showEditSheet = true },
                    onDelete = {
                        scope.launch { repository.delete(item.id) }
                    }
                )
            }

            if (completed.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { completedCollapsed = !completedCollapsed }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (completedCollapsed) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                            contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.todo_completed, completed.size), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (!completedCollapsed) {
                        items(completed, key = { it.id }) { item ->
                            TodoCard(
                                item = item,
                                isCompleted = true,
                                onToggle = {
                                    // save() 内自动按 reminderAt 重排闹钟
                                    scope.launch { repository.save(item.copy(done = false)) }
                                },
                                onClick = { editingItem = item; showEditSheet = true },
                                onDelete = {
                                    scope.launch { repository.delete(item.id) }
                                }
                            )
                        }
                }
            }
        }
        }
    }

    if (showEditSheet) {
        TodoEditSheet(
            existingItem = editingItem,
            onSave = { text, done, reminderAt, repeatRule, alarmMode ->
                scope.launch {
                    val base = editingItem
                    val toSave = if (base == null) {
                        TodoItem(text = text, done = done, sortOrder = active.size, reminderAt = reminderAt, repeatRule = repeatRule, alarmMode = alarmMode)
                    } else {
                        base.copy(text = text, done = done, reminderAt = reminderAt, repeatRule = repeatRule, alarmMode = alarmMode)
                    }
                    repository.save(toSave)
                    if (toSave.reminderAt != null && !toSave.done) {
                        TodoNotificationHelper.ensureChannel(context)
                        TodoAlarmNotifier.ensureChannel(context)
                        ensureNotificationPermissionForReminder()
                        ensureExactAlarmPermission()
                        if (toSave.alarmMode == TodoItem.MODE_RING) {
                            ensureMiuiAutoStart()
                            ensureFullScreenIntentPermission()
                        }
                    }
                    showEditSheet = false
                }
            },
            onDismiss = { showEditSheet = false }
        )
    }

    if (showTaskSheet) {
        FocusTaskEditSheet(
            existingTask = editingFocusTask,
            onSave = { title ->
                scope.launch {
                    val base = editingFocusTask
                    if (base == null) {
                        focusRepository.addTask(title, 0)
                    } else {
                        focusRepository.saveTask(base.copy(title = title))
                    }
                    showTaskSheet = false
                }
            },
            onDismiss = { showTaskSheet = false }
        )
    }
}

@Composable
private fun TodoCard(item: TodoItem, isCompleted: Boolean, onToggle: () -> Unit, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = when {
        !isCompleted -> glassTint(dark)
        dark -> Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.36f), Color.Black.copy(alpha = 0.28f))
        )
        else -> Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.44f), Color.White.copy(alpha = 0.35f))
        )
    }
    SwipeDeleteCard(
        onClick = onClick,
        onDelete = onDelete,
        confirmTitle = stringResource(R.string.todo_delete_title),
        confirmMessage = stringResource(R.string.todo_delete_message, item.text),
        containerColor = Color.Transparent,
        cardModifier = Modifier
            .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
            .background(tint, MaterialTheme.shapes.medium)
            .border(1.dp, glassStroke(dark), MaterialTheme.shapes.medium)
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = item.done, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(Spacing.s))
            Column(Modifier.weight(1f)) {
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (item.reminderAt != null && !isCompleted) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        (if (item.alarmMode == TodoItem.MODE_RING) stringResource(R.string.todo_ring_prefix) else "") +
                            DateUtils.formatReminder(context, item.reminderAt, item.repeatRule),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 学习模式下的待办 Tab：番茄钟卡片 + 专注任务列表 + 折叠的普通待办。
 *
 * 普通待办的 Flow 依然在父 composable 里订阅着，这里只是把渲染折叠起来 ——
 * 所以「看不见」不等于「不提醒」，闹钟照旧由 AlarmManager 触发。
 */
@Composable
private fun StudyTodoTab(
    repository: TodoRepository,
    focusRepository: FocusRepository,
    allItems: List<TodoItem>,
    padding: PaddingValues,
    listBackdrop: LayerBackdrop,
    onOpenFocus: (Long?) -> Unit,
    onEditTask: (FocusTask) -> Unit,
    onStartFocus: (FocusTask) -> Unit,
    onArchiveTask: (FocusTask) -> Unit
) {
    val tasks by remember { focusRepository.observeActiveTasks() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val record by FocusRuntime.record.collectAsStateWithLifecycle()
    var tick by remember { mutableIntStateOf(0) }
    var normalExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(record?.running) {
        while (record?.running == true) {
            kotlinx.coroutines.delay(1000L)
            tick++
        }
    }

    val remaining = record?.let {
        FocusTimer.remainingMs(it.toSession(), remember(tick, record) { SystemClock.elapsedRealtime() }, it.segments())
    } ?: 0L
    val normalActive = remember(allItems) { allItems.filter { !it.done } }
    val normalDone = remember(allItems) { allItems.filter { it.done } }
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .layerBackdrop(listBackdrop),
        contentPadding = PaddingValues(
            start = Spacing.l, end = Spacing.l, top = Spacing.m, bottom = BottomBarContentInset
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        item {
            FocusTimerCard(
                record = record,
                remainingMs = remaining,
                onClick = { onOpenFocus(record?.taskId) }
            )
        }

        item {
            Text(
                stringResource(R.string.focus_task_section),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.s)
            )
        }

        if (tasks.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.focus_task_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = Spacing.m)
                )
            }
        } else {
            items(tasks, key = { it.id }) { task ->
                FocusTaskCard(
                    task = task,
                    onOpen = { onEditTask(task) },
                    onStartFocus = { onStartFocus(task) },
                    onArchive = { onArchiveTask(task) }
                )
            }
        }

        if (allItems.isNotEmpty()) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { normalExpanded = !normalExpanded }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (normalExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.focus_normal_todos, allItems.count { !it.done }),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (normalExpanded) {
                items(normalActive, key = { "n${it.id}" }) { item ->
                    TodoCard(
                        item = item,
                        isCompleted = false,
                        onToggle = { scope.launch { repository.save(item.copy(done = true)) } },
                        onClick = {},
                        onDelete = { scope.launch { repository.delete(item.id) } }
                    )
                }
                if (normalDone.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.todo_completed, normalDone.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = Spacing.s)
                        )
                    }
                    items(normalDone, key = { "d${it.id}" }) { item ->
                        TodoCard(
                            item = item,
                            isCompleted = true,
                            onToggle = { scope.launch { repository.save(item.copy(done = false)) } },
                            onClick = {},
                            onDelete = { scope.launch { repository.delete(item.id) } }
                        )
                    }
                }
            }
        }
    }
}

/** 学习模式顶部的迷你番茄钟卡片：点进番茄钟页 */
@Composable
private fun FocusTimerCard(
    record: FocusRecord?,
    remainingMs: Long,
    onClick: () -> Unit
) {
    val phase = record?.phase
    val accent = when (phase) {
        FocusSession.PHASE_SHORT_BREAK -> MaterialTheme.colorScheme.tertiary
        FocusSession.PHASE_LONG_BREAK -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    val phaseText = stringResource(
        when (phase) {
            FocusSession.PHASE_SHORT_BREAK -> R.string.focus_phase_short_break
            FocusSession.PHASE_LONG_BREAK -> R.string.focus_phase_long_break
            else -> R.string.focus_phase_focus
        }
    )
    val statusText = if (record == null) {
        stringResource(R.string.focus_idle_hint)
    } else if (record.running) {
        stringResource(R.string.focus_status_running, FocusTimer.formatDuration(remainingMs))
    } else {
        stringResource(R.string.focus_status_paused, FocusTimer.formatDuration(remainingMs))
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.padding(Spacing.l),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Timer,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(Spacing.m))
            Column(Modifier.weight(1f)) {
                Text(
                    phaseText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    statusText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val title = record?.taskTitle?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.focus_task_fallback)
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
