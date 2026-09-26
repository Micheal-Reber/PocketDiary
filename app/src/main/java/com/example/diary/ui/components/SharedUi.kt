package com.example.diary.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.diary.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.roundToInt

// 滑动删除阈值/上限（日记卡与待办卡共用，原先各复制一份）
private const val SWIPE_DELETE_THRESHOLD = -150f
private const val SWIPE_OFFSET_MAX = -250f

/** 通用确认弹窗（删除/放弃编辑等）；调用方自行控制 visible 与回调时序。 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "删除",
    dismissText: String = "取消",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } }
    )
}

/**
 * 删除触发二选一：左滑露出红底 + 超阈值弹确认框（enableSwipe=true），
 * 或长按直接弹确认框（enableSwipe=false）。卡片内容经 [content] 插槽注入
 * （Modifier 顺序固定：offset → 手势 → clickable，保证点击/拖拽不互抢）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeDeleteCard(
    onClick: () -> Unit,
    onDelete: () -> Unit,
    confirmTitle: String,
    confirmMessage: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    cardModifier: Modifier = Modifier,
    enableSwipe: Boolean = true,
    content: @Composable () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)) {
        // matchParentSize 按 Card 最终尺寸铺满红底（仅左滑模式露出）
        if (enableSwipe) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.medium)
                    .padding(end = Spacing.xl),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete, "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enableSwipe) {
                        Modifier
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (offsetX < SWIPE_DELETE_THRESHOLD) showDeleteConfirm = true
                                        offsetX = 0f
                                    },
                                    onHorizontalDrag = { _, dragAmount ->
                                        offsetX = (offsetX + dragAmount).coerceIn(SWIPE_OFFSET_MAX, 0f)
                                    }
                                )
                            }
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (enableSwipe) {
                        Modifier.clickable { onClick() }
                    } else {
                        Modifier.combinedClickable(
                            onClick = onClick,
                            onLongClick = { showDeleteConfirm = true }
                        )
                    }
                )
                .then(cardModifier)
        ) {
            content()
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = confirmTitle,
            message = confirmMessage,
            onConfirm = { showDeleteConfirm = false; onDelete() },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

/** 顶栏内搜索开关钮（日记/倒数日共用二态图标）。 */
@Composable
fun SearchToggleButton(
    searchActive: Boolean,
    contentDescriptionBase: String,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        Icon(
            if (searchActive) Icons.Default.Close else Icons.Default.Search,
            contentDescription = if (searchActive) "关闭搜索" else "搜索$contentDescriptionBase"
        )
    }
}

/** 列表页搜索输入框（共享样式：无 indicator、surfaceContainerHigh 底）。 */
@Composable
fun SearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = MaterialTheme.colorScheme.outline) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.xs)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
    )
}

/**
 * 共享日期选择弹窗：selectedDateMillis 为 UTC 毫秒，换算务必走 ZoneOffset.UTC
 * （勿用 systemDefault，远时区/DST 会 ±1 天）。初始日期可空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UtcDatePickerDialog(
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPick(
                        Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                    )
                } ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        DatePicker(state = state)
    }
}

/** 可横滑的心情/天气 FilterChip 行（编辑器两行同构）；再点一次取消选中传 null。 */
@Composable
fun PresetChipRow(
    values: List<String>,
    labels: List<String>,
    selected: String?,
    onToggle: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        values.forEachIndexed { index, icon ->
            val isSelected = selected == icon
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(if (isSelected) null else icon) },
                border = null,
                label = { Text("$icon ${labels[index]}", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
