package com.example.diary.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.data.local.TodoItem
import com.example.diary.data.repository.TodoRepository
import com.example.diary.data.todo.TodoNotificationHelper
import com.example.diary.data.todo.TodoReminderScheduler
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 待办列表 - 贴合图一浅色整体 + 图三已完成折叠
 * 主题跟随 MaterialTheme（亮/暗自动），不再硬编码黑底/黄FAB
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    repository: TodoRepository,
) {
    val allItems by repository.observeAll().collectAsState(initial = emptyList())
    val active = remember(allItems) { allItems.filter { !it.done } }
    val completed = remember(allItems) { allItems.filter { it.done } }
    var completedCollapsed by rememberSaveable { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<TodoItem?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) { TodoNotificationHelper.ensureChannel(context) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("待办", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                actions = {
                    // 右上 ✎ 与图一一致（预留编辑入口，暂无额外动作）
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Filled.Add, // 使用 Add 占位，若需 pencil 可换 Icons.Filled.Edit
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
                        scope.launch {
                            val updated = item.copy(done = true)
                            repository.save(updated)
                            TodoReminderScheduler.cancel(context, item.id)
                        }
                    },
                    onClick = { editingItem = item; showEditSheet = true },
                    onDelete = {
                        scope.launch {
                            repository.delete(item.id)
                            TodoReminderScheduler.cancel(context, item.id)
                        }
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
                                scope.launch {
                                    val updated = item.copy(done = false)
                                    repository.save(updated)
                                    if (updated.reminderAt != null) TodoReminderScheduler.schedule(context, updated)
                                }
                            },
                            onClick = { editingItem = item; showEditSheet = true },
                            onDelete = {
                                scope.launch {
                                    repository.delete(item.id)
                                    TodoReminderScheduler.cancel(context, item.id)
                                }
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
                    val id = repository.save(toSave)
                    val saved = toSave.copy(id = if (toSave.id == 0L) id else toSave.id)
                    if (saved.reminderAt != null && !saved.done) {
                        TodoNotificationHelper.ensureChannel(context)
                        TodoReminderScheduler.schedule(context, saved)
                    } else {
                        TodoReminderScheduler.cancel(context, saved.id)
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
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    val deleteThreshold = -150f

    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
        // 红色右滑删除背景 - 抄日记
        Box(
            Modifier.matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                .padding(end = Spacing.xl),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
        }
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = if (isCompleted) MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceContainerLow
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < deleteThreshold) showDeleteConfirm = true
                            offsetX = 0f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-250f, 0f)
                        }
                    )
                }
                .clickable { onClick() }
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
                            formatReminder(item.reminderAt, item.repeatRule),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除待办") },
            text = { Text("确定要删除“${item.text}”吗？") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
}

private fun formatReminder(at: Long, repeat: Int): String {
    val fmt = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
    val base = fmt.format(java.util.Date(at))
    return if (repeat == TodoItem.REPEAT_DAILY) "$base · 每天" else base
}
