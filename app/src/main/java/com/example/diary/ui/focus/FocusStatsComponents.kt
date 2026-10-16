package com.example.diary.ui.focus

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.diary.R
import com.example.diary.data.focus.FocusStats
import com.example.diary.data.repository.FocusRepository
import com.example.diary.ui.theme.Spacing
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

private val HeatmapCellSize = 12.dp
private val HeatmapSpacing = 3.dp
private val HeatmapMonthLabelHeight = 16.dp

/** 卡片外壳：小标题（onSurfaceVariant）+ surfaceContainerLow 圆角卡，层次靠色阶不靠阴影 */
@Composable
internal fun FocusStatsCard(
    title: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.l),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(Spacing.m)
            ) { content() }
        }
    }
}

/** 今日目标半圆表盘：180° 弧 + 达标 ✓ + 大字时长 + 次要番茄数 + 可点目标行 */
@Composable
internal fun FocusGoalGauge(
    millis: Long,
    goalMinutes: Int,
    completedSessions: Int,
    onEditGoal: () -> Unit
) {
    val progress = FocusStats.goalProgress(millis, goalMinutes)
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gaugeHeight = maxWidth / 2f + 6.dp
            Box(Modifier.fillMaxWidth().height(gaugeHeight), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.fillMaxWidth().height(gaugeHeight)) {
                    val stroke = 9.dp.toPx()
                    val diameter = size.width - stroke
                    val arcSize = Size(diameter, diameter)
                    val topLeft = Offset(stroke / 2f, stroke / 2f)
                    fun arc(color: Color, sweep: Float) = drawArc(
                        color = color,
                        startAngle = 180f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    arc(track, 180f)
                    if (progress > 0f) arc(primary, 180f * progress)
                }
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.focus_stats_today),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (progress >= 1f) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        FocusRepository.formatMillis(millis),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Text(
                        stringResource(R.string.focus_stats_today_sessions, completedSessions),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Row(
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { onEditGoal() }
                .padding(horizontal = Spacing.s, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                stringResource(R.string.focus_stats_goal_edit, goalMinutes),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** 近 7 天小格：达标/接近用主色按比例渐深，无数据走 surfaceContainerHighest */
@Composable
internal fun FocusRecentDaysRow(days: List<FocusDayCell>, goalMinutes: Int) {
    val weekdayHeaders = stringArrayResource(R.array.weekday_headers)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val primary = MaterialTheme.colorScheme.primary
    val goalMillis = goalMinutes * 60_000L
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        days.forEach { cell ->
            val ratio = if (goalMillis > 0L) {
                (cell.millis.toDouble() / goalMillis.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    weekdayHeaders[cell.date.dayOfWeek.value - 1],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (cell.millis > 0L) {
                                androidx.compose.ui.graphics.lerp(empty, primary, (0.18f + 0.82f * ratio).toFloat())
                            } else {
                                empty
                            }
                        )
                )
                Text(
                    FocusRepository.formatMillis(cell.millis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/** GitHub 风横滚热力图：每列一周（列头写月份），底部「少 → 多」图例，进来自动滚到最新 */
@Composable
internal fun FocusHeatmap(cells: List<FocusDayCell>) {
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val heatColors = listOf(empty) + listOf(0.18f, 0.30f, 0.42f, 0.55f, 0.68f, 0.84f, 1f)
        .map { androidx.compose.ui.graphics.lerp(empty, primary, it) }
    val byDate = remember(cells) { cells.associateBy { it.date } }
    val start = cells.firstOrNull()?.date ?: return
    val today = cells.lastOrNull()?.date ?: return
    val monday = remember(cells) {
        start.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    }
    val weeks = remember(cells) {
        List(ChronoUnit.WEEKS.between(monday, today).toInt() + 1) { index ->
            monday.plusWeeks(index.toLong())
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(weeks.size) { if (weeks.isNotEmpty()) listState.scrollToItem(weeks.lastIndex) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(HeatmapSpacing)) {
            items(weeks.size) { index ->
                val weekStart = weeks[index]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HeatmapSpacing)
                ) {
                    Box(
                        Modifier.height(HeatmapMonthLabelHeight),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            monthLabelForWeek(weekStart),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    repeat(7) { dayIndex ->
                        val date = weekStart.plusDays(dayIndex.toLong())
                        if (date.isBefore(start) || date.isAfter(today)) {
                            Spacer(Modifier.size(HeatmapCellSize))
                        } else {
                            val level = (byDate[date]?.level ?: 0).coerceIn(0, 7)
                            Box(
                                Modifier
                                    .size(HeatmapCellSize)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(heatColors[level])
                            )
                        }
                    }
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                stringResource(R.string.focus_stats_heatmap_less),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            heatColors.forEach { color ->
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(color)
                )
            }
            Text(
                stringResource(R.string.focus_stats_heatmap_more),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 只给「含每月 1 号的那一周」写月份标签，跟参考实现一致 */
private fun monthLabelForWeek(weekStart: LocalDate): String {
    val firstOfMonth = (0L..6L).map { weekStart.plusDays(it) }.firstOrNull { it.dayOfMonth == 1 } ?: return ""
    return "${firstOfMonth.monthValue}月"
}

/**
 * 期间柱图：柱高按当期峰值归一，末柱高亮。
 * 标签预计算（remember）后按柱心绘制——measure 绝不能放 draw 作用域（每帧重测）。
 */
@Composable
internal fun FocusBarChart(
    buckets: List<FocusBucketUi>,
    labels: List<String>,
    highlightLast: Boolean
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val max = remember(buckets) { (buckets.maxOfOrNull { it.millis } ?: 0L).coerceAtLeast(1L) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp)
    val labelStep = if (buckets.size > 16) (buckets.size / 8).coerceAtLeast(1) else 1
    val measured = remember(buckets, labels) {
        labels.map { textMeasurer.measure(it, labelStyle) }
    }

    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        val count = buckets.size.coerceAtLeast(1)
        val slot = size.width / count
        val barWidth = (slot * 0.55f).coerceAtMost(18.dp.toPx())
        val baseline = size.height - 14.dp.toPx()
        drawLine(
            track.copy(alpha = 0.4f),
            Offset(0f, baseline),
            Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx()
        )
        buckets.forEachIndexed { index, bucket ->
            val ratio = (bucket.millis.toDouble() / max.toDouble()).coerceIn(0.0, 1.0)
            val barHeight = (ratio * baseline).toFloat()
                .coerceAtLeast(if (bucket.millis > 0L) 3.dp.toPx() else 0f)
            val x = index * slot + (slot - barWidth) / 2f
            val highlight = highlightLast && index == buckets.lastIndex
            drawRoundRect(
                color = if (bucket.millis > 0L) primary.copy(alpha = if (highlight) 1f else 0.55f) else track,
                topLeft = Offset(x, baseline - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            if (index % labelStep == 0 || (highlightLast && index == buckets.lastIndex)) {
                val text = measured.getOrNull(index) ?: return@forEachIndexed
                val tx = (index * slot + slot / 2f - text.size.width / 2f)
                    .coerceIn(0f, (size.width - text.size.width).coerceAtLeast(0f))
                drawText(text, color = labelColor, topLeft = Offset(tx, baseline + 2.dp.toPx()))
            }
        }
    }
}

@Composable
internal fun FocusChangeText(changePercent: Float) {
    val up = changePercent >= 0f
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (up) Icons.Default.ArrowCircleUp else Icons.Default.ArrowCircleDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            stringResource(
                if (up) R.string.focus_stats_change_up else R.string.focus_stats_change_down,
                FocusStats.formatPercent(changePercent)
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun FocusSummaryRow(label: String, value: String) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun FocusTaskRow(row: FocusTaskRowUi, onClick: () -> Unit) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.title.ifBlank { stringResource(R.string.focus_stats_task_free) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                buildString {
                    append(stringResource(R.string.focus_stats_task_sessions, row.completedCount))
                    if (row.archived) {
                        append(" · ")
                        append(stringResource(R.string.focus_stats_task_archived))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            FocusRepository.formatMillis(row.millis),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            )
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 每日目标预设（参考实现的 GoalPicker 简化为一排可横滑 chip） */
@Composable
internal fun FocusGoalPicker(
    current: Int,
    onPick: (Int) -> Unit
) {
    val presets = listOf(25, 50, 100, 150, 200, 300)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s)
    ) {
        presets.forEach { minutes ->
            FilterChip(
                selected = current == minutes,
                onClick = { onPick(minutes) },
                border = null,
                label = { Text("$minutes", style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
internal fun FocusSegmentedRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = Spacing.s),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}