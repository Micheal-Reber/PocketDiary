package com.example.diary.ui.todo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.data.local.TodoItem
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoNotificationHelper
import com.example.diary.ui.components.SwipeDeleteCard
import com.example.diary.ui.theme.Spacing
import com.example.diary.util.DateUtils
import kotlinx.coroutines.launch

/**
 * 待办列表 - 贴合图一浅色整体 + 图三已完成折叠
 * 主题跟随 MaterialTheme（亮/暗自动），不再硬编码黑底/黄FAB
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    repository: TodoRepository,
) {
    // remember: 避免每次重组新建 Flow → collectAsState 取消重订阅
    val allItems by remember { repository.observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val active = remember(allItems) { allItems.filter { !it.done } }
    val completed = remember(allItems) { allItems.filter { it.done } }
    var completedCollapsed by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { TodoNotificationHelper.ensureChannel(context) }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    // 保存带提醒的待办时申请通知权限；被拒不阻塞保存（闹钟照排），仅提示
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    "未授予通知权限，待办提醒可能无法显示",
                    actionLabel = "去设置",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                        "未授予精确闹钟权限，提醒可能不准确",
                        actionLabel = "去设置",
                        duration = SnackbarDuration.Long
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("待办", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingItem = null; showEditSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, "新建待办")
            }
        }
    ) { padding ->
        if (active.isEmpty() && completed.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("还没有待办事项", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("点击右下角 + 添加第一项", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.m),
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
                        Text("已完成 ${completed.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
            onSave = { text, done, reminderAt, repeatRule ->
                scope.launch {
                    val base = editingItem
                    val toSave = if (base == null) {
                        TodoItem(text = text, done = done, sortOrder = active.size, reminderAt = reminderAt, repeatRule = repeatRule)
                    } else {
                        base.copy(text = text, done = done, reminderAt = reminderAt, repeatRule = repeatRule)
                    }
                    repository.save(toSave)
                    if (toSave.reminderAt != null && !toSave.done) {
                        TodoNotificationHelper.ensureChannel(context)
                        ensureNotificationPermissionForReminder()
                        ensureExactAlarmPermission()
                    }
                    showEditSheet = false
                }
            },
            onDismiss = { showEditSheet = false }
        )
    }
}

@Composable
private fun TodoCard(item: TodoItem, isCompleted: Boolean, onToggle: () -> Unit, onClick: () -> Unit, onDelete: () -> Unit) {
    SwipeDeleteCard(
        onClick = onClick,
        onDelete = onDelete,
        confirmTitle = "删除待办",
        confirmMessage = "确定要删除“${item.text}”吗？",
        containerColor = if (isCompleted) {
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
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
                        DateUtils.formatReminder(item.reminderAt, item.repeatRule),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
