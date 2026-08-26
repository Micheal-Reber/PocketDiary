package com.example.diary.ui.countdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.countdown.DateMath.CountState
import com.example.diary.data.image.EventImageStore
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 倒数日列表页：搜索 / 列表-网格切换 / 蓝橙徽章卡片 / 置顶排序 /
 * 长按删除确认（滑动删除留给日记列表，密集小卡用长按更顺手）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownListScreen(
    repository: CountdownRepository,
    onOpenDetail: (Long) -> Unit,
    onCreate: () -> Unit
) {
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchActive) { if (searchActive) searchFocusRequester.requestFocus() }

    val allEvents by repository.observeAll().collectAsState(initial = emptyList())
    val today = remember { LocalDate.now() }
    val events = remember(allEvents, searchQuery) {
        if (searchQuery.isBlank()) allEvents
        else allEvents.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("倒数日", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { gridMode = !gridMode }) {
                        Icon(
                            if (gridMode) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (gridMode) "列表视图" else "网格视图"
                        )
                    }
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    }) {
                        Icon(
                            if (searchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searchActive) "关闭搜索" else "搜索事件"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "新建倒数日", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索事件名称...", color = MaterialTheme.colorScheme.outline) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.l, vertical = Spacing.xs)
                        .focusRequester(searchFocusRequester)
                )
            }

            when {
                events.isEmpty() && searchQuery.isNotBlank() -> EmptyHint("未找到相关事件")
                events.isEmpty() -> EmptyHint("还没有倒数日\n点击右下角 + 添加重要日子")
                gridMode -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    items(events, key = { it.id }) { event ->
                        EventCard(
                            event, today, compact = true,
                            onClick = { onOpenDetail(event.id) },
                            onDelete = {
                                scope.launch {
                                    EventImageStore.clear(context, event.id)
                                    repository.delete(event.id)
                                }
                            }
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(Spacing.l),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    items(events, key = { it.id }) { event ->
                        Box(Modifier.animateItem()) {
                            EventCard(
                                event, today, compact = false,
                                onClick = { onOpenDetail(event.id) },
                                onDelete = {
                                    scope.launch {
                                        EventImageStore.clear(context, event.id)
                                        repository.delete(event.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventCard(
    event: CountdownEvent,
    today: LocalDate,
    compact: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val state = remember(event.date, event.repeatRule, event.plusOne, today) {
        DateMath.compute(event.date, event.repeatRule, event.plusOne, today)
    }
    val accent = eventAccent(event.colorIndex, state)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (event.highlighted) BorderStroke(2.dp, accent) else null,
        modifier = Modifier
            .then(if (compact) Modifier.height(120.dp) else Modifier.fillMaxWidth())
            .combinedClickable(onClick = onClick, onLongClick = { showDeleteConfirm = true })
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (event.pinned) {
                        Icon(
                            Icons.Default.PushPin, "已置顶",
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                    }
                    Text(
                        event.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    stateLabel(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // 数字徽章：彩色圆形底 + 白字（就是今天 → 「今」）
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (compact) 56.dp else 64.dp)
                    .clip(CircleShape)
                    .background(accent)
            ) {
                Text(
                    text = when (state) {
                        is CountState.Today -> "今"
                        is CountState.Countdown -> "${state.days}"
                        is CountState.Countup -> "${state.days}"
                    },
                    color = Color.White,
                    style = if (compact) MaterialTheme.typography.titleMedium
                    else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除「${event.name}」？") },
            text = { Text("该事件的背景图也会一并删除，不可恢复。") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.HourglassEmpty, null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(Spacing.l))
            Text(
                text, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
