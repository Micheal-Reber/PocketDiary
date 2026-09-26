package com.example.diary.ui.countdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diary.data.countdown.DateMath
import com.example.diary.data.countdown.DateMath.CountState
import com.example.diary.data.image.EventImageStore
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.components.ConfirmDialog
import com.example.diary.ui.components.SearchTextField
import com.example.diary.ui.components.SearchToggleButton
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.navigation.glassStroke
import com.example.diary.ui.navigation.glassTint
import com.example.diary.ui.theme.Spacing
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

    val allEvents by remember { repository.observeAll() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
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
                    SearchToggleButton(
                        searchActive = searchActive,
                        contentDescriptionBase = "事件",
                        onToggle = {
                            searchActive = !searchActive
                            if (!searchActive) searchQuery = ""
                        }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreate,
                modifier = Modifier.padding(bottom = BottomBarContentInset),
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "新建倒数日", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchActive) {
                SearchTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "搜索事件名称...",
                    focusRequester = searchFocusRequester
                )
            }

            when {
                events.isEmpty() && searchQuery.isNotBlank() -> EmptyHint("未找到相关事件")
                events.isEmpty() -> EmptyHint("还没有倒数日\n点击右下角 + 添加重要日子")
                gridMode -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Spacing.l, bottom = BottomBarContentInset),
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
                                    clearBlurCache(event.id)
                                    repository.delete(event.id)
                                }
                            }
                        )
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Spacing.l, bottom = BottomBarContentInset),
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
                                        clearBlurCache(event.id)
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
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = if (event.highlighted) BorderStroke(2.dp, accent) else null,
        modifier = Modifier
            .then(if (compact) Modifier.height(120.dp) else Modifier.fillMaxWidth())
            .combinedClickable(onClick = onClick, onLongClick = { showDeleteConfirm = true })
            .background(glassTint(dark), MaterialTheme.shapes.medium)
            .border(1.dp, glassStroke(dark), MaterialTheme.shapes.medium)
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
        ConfirmDialog(
            title = "删除「${event.name}」？",
            message = "该事件的背景图也会一并删除，不可恢复。",
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.HourglassTop, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(Spacing.l))
            Text(
                text, style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
