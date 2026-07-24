package com.example.diary.ui.habits

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.diary.data.local.Habit
import java.time.LocalDate

@Composable
internal fun CheckInDialog(
    date: LocalDate,
    habits: List<Habit>,
    checkedMap: Map<Long, Boolean>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}月${date.dayOfMonth}日 打卡") },
        text = {
            if (habits.isEmpty()) {
                Text("还没有习惯，请先添加", color = MaterialTheme.colorScheme.outline)
            } else {
                Column {
                    habits.forEach { habit ->
                        val checked = checkedMap[habit.id] == true
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(habit.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggle(habit.id) },
                                colors = CheckboxDefaults.colors(checkedColor = habitColor(habit.colorIndex))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${habit.emoji} ${habit.name}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
internal fun AddHabitDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("✅") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加习惯") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("习惯名称") },
                    placeholder = { Text("如：早起、跑步、喝水") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { input ->
                        // Cap at 2 user-perceived emoji. Most common emoji (☀️, 🌤,
                        // ❤️) are 2 UTF-16 codepoints apiece, so 2 × 2 = 4 is the
                        // real ceiling. codePointCount (not .length) avoids chopping
                        // surrogate pairs like 🏃‍♀️ in the middle. Truncate rather
                        // than reject — silent rejection leaves the TextField
                        // showing stale text and looks broken.
                        emoji = if (input.isEmpty()) {
                            ""
                        } else if (input.codePointCount(0, input.length) <= 4) {
                            input
                        } else {
                            input.substring(0, input.offsetByCodePoints(0, 4))
                        }
                    },
                    label = { Text("图标 (emoji)") },
                    // widthIn lets the box grow for long emoji strings (rare but
                    // legal — a 2-emoji string is allowed by the cap) and keeps
                    // a sensible min so it doesn't shrink to nothing.
                    modifier = Modifier.widthIn(min = 80.dp, max = 160.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, emoji) },
                // Both fields must be non-blank; an emoji-less habit would render
                // as a blank circle on the calendar.
                enabled = name.isNotBlank() && emoji.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
internal fun ManageHabitsDialog(
    habits: List<Habit>,
    onDelete: (Habit) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理习惯") },
        text = {
            if (habits.isEmpty()) {
                Text("还没有习惯", color = MaterialTheme.colorScheme.outline)
            } else {
                Column {
                    habits.forEach { habit ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${habit.emoji} ${habit.name}", style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { onDelete(habit) }) {
                                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
