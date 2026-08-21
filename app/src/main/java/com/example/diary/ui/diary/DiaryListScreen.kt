package com.example.diary.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.repository.DiaryRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    diaryRepository: DiaryRepository,
    onWriteDiary: (String?) -> Unit,
    onEditDiary: (String) -> Unit
) {
    val entries by diaryRepository.getAllEntries().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日记", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                    Text("📝", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(16.dp))
                    Text("还没有日记", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                    Text("点击右下角 + 开始写第一篇", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    DiaryCard(entry = entry, onClick = { onEditDiary(entry.date) },
                        onDelete = { scope.launch { diaryRepository.deleteEntry(entry.id) } })
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

    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
        // Red swipe-delete background — matchParentSize sizes it to the Box's
        // final dimensions (dictated by the Card below), so the red always
        // covers the entire card, edge to edge.
        Box(
            Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
                .padding(end = 20.dp),
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
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Heading is always the entry's date; mood rides on the right.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(entry.date, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!entry.mood.isNullOrEmpty()) Text(entry.mood, style = MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(8.dp))

                // Fixed-size content preview: exactly two lines, ellipsized.
                Text(entry.content, style = MaterialTheme.typography.bodyMedium,
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
