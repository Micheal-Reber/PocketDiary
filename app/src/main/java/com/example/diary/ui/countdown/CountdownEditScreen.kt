package com.example.diary.ui.countdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 倒数日编辑页（新建/编辑复用）：名称、目标日、置顶、重复规则，
 * 进阶折叠区（结束日/精确时间/+1日/颜色/高亮）。顶部与底部双保存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownEditScreen(
    existingId: Long?,
    repository: CountdownRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    var loaded by rememberSaveable { mutableStateOf(existingId == null) }
    var name by rememberSaveable { mutableStateOf("") }
    var dateStr by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var repeatRule by rememberSaveable { mutableIntStateOf(CountdownEvent.REPEAT_NONE) }
    var plusOne by rememberSaveable { mutableStateOf(false) }
    var colorIndex by rememberSaveable { mutableIntStateOf(CountdownPalette.AUTO) }
    var highlighted by rememberSaveable { mutableStateOf(false) }
    var endDateStr by rememberSaveable { mutableStateOf("") }   // 空 = 未设
    var timeText by rememberSaveable { mutableStateOf("") }     // 空 = 未设
    var advancedOpen by rememberSaveable { mutableStateOf(false) }

    // 卡片风格：0=经典全屏(CLASSIC)，1=照片卡片(PHOTO_CARD)
    var cardStyle by rememberSaveable { mutableIntStateOf(CountdownEvent.CARD_STYLE_CLASSIC) }
    // 照片卡专属设置
    var blurRadius by rememberSaveable { mutableIntStateOf(0) }
    var fontDark by rememberSaveable { mutableStateOf(false) }
    var textureIndex by rememberSaveable { mutableIntStateOf(-1) }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showEndDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    // 编辑模式：仅首次进入装载一次（rememberSaveable 已恢复则跳过）
    LaunchedEffect(existingId) {
        if (existingId != null && !loaded) {
            repository.get(existingId)?.let { e ->
                name = e.name; dateStr = e.date; pinned = e.pinned
                repeatRule = e.repeatRule; plusOne = e.plusOne
                colorIndex = e.colorIndex; highlighted = e.highlighted
                endDateStr = e.endDate ?: ""; timeText = e.time ?: ""
                cardStyle = e.cardStyle
                blurRadius = e.blurRadius
                fontDark = e.fontDark
                textureIndex = e.textureIndex
            }
            loaded = true
        }
    }

    val dateValid = runCatching { LocalDate.parse(dateStr) }.isSuccess
    val endDateValid = endDateStr.isBlank() || runCatching { LocalDate.parse(endDateStr) }.isSuccess
    val timeValid = timeText.isBlank() || Regex("^\\d{1,2}:\\d{2}$").matches(timeText.trim())
    val canSave = name.isNotBlank() && dateValid && endDateValid && timeValid

    fun persist() {
        if (!canSave) return
        scope.launch {
            repository.save(
                CountdownEvent(
                    id = existingId ?: 0L,
                    name = name.trim(),
                    date = dateStr,
                    pinned = pinned,
                    repeatRule = repeatRule,
                    plusOne = plusOne,
                    colorIndex = colorIndex,
                    highlighted = highlighted,
                    endDate = if (endDateStr.isBlank()) null else endDateStr,
                    time = if (timeText.isBlank()) null else timeText.trim(),
                    textureIndex = textureIndex,
                    cardStyle = cardStyle,
                    blurRadius = blurRadius,
                    fontDark = fontDark
                )
            )
            onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (existingId == null) "新建倒数日" else "编辑倒数日") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { persist() }, enabled = canSave) { Text("保存") }
                }
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Button(
                    onClick = { persist() },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.l, vertical = Spacing.m)
                ) { Text("保存", modifier = Modifier.padding(vertical = Spacing.xs)) }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.l)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("事件名称") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )

            // 目标日
            ListItem(
                headlineContent = { Text("目标日") },
                supportingContent = { Text("未来日期为倒数，过去日期为正数；$dateStr${weekdaySuffix(dateStr)}") },
                trailingContent = {
                    TextButton(onClick = { showDatePicker = true }) { Text("选择") }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clip(MaterialTheme.shapes.small)
            )

            // 卡片风格选择器（双预览卡）
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("卡片风格", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    // 经典全屏预览
                    StylePreviewCard(
                        label = "经典全屏",
                        selected = cardStyle == CountdownEvent.CARD_STYLE_CLASSIC,
                        onClick = { cardStyle = CountdownEvent.CARD_STYLE_CLASSIC },
                        accent = MaterialTheme.colorScheme.primary,
                        isPhotoCard = false,
                        modifier = Modifier.weight(1f)
                    )
                    // 照片卡片预览
                    StylePreviewCard(
                        label = "照片卡片",
                        selected = cardStyle == CountdownEvent.CARD_STYLE_PHOTO_CARD,
                        onClick = { cardStyle = CountdownEvent.CARD_STYLE_PHOTO_CARD },
                        accent = MaterialTheme.colorScheme.primary,
                        isPhotoCard = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 置顶
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("置顶显示在列表最前")
                Switch(checked = pinned, onCheckedChange = { pinned = it })
            }

            // 重复规则
            Column {
                Text("重复规则", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(
                        selected = repeatRule == CountdownEvent.REPEAT_NONE,
                        onClick = { repeatRule = CountdownEvent.REPEAT_NONE },
                        label = { Text("不重复") }
                    )
                    FilterChip(
                        selected = repeatRule == CountdownEvent.REPEAT_YEARLY,
                        onClick = { repeatRule = CountdownEvent.REPEAT_YEARLY },
                        label = { Text("每年") }
                    )
                    FilterChip(
                        selected = repeatRule == CountdownEvent.REPEAT_MONTHLY,
                        onClick = { repeatRule = CountdownEvent.REPEAT_MONTHLY },
                        label = { Text("每月") }
                    )
                }
            }

            // 进阶设置折叠区
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.small,
                onClick = { advancedOpen = !advancedOpen }
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.l, vertical = Spacing.m),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("进阶设置")
                    Icon(if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }

            if (advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    ListItem(
                        headlineContent = { Text("结束日") },
                        supportingContent = { Text(endDateStr.ifBlank { "未设置" }) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { showEndDatePicker = true }) { Text("选择") }
                                if (endDateStr.isNotBlank()) {
                                    TextButton(onClick = { endDateStr = "" }) { Text("清除") }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text("精确时间（HH:mm，可选）") },
                        placeholder = { Text("如 20:00") },
                        isError = !timeValid,
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("+1日")
                            Text(
                                "计数含首尾当天整体 +1",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = plusOne, onCheckedChange = { plusOne = it })
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("高亮旗标")
                        Switch(checked = highlighted, onCheckedChange = { highlighted = it })
                    }

                    // 字色切换：两种风格通用（黑/白字）
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("文字颜色")
                        androidx.compose.material3.Switch(
                            checked = fontDark,
                            onCheckedChange = { fontDark = it }
                        )
                    }
                    Text(if (fontDark) "黑字（适合浅色背景/纹理）" else "白字（适合深色背景/纹理）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.l)
                    )

                    // 颜色色板：首格「自动」
                    Column {
                        Text("颜色", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("应用于卡片徽章、高亮描边与详情页数字",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("自动：倒数 = 蓝 · 正数 = 橙",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(Spacing.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            PaletteDot(
                                color = null, label = "自动",
                                selected = colorIndex == CountdownPalette.AUTO,
                                onClick = { colorIndex = CountdownPalette.AUTO }
                            )
                            CountdownPalette.colors.forEachIndexed { idx, c ->
                                PaletteDot(
                                    color = c, label = null,
                                    selected = colorIndex == idx + 1,
                                    onClick = { colorIndex = idx + 1 }
                                )
                            }
                        }
                    }

                    // 纹理选择器：CLASSIC 始终显示；PHOTO_CARD 选过纹理后也显示
                    if (cardStyle == CountdownEvent.CARD_STYLE_CLASSIC || textureIndex >= 0) {
                        Column(Modifier.padding(top = Spacing.m)) {
                            Text("内置纹理", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(Spacing.s))
                            TexturePickerRow(
                                selectedIndex = textureIndex,
                                onSelect = { textureIndex = it },
                                showNone = true
                            )
                        }
                    }
                }
            }

            // 删除（编辑态）
            if (existingId != null) {
                TextButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Spacing.xs))
                    Text("删除此事件", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }

        if (showDatePicker) {
            DatePickerModal(
                initial = LocalDate.parse(dateStr),
                onDismiss = { showDatePicker = false },
                onPick = { showDatePicker = false; dateStr = it }
            )
        }
        if (showEndDatePicker) {
            DatePickerModal(
                initial = runCatching { LocalDate.parse(endDateStr) }.getOrElse { LocalDate.now() },
                onDismiss = { showEndDatePicker = false },
                onPick = { showEndDatePicker = false; endDateStr = it }
            )
        }
        if (showDeleteDialog && existingId != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("删除「$name」？") },
                text = { Text("不可恢复。") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog = false
                        scope.launch {
                            com.example.diary.data.image.EventImageStore.clear(context, existingId)
                            repository.delete(existingId)
                            onBack()
                        }
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
                }
            )
        }
    }
}

private fun weekdaySuffix(dateStr: String): String =
    runCatching { " · ${weekdayLabel(LocalDate.parse(dateStr))}" }.getOrDefault("")

@Composable
private fun PaletteDot(color: Color?, label: String?, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
    ) {
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        } else if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 风格预览卡：经典全屏 / 照片卡片 两种风格的微缩预览。 */
@Composable
private fun StylePreviewCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: androidx.compose.ui.graphics.Color,
    isPhotoCard: Boolean,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        shape = shape,
        modifier = modifier
            .height(120.dp)
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) accent else androidx.compose.ui.graphics.Color.Transparent,
                shape = shape
            ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isPhotoCard) {
                // 照片卡片预览：3:2 圆角卡 + 模拟模糊 + 文字
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(1.5f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(Spacing.s),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("照片卡", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("横图背景", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                // 经典全屏预览：竖向全屏卡 + 大数字
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(Spacing.s),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("经典", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("99", fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        color = accent)
                    Text("天", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xs))
        }
    }
}

/** M3 DatePicker 弹窗——UTC 毫秒换算（与日记编辑器同约定）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerModal(initial: LocalDate, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPick(
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    )
                } ?: onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        DatePicker(state = state)
    }
}
