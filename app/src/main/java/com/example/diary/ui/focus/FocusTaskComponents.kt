package com.example.diary.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.R
import com.example.diary.data.focus.FocusTimer
import com.example.diary.data.local.FocusTask
import com.example.diary.ui.components.SwipeDeleteCard
import com.example.diary.ui.navigation.glassStroke
import com.example.diary.ui.navigation.glassTint
import com.example.diary.ui.theme.Spacing

/**
 * 专注任务卡片：**点一下直接开一段专注**，长按才进编辑（左滑仍可归档）。
 * 右侧不再放 ▶ 按钮 —— 整张卡就是那个按钮，少一个可点区域少一处歧义。
 * 没有完成态 —— 事项不会消失，下次继续专注同一个。
 */
@Composable
fun FocusTaskCard(
    task: FocusTask,
    onStartFocus: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = if (dark) {
        Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = 0.36f), Color.Black.copy(alpha = 0.28f))
        )
    } else {
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.44f), Color.White.copy(alpha = 0.35f))
        )
    }
    SwipeDeleteCard(
        onClick = onStartFocus,
        onLongClick = onEdit,
        onDelete = onArchive,
        confirmTitle = stringResource(R.string.focus_archive_title),
        confirmMessage = stringResource(R.string.focus_archive_message, task.title),
        containerColor = Color.Transparent,
        cardModifier = Modifier
            .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.medium)
            .background(tint, MaterialTheme.shapes.medium)
            .border(1.dp, glassStroke(dark), MaterialTheme.shapes.medium)
    ) {
        Row(
            Modifier.padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (task.focusMillis > 0L || task.sessionCount > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(
                            R.string.focus_task_meta,
                            task.sessionCount,
                            FocusTimer.formatAccumulated(task.focusMillis)
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.focus_start_action),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/** 专注任务编辑弹板：只有标题一项 —— 没有提醒、没有完成态 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusTaskEditSheet(
    existingTask: FocusTask?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(existingTask?.title ?: "") }
    val canSave = title.trim().isNotBlank()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Outlined.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                TextField(
                    value = title,
                    onValueChange = { if (it.length <= 200) title = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.focus_task_input_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (existingTask != null && existingTask.focusMillis > 0L) {
                    Text(
                        stringResource(R.string.focus_task_total, FocusTimer.formatAccumulated(existingTask.focusMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                TextButton(
                    onClick = { if (canSave) onSave(title.trim()) },
                    enabled = canSave
                ) {
                    Text(
                        stringResource(R.string.common_done),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
            if (title.length >= 180) {
                Text(
                    "${title.length}/200",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
