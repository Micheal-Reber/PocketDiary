package com.example.diary.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.diary.data.local.DiaryEntry
import com.example.diary.data.photo.PhotoStore
import com.example.diary.data.repository.DiaryRepository
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryListScreen(
    diaryRepository: DiaryRepository,
    onWriteDiary: (String?) -> Unit,
    onEditDiary: (String) -> Unit
) {
    val entries by diaryRepository.getAllEntries()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日记", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onWriteDiary(null) },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "写日记", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📝", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(16.dp))
                    Text("还没有日记", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.height(4.dp))
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
                    DiaryCard(entry = entry, onClick = { onEditDiary(entry.date) })
                }
            }
        }
    }
}

@Composable
private fun DiaryCard(entry: DiaryEntry, onClick: () -> Unit) {
    val photoList = remember(entry.photoPaths) {
        // Use PhotoStore.parsePaths for consistent CSV handling and drop files
        // that no longer exist (e.g., cleared by OS or user manually deleted).
        PhotoStore.parsePaths(entry.photoPaths)
            .map { File(it) }
            .filter { it.exists() }
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.title.ifEmpty { "无标题" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!entry.mood.isNullOrEmpty()) {
                    Text(entry.mood, style = MaterialTheme.typography.titleLarge)
                }
            }
            if (entry.content.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    entry.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (photoList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(photoList, key = { it.absolutePath }) { f ->
                        AsyncImage(
                            model = f,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (!entry.weather.isNullOrEmpty()) {
                    Text("  ${entry.weather}", style = MaterialTheme.typography.labelSmall)
                }
                if (!entry.locationName.isNullOrEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.outline)
                    Text(
                        "  ${entry.locationName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
