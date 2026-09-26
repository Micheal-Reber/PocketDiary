package com.example.diary.ui.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.data.repository.DiaryRepository
import com.example.diary.ui.components.SearchTextField
import com.example.diary.ui.components.SearchToggleButton
import com.example.diary.ui.components.SwipeDeleteCard
import com.example.diary.ui.editor.markdownToPlainText
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    diaryRepository: DiaryRepository,
    themePreferences: ThemePreferences,
    onWriteDiary: (String?) -> Unit,
    onEditDiary: (String) -> Unit
) {
    // Full-text search — state survives rotation; closing the field clears
    // the query. The observed flow is swapped only when the debounced query
    // changes (avoids a DB round-trip per keystroke).
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }
    LaunchedEffect(searchQuery) {
        kotlinx.coroutines.delay(250)
        debouncedQuery = searchQuery.trim()
    }
    val entriesFlow = remember(debouncedQuery) {
        if (debouncedQuery.isBlank()) diaryRepository.getAllEntries()
        else diaryRepository.searchEntries(debouncedQuery)
    }
    val entries by entriesFlow.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val bgPath by themePreferences.diaryBackgroundPath.collectAsStateWithLifecycle(initialValue = null)
    // Decode once per path change, downscaled — raw camera photos are far too
    // large to decode at full resolution just to crop-fill a phone screen.
    val bgBitmap by produceState<ImageBitmap?>(initialValue = null, bgPath) {
        value = BackgroundImageStore.decode(context, bgPath, maxDim = 1600)
    }
    val hasCustomBg = bgBitmap != null

    Box(Modifier.fillMaxSize()) {
        if (bgBitmap != null) {
            Image(
                bitmap = bgBitmap!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Scrim so cards/text keep their contrast on bright photos.
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
        }

        Scaffold(
            containerColor = if (hasCustomBg) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("日记", fontWeight = FontWeight.Bold) },
                    actions = {
                        SearchToggleButton(
                            searchActive = searchActive,
                            contentDescriptionBase = "日记",
                            onToggle = {
                                searchActive = !searchActive
                                if (!searchActive) searchQuery = ""
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (hasCustomBg) Color.Transparent else MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { onWriteDiary(null) },
                    modifier = Modifier.padding(bottom = BottomBarContentInset),
                    containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "写日记", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (searchActive) {
                    SearchTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "搜索日记内容...",
                        focusRequester = searchFocusRequester
                    )
                    if (debouncedQuery.isNotBlank()) {
                        Text(
                            "找到 ${entries.size} 篇日记",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.xs)
                        )
                    }
                }

        if (entries.isEmpty()) {
            if (searchQuery.isNotBlank()) {
                // Search miss — distinct from the no-diaries state.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未找到相关日记", style = MaterialTheme.typography.bodyLarge,
                        color = if (hasCustomBg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(Spacing.l))
                    Text("还没有日记", style = MaterialTheme.typography.titleMedium,
                        color = if (hasCustomBg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("点击右下角 + 开始写第一篇", style = MaterialTheme.typography.bodySmall,
                        color = if (hasCustomBg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline)
                }
            }
            }
        } else {
                // Group by calendar month (yyyy-MM); each new month gets a
                // big-number divider, like the reference design. groupBy keeps
                // encounter order, so groups run newest → oldest.
                // remember: 只在 entries 变化时重建分组，避免每次重组 O(n)
                val displayItems = remember(entries) {
                    entries.groupBy { it.date.take(7) }
                        .flatMap { (_, monthEntries) ->
                            listOf<DisplayItem>(DisplayItem.Header(monthEntries.first().date.take(7))) +
                                monthEntries.map { DisplayItem.Entry(it) }
                        }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = Spacing.l, bottom = BottomBarContentInset),
                    verticalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    items(
                        displayItems,
                        key = { item ->
                            when (item) {
                                is DisplayItem.Header -> "h_${item.month}"
                                is DisplayItem.Entry -> item.entry.id
                            }
                        }
                    ) { item ->
                        // Smooth placement/removal when entries are added or deleted.
                        Box(Modifier.animateItem()) {
                            when (item) {
                                is DisplayItem.Header -> MonthDivider(item.month.drop(5).toInt(), hasCustomBg)
                                is DisplayItem.Entry -> DiaryCard(
                                    entry = item.entry,
                                    highlightQuery = debouncedQuery,
                                    onClick = { onEditDiary(item.entry.date) },
                                    onDelete = { scope.launch { diaryRepository.deleteEntry(item.entry.id) } }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun DiaryCard(
    entry: DiaryEntry,
    highlightQuery: String = "",
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    SwipeDeleteCard(
        onClick = onClick,
        onDelete = onDelete,
        confirmTitle = "删除日记",
        confirmMessage = "确定要删除这篇日记吗？"
    ) {
        Column(modifier = Modifier.padding(Spacing.xl)) {
            // Heading is always the entry's date; mood rides on the right.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.date, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!entry.mood.isNullOrEmpty()) Text(entry.mood, style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(8.dp))

            // Fixed-size content preview: exactly one line, ellipsized.
            // Markdown syntax is stripped so **bold** reads as bold words.
            HighlightedPreview(
                text = markdownToPlainText(entry.content),
                query = highlightQuery
            )

            Spacer(Modifier.height(10.dp))

            // Weather + location pinned to the card's bottom-left; the row
            // keeps its height even when both are empty so every card is
            // the same size.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.heightIn(min = 18.dp)
            ) {
                Text(entry.weather ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!entry.locationName.isNullOrEmpty()) {
                    if (!entry.weather.isNullOrEmpty()) Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.LocationOn, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(2.dp))
                    Text(entry.locationName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HighlightedPreview(text: String, query: String) {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = 1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        return
    }

    val highlighted = buildAnnotatedString {
        val matcher = Regex(Regex.escape(normalizedQuery), RegexOption.IGNORE_CASE)
        var cursor = 0
        matcher.findAll(text).forEach { match ->
            append(text.substring(cursor, match.range.first))
            val start = length
            append(match.value)
            addStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                ),
                start,
                length
            )
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    Text(
        highlighted,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        minLines = 1,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

/** LazyColumn row model: a month divider or a diary card. */
private sealed interface DisplayItem {
    data class Header(val month: String) : DisplayItem
    data class Entry(val entry: DiaryEntry) : DisplayItem
}

@Composable
private fun MonthDivider(month: Int, hasCustomBg: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            month.toString(),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            style = TextStyle(
                shadow = if (hasCustomBg) {
                    Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 8f)
                } else {
                    Shadow.None
                }
            )
        )
    }
}
