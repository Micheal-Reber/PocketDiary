package com.example.diary.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.data.local.TodoItem
import com.example.diary.util.DateUtils

/**
 * 图1 精准还原 + 主题跟随：底部弹板 + □ + 输入 + 设置提醒胶囊 + 完成（主色）
 * 配色跟随 MaterialTheme（图一浅色版），不再硬编码深色
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditSheet(
    existingItem: TodoItem?,
    onSave: (String, Boolean, Long?, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(existingItem?.text ?: "") }
    var done by rememberSaveable { mutableStateOf(existingItem?.done ?: false) }
    var reminderAt by rememberSaveable { mutableStateOf(existingItem?.reminderAt) }
    var repeatRule by rememberSaveable { mutableIntStateOf(existingItem?.repeatRule ?: TodoItem.REPEAT_NONE) }
    var showReminderSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val canSave = text.trim().isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top = 12.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // 输入行：□ + 文本
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = done,
                    onCheckedChange = { done = it }
                )
                Spacer(Modifier.width(12.dp))
                TextField(
                    value = text,
                    onValueChange = { if (it.length <= 200) text = it },
                    placeholder = { Text("输入待办...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(fontSize = 17.sp)
                )
            }
            Spacer(Modifier.height(16.dp))
// 底部操作区：设置提醒 pill + 完成
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fmt = remember(reminderAt) {
                    DateUtils.formatReminderAt(reminderAt, repeatRule)
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.clickable { showReminderSheet = true }
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (fmt == null) "设置提醒" else fmt,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (fmt != null) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "×",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { reminderAt = null; repeatRule = TodoItem.REPEAT_NONE }
                            )
                        }
                    }
                }
                TextButton(onClick = {
                    if (canSave) onSave(text.trim(), done, reminderAt, repeatRule)
                }, enabled = canSave) {
                    Text("完成", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (text.length >= 180) {
                Text(
                    "${text.length}/200",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (text.length > 200) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }

    if (showReminderSheet) {
        ReminderTimeSheet(
            initialAt = reminderAt,
            initialRepeat = repeatRule,
            onConfirm = { at, repeat ->
                reminderAt = at
                repeatRule = repeat
                showReminderSheet = false
            },
            onDismiss = { showReminderSheet = false }
        )
    }
}
