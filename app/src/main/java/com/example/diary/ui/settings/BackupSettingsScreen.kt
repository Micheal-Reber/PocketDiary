package com.example.diary.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diary.R
import com.example.diary.data.backup.BackupRepository
import com.example.diary.data.backup.ImportResult
import com.example.diary.ui.navigation.BottomBarContentInset
import kotlinx.coroutines.launch

@Composable
fun BackupSettingsScreen(
    backupRepository: BackupRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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
                        showToast(context, context.getString(R.string.settings_export_success))
                    }.onFailure { e ->
                        showToast(context, context.getString(R.string.settings_export_failed, e.message))
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

    SettingsDetailScaffold(
        title = stringResource(R.string.settings_migration),
        onBack = onBack
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroupCard {
                SettingsRow(
                    headline = stringResource(R.string.settings_export),
                    supporting = if (backupBusy) stringResource(R.string.settings_busy) else stringResource(R.string.settings_export_desc),
                    leading = {
                        Icon(
                            Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !backupBusy) {
                            exportLauncher.launch(context.getString(R.string.settings_backup_filename))
                        }
                )

                GroupDivider()

                SettingsRow(
                    headline = stringResource(R.string.settings_import),
                    supporting = if (backupBusy) stringResource(R.string.settings_busy) else stringResource(R.string.settings_import_desc),
                    leading = {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !backupBusy) {
                            importLauncher.launch(arrayOf("application/zip"))
                        }
                )
            }

            Spacer(Modifier.height(BottomBarContentInset))
        }
    }

    // 导入确认弹窗（破坏性：清空现有数据）
    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text(stringResource(R.string.settings_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_import_confirm_body)) },
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
                                            context.getString(
                                                R.string.settings_import_success,
                                                result.diaryEntriesImported,
                                                result.habitsImported,
                                                result.habitRecordsImported,
                                                result.countdownEventsImported,
                                                result.todosImported,
                                                result.imagesImported
                                            )
                                        )
                                    }
                                    is ImportResult.Failure -> {
                                        showToast(
                                            context,
                                            context.getString(R.string.settings_import_failed, result.message)
                                        )
                                    }
                                }
                            } finally {
                                backupBusy = false
                            }
                        }
                    },
                    enabled = !backupBusy
                ) { Text(stringResource(R.string.settings_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportUri = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}