package com.example.diary.ui.diary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.data.repository.DiaryRepository
import com.example.diary.ui.editor.markdownToPlainText
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    diaryRepository: DiaryRepository,
    themePreferences: ThemePreferences,
    onWriteDiary: (String?) -> Unit,
    onEditDiary: (String) -> Unit
) {
    val entries by diaryRepository.getAllEntries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val bgPath by themePreferences.diaryBackgroundPath.collectAsStateWithLifecycle(initialValue = null)
    // Decode once per path change, downscaled — raw camera photos are far too
    // large to decode at full resolution just to crop-fill a phone screen.
    val bgBitmap by produceState<ImageBitmap?>(initialValue = null, bgPath) {
        value = BackgroundImageStore.decode(bgPath, maxDim = 1600)
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (hasCustomBg) Color.Transparent else MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { onWriteDiary(null) },
                    containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, "写日记", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        ) { padding ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📝", style = MaterialTheme.typography.displayMedium)
                    }
                    Spacer(Modifier.height(Spacing.l))
                    Text("还没有日记", style = MaterialTheme.typography.titleMedium,
                        color = if (hasCustomBg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.xs))
                    Text("点击右下角 + 开始写第一篇", style = MaterialTheme.typography.bodySmall,
                        color = if (hasCustomBg) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline)
                }
            }
        } else {
                // Group by calendar month (yyyy-MM); each new month gets a
                // big-number divider, like the reference design. groupBy keeps
                // encounter order, so groups run newest → oldest.
                val displayItems = entries.groupBy { it.date.take(7) }
                    .flatMap { (_, monthEntries) ->
                        listOf<DisplayItem>(DisplayItem.Header(monthEntries.first().date.take(7))) +
                            monthEntries.map { DisplayItem.Entry(it) }
                    }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(Spacing.l),
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

@Composable
private fun DiaryCard(entry: DiaryEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    val deleteThreshold = -150f

    Box(modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
        // Red swipe-delete background — matchParentSize sizes it to the Box's
        // final dimensions (dictated by the Card below), so the red always
        // covers the entire card, edge to edge.
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                .padding(end = Spacing.xl),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(28.dp))
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
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
                .clickable { onClick() },
            shape = MaterialTheme.shapes.medium,
            // Flat tonal card (ReadYou-style): layering via container color,
            // not shadows. surfaceContainerLow sits one step above the page.
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(Spacing.xl)) {
                // Heading is always the entry's date; mood rides on the right.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.date, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!entry.mood.isNullOrEmpty()) Text(entry.mood, style = MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(8.dp))

                // Fixed-size content preview: exactly two lines, ellipsized.
                // Markdown syntax is stripped so **bold** reads as bold words.
                Text(markdownToPlainText(entry.content), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)

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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除日记") },
            text = { Text("确定要删除这篇日记吗？") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") } }
        )
    }
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
