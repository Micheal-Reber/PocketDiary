package com.example.diary.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.BuildConfig
import com.example.diary.data.backup.BackupRepository
import com.example.diary.data.backup.ImportResult
import com.example.diary.data.image.BackgroundImageStore
import com.example.diary.data.preferences.ThemePreferences
import com.example.diary.ui.navigation.BottomBarContentInset
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themePreferences: ThemePreferences,
    backupRepository: BackupRepository,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isDarkMode by themePreferences.isDarkMode.collectAsStateWithLifecycle(initialValue = false)
    val dynamicColor by themePreferences.dynamicColor.collectAsStateWithLifecycle(initialValue = true)
    // Diary list background: picked photo copied into private storage; the
    // stored value is filesDir-relative (null = default color background).
    val bgPath by themePreferences.diaryBackgroundPath.collectAsStateWithLifecycle(initialValue = null)
    val pickBackground = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val file = BackgroundImageStore.importFromUri(context, uri)
                if (file != null) {
                    themePreferences.setDiaryBackgroundPath(BackgroundImageStore.RELATIVE_PATH)
                }
            }
        }
    }

    // Export/Import launchers — busy 防双击；导入前二次确认（全量覆盖）
    var backupBusy by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null && !backupBusy) {
            scope.launch {
                backupBusy = true
                try {
                    val result = backupRepository.export(uri)
                    result.onSuccess {
                        showToast(context, "导出成功")
                    }.onFailure { e ->
                        showToast(context, "导出失败: ${e.message}")
                    }
                } finally {
                    backupBusy = false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !backupBusy) {
            pendingImportUri = uri
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        // 导入确认弹窗（破坏性：清空现有数据）
        if (pendingImportUri != null) {
            AlertDialog(
                onDismissRequest = { pendingImportUri = null },
                title = { Text("导入将清空现有数据？") },
                text = { Text("日记、习惯、倒数日、待办与图片都会被备份内容覆盖，此操作不可撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val uri = pendingImportUri ?: return@TextButton
                            pendingImportUri = null
                            scope.launch {
                                backupBusy = true
                                try {
                                    when (val result = backupRepository.importData(uri)) {
                                        is ImportResult.Success -> {
                                            showToast(
                                                context,
                                                "导入成功: ${result.diaryEntriesImported}篇日记, ${result.habitsImported}个习惯, " +
                                                    "${result.habitRecordsImported}条打卡, ${result.countdownEventsImported}个倒数日, " +
                                                    "${result.todosImported}条待办, ${result.imagesImported}张图片"
                                            )
                                        }
                                        is ImportResult.Failure -> {
                                            showToast(context, "导入失败: ${result.message}")
                                        }
                                    }
                                } finally {
                                    backupBusy = false
                                }
                            }
                        },
                        enabled = !backupBusy
                    ) { Text("确认导入") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImportUri = null }) { Text("取消") }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance section
            Text(
                "外观",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            ListItem(
                headlineContent = { Text("暗色模式") },
                supportingContent = { Text("切换深色/浅色主题") },
                leadingContent = {
                    Icon(Icons.Default.DarkMode, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { checked ->
                            scope.launch { themePreferences.setDarkMode(checked) }
                        }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("壁纸取色") },
                supportingContent = {
                    Text(
                        if (dynamicColor) "跟随系统壁纸配色（Material You）"
                        else "使用应用默认墨绿配色"
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Palette, contentDescription = null)
                },
                trailingContent = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { checked ->
                            scope.launch { themePreferences.setDynamicColor(checked) }
                        }
                    )
                }
            )

            ListItem(
                headlineContent = { Text("日记背景") },
                supportingContent = {
                    Text(
                        if (bgPath != null) "已自定义，点击更换照片"
                        else "使用默认背景，点击选择照片"
                    )
                },
                leadingContent = {
                    Icon(Icons.Default.Wallpaper, contentDescription = null)
                },
                trailingContent = {
                    if (bgPath != null) {
                        TextButton(onClick = {
                            scope.launch {
                                BackgroundImageStore.clear(context)
                                themePreferences.setDiaryBackgroundPath(null)
                            }
                        }) { Text("恢复默认") }
                    }
                },
                modifier = Modifier.clickable {
                    pickBackground.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Data Migration section
            Text(
                "数据迁移",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            ListItem(
                headlineContent = { Text("导出数据") },
                supportingContent = {
                    Text(if (backupBusy) "处理中..." else "备份所有日记、习惯、倒数日、照片和设置到 ZIP 文件")
                },
                leadingContent = {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !backupBusy) {
                        exportLauncher.launch("PocketDiary备份.zip")
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            ListItem(
                headlineContent = { Text("导入数据") },
                supportingContent = {
                    Text(if (backupBusy) "处理中..." else "从 ZIP 备份恢复所有数据（清空现有数据后导入）")
                },
                leadingContent = {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !backupBusy) {
                        importLauncher.launch(arrayOf("application/zip"))
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // About section
            Text(
                "关于",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            ListItem(
                headlineContent = { Text("PocketDiary") },
                supportingContent = {
                    Text("版本 ${BuildConfig.VERSION_NAME} · 简洁好用的日记本")
                },
                leadingContent = {
                    Icon(Icons.Default.Info, contentDescription = null)
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Spacer(Modifier.height(32.dp))

            // Footer
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Made by Xuan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(Modifier.height(BottomBarContentInset))
        }
    }
}

private fun showToast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
}
