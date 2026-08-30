package com.example.diary.ui.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.data.local.TodoItem
import com.example.diary.util.DateUtils
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderTimeSheet(
    initialAt: Long?,
    initialRepeat: Int,
    onConfirm: (Long, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialDateTime = remember(initialAt) {
        initialAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
            ?: LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0)
    }
    var selectedDate by rememberSaveable { mutableStateOf(initialDateTime.toLocalDate()) }
    var selectedTime by rememberSaveable { mutableStateOf(initialDateTime.toLocalTime().withSecond(0).withNano(0)) }
    var currentMonth by rememberSaveable { mutableStateOf(YearMonth.from(selectedDate)) }
    var repeatRule by rememberSaveable { mutableIntStateOf(initialRepeat) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRepeatMenu by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { Box(Modifier.padding(top=12.dp).width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant)) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("设置提醒时间", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
Text("提醒时间", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp)) {
                    Text(DateUtils.formatDate(selectedDate), color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { showTimePicker = true }) {
                    Text(DateUtils.formatTime(selectedTime), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 14.sp)
                }
            }
            }
            Spacer(Modifier.height(16.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("重复提醒", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp)
                Box {
                    Row(Modifier.clickable { showRepeatMenu = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (repeatRule == TodoItem.REPEAT_DAILY) "每天" else "不重复", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showRepeatMenu, onDismissRequest = { showRepeatMenu = false }) {
                        DropdownMenuItem(text = { Text("不重复") }, onClick = { repeatRule = TodoItem.REPEAT_NONE; showRepeatMenu = false })
                        DropdownMenuItem(text = { Text("每天") }, onClick = { repeatRule = TodoItem.REPEAT_DAILY; showRepeatMenu = false })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(Modifier.height(12.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(DateUtils.formatDate(currentMonth.atDay(1)), color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("一","二","三","四","五","六","日").forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 日历网格 - 修复31号错位：补齐至7的倍数，确保每行7列
            val days = remember(currentMonth) {
                buildList<LocalDate?> {
                    val first = currentMonth.atDay(1)
                    val firstDow = first.dayOfWeek.value
                    repeat(firstDow - 1) { add(null) }
                    for (d in 1..currentMonth.lengthOfMonth()) add(currentMonth.atDay(d))
                    val rem = size % 7
                    if (rem != 0) repeat(7 - rem) { add(null) }
                }
            }
            Column {
                val rows = days.chunked(7)
                rows.forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (date != null) {
                                    val isSelected = date == selectedDate
                                    val isToday = date == LocalDate.now()
                                    Box(
                                        Modifier.size(36.dp).clip(CircleShape)
                                            .background(
                                                when {
                                                    isSelected -> MaterialTheme.colorScheme.primary
                                                    isToday -> MaterialTheme.colorScheme.surfaceContainerHighest
                                                    else -> Color.Transparent
                                                }
                                            )
                                            .clickable { selectedDate = date },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(date.dayOfMonth.toString(), color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onDismiss() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("取消", fontSize = 16.sp) }
                Button(
                    onClick = {
                        val at = LocalDateTime.of(selectedDate, selectedTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val finalAt = if (at <= System.currentTimeMillis() && repeatRule == TodoItem.REPEAT_NONE) at + 24*60*60*1000L else at
                        onConfirm(finalAt, repeatRule)
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("确定", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showTimePicker) {
        var isTextInput by remember { mutableStateOf(false) }
        val timePickerState = rememberTimePickerState(initialHour = selectedTime.hour, initialMinute = selectedTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("选择时间")
                    IconButton(onClick = { isTextInput = !isTextInput }) {
                        Icon(if (isTextInput) Icons.Filled.Schedule else Icons.Filled.Edit, contentDescription = if (isTextInput) "切换拨盘" else "切换输入")
                    }
                }
            },
            text = {
                if (isTextInput) TimeInput(state = timePickerState) else TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            }
        )
    }
}
