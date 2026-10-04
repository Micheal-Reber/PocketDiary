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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.R
import com.example.diary.data.local.CountdownEvent
import com.example.diary.data.repository.CountdownRepository
import com.example.diary.ui.components.ConfirmDialog
import com.example.diary.ui.components.UtcDatePickerDialog
import com.example.diary.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.LocalDate
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
                title = { Text(if (existingId == null) stringResource(R.string.cd_edit_new) else stringResource(R.string.cd_edit_edit)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = { persist() }, enabled = canSave) { Text(stringResource(R.string.cd_save)) }
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
                ) { Text(stringResource(R.string.cd_save), modifier = Modifier.padding(vertical = Spacing.xs)) }
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
                label = { Text(stringResource(R.string.cd_name)) },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            )

            // 目标日
            ListItem(
                headlineContent = { Text(stringResource(R.string.cd_target)) },
                supportingContent = { Text(stringResource(R.string.cd_target_support, dateStr + weekdaySuffix(context, dateStr))) },
                trailingContent = {
                    TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.cd_choose)) }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clip(MaterialTheme.shapes.small)
            )

            // 卡片风格选择器（双预览卡）
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.cd_card_style), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    // 经典全屏预览
                    StylePreviewCard(
                        label = stringResource(R.string.cd_style_classic),
                        selected = cardStyle == CountdownEvent.CARD_STYLE_CLASSIC,
                        onClick = { cardStyle = CountdownEvent.CARD_STYLE_CLASSIC },
                        accent = MaterialTheme.colorScheme.primary,
                        isPhotoCard = false,
                        modifier = Modifier.weight(1f)
                    )
                    // 照片卡片预览
                    StylePreviewCard(
                        label = stringResource(R.string.cd_style_photo),
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
                Text(stringResource(R.string.cd_pin_label))
                Switch(checked = pinned, onCheckedChange = { pinned = it })
            }

            // 重复规则
            Column {
                Text(stringResource(R.string.cd_repeat), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    FilterChip(
                        selected = repeatRule == CountdownEvent.REPEAT_NONE,
                        onClick = { repeatRule = CountdownEvent.REPEAT_NONE },
                        label = { Text(stringResource(R.string.cd_repeat_none)) }
                    )
                    FilterChip(
                        selected = repeatRule == CountdownEvent.REPEAT_YEARLY,
                        onClick = { repeatRule = CountdownEvent.REPEAT_YEARLY },
                        label = { Text(stringResource(R.string.cd_repeat_yearly)) }
                    )
                    FilterChip(
                        selected = repeatRule == CountdownEvent.REPEAT_MONTHLY,
                        onClick = { repeatRule = CountdownEvent.REPEAT_MONTHLY },
                        label = { Text(stringResource(R.string.cd_repeat_monthly)) }
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
                    Text(stringResource(R.string.cd_advanced))
                    Icon(if (advancedOpen) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }

            if (advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.cd_end_date_item)) },
                        supportingContent = { Text(endDateStr.ifBlank { stringResource(R.string.cd_not_set) }) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { showEndDatePicker = true }) { Text(stringResource(R.string.cd_choose)) }
                                if (endDateStr.isNotBlank()) {
                                    TextButton(onClick = { endDateStr = "" }) { Text(stringResource(R.string.cd_clear)) }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )

                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { timeText = it },
                        label = { Text(stringResource(R.string.cd_time_label)) },
                        placeholder = { Text(stringResource(R.string.cd_time_hint)) },
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
                            Text(stringResource(R.string.cd_plus_one))
                            Text(
                                stringResource(R.string.cd_plus_one_hint),
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
                        Text(stringResource(R.string.cd_highlight))
                        Switch(checked = highlighted, onCheckedChange = { highlighted = it })
                    }

                    // 颜色色板：首格「自动」
                    Column {
                        Text(stringResource(R.string.cd_color), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.cd_color_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.cd_color_auto_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(Spacing.s))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            PaletteDot(
                                color = null, label = stringResource(R.string.palette_auto),
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
                            Text(stringResource(R.string.cd_texture_title), style = MaterialTheme.typography.labelMedium,
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
                    Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Spacing.xs))
                    Text(stringResource(R.string.cd_delete_event), color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }

        if (showDatePicker) {
            UtcDatePickerDialog(
                initialDate = runCatching { LocalDate.parse(dateStr) }.getOrNull(),
                onDismiss = { showDatePicker = false },
                onPick = { showDatePicker = false; dateStr = it.format(DateTimeFormatter.ISO_LOCAL_DATE) }
            )
        }
        if (showEndDatePicker) {
            UtcDatePickerDialog(
                initialDate = runCatching { LocalDate.parse(endDateStr) }.getOrElse { LocalDate.now() },
                onDismiss = { showEndDatePicker = false },
                onPick = { showEndDatePicker = false; endDateStr = it.format(DateTimeFormatter.ISO_LOCAL_DATE) }
            )
        }
        if (showDeleteDialog && existingId != null) {
            ConfirmDialog(
                title = stringResource(R.string.cd_delete_title, name),
                message = stringResource(R.string.cd_delete_edit_message),
                onConfirm = {
                    showDeleteDialog = false
                    scope.launch {
                        com.example.diary.data.image.EventImageStore.clear(context, existingId)
                        clearBlurCache(existingId)
                        repository.delete(existingId)
                        onBack()
                    }
                },
                onDismiss = { showDeleteDialog = false }
            )
        }
    }
}

private fun weekdaySuffix(context: android.content.Context, dateStr: String): String =
    runCatching { " · ${weekdayLabel(context, LocalDate.parse(dateStr))}" }.getOrDefault("")

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
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(Spacing.s),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.cd_preview_photo), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.cd_preview_photo_sub), style = MaterialTheme.typography.bodySmall,
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
                    Text(stringResource(R.string.cd_preview_classic), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("99", fontSize = 32.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                        color = accent)
                    Text(stringResource(R.string.cd_unit_days), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Spacing.xs))
        }
    }
}
