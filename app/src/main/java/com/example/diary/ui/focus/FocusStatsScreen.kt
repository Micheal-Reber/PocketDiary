package com.example.diary.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diary.R
import com.example.diary.data.focus.FocusRange
import com.example.diary.data.focus.FocusStats
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.repository.FocusRepository
import com.example.diary.ui.theme.Spacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 专注统计（学习模式下的二级页）：今日目标表盘 + 近 7 天 + 热力图 + 期间趋势 + 事项分布。
 * 与打卡日历/习惯统计彻底无关，只读 focus_sessions。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusStatsScreen(
    repository: FocusRepository,
    focusPreferences: FocusPreferences,
    onBack: () -> Unit,
    onOpenTaskFocus: (Long?) -> Unit
) {
    val viewModel: FocusStatsViewModel =
        viewModel(factory = FocusStatsViewModelFactory(repository, focusPreferences))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.reload() }

    var showGoalPicker by remember { mutableStateOf(false) }
    var showGoalInput by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                    }
                },
                title = { Text(stringResource(R.string.focus_stats_title), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.l, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            FocusStatsCard(title = stringResource(R.string.focus_stats_today_goal)) {
                FocusGoalGauge(
                    millis = state.todayMillis,
                    goalMinutes = state.goalMinutes,
                    completedSessions = state.todayCompleted,
                    onEditGoal = { showGoalPicker = !showGoalPicker }
                )
                if (showGoalPicker) {
                    FocusGoalPicker(
                        current = state.goalMinutes,
                        onPick = { minutes ->
                            showGoalPicker = false
                            viewModel.setGoalMinutes(minutes)
                        },
                        onCustom = {
                            showGoalPicker = false
                            showGoalInput = true
                        }
                    )
                }
                Text(
                    stringResource(R.string.focus_stats_recent),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FocusRecentDaysRow(state.recentDays, state.goalMinutes)
                Text(
                    stringResource(R.string.focus_stats_heatmap_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FocusHeatmap(state.heatmap)
            }

            state.period?.let { period ->
                FocusStatsCard(title = stringResource(R.string.focus_stats_duration)) {
                    FocusSegmentedRow(
                        labels = listOf(
                            stringResource(R.string.focus_stats_range_week),
                            stringResource(R.string.focus_stats_range_month),
                            stringResource(R.string.focus_stats_range_year)
                        ),
                        selectedIndex = FocusRange.entries.indexOf(period.range),
                        onSelect = { index -> viewModel.setRange(FocusRange.entries[index]) }
                    )
Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m)
                ) {
                    Text(
                        FocusRepository.formatMillis(period.averageMillis),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Normal,
                            letterSpacing = 0.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    period.changePercent?.let { FocusChangeText(it) }
                }
                    Text(
                        periodLabel(period.range),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FocusBarChart(
                        buckets = period.buckets,
                        labels = bucketLabels(period.range),
                        highlightLast = true
                    )
                    FocusSummaryRow(
                        label = stringResource(R.string.focus_stats_summary_total),
                        value = FocusRepository.formatMillis(period.totalMillis)
                    )
                    FocusSummaryRow(
                        label = stringResource(R.string.focus_stats_summary_completed),
                        value = period.completedCount.toString()
                    )
                    FocusSummaryRow(
                        label = stringResource(R.string.focus_stats_summary_avg_pom),
                        value = FocusRepository.formatMillis(
                            if (period.completedCount > 0) period.totalMillis / period.completedCount else 0L
                        )
                    )
                }
            }

            FocusStatsCard(title = stringResource(R.string.focus_stats_tasks)) {
                if (state.tasks.isEmpty()) {
                    Text(
                        stringResource(R.string.focus_stats_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = Spacing.m)
                    )
                }
                state.tasks.forEach { row ->
                    FocusTaskRow(row = row) { onOpenTaskFocus(row.taskId) }
                }
            }

            Spacer(Modifier.height(Spacing.l))
        }
    }

    if (showGoalInput) {
        FocusGoalInputDialog(
            current = state.goalMinutes,
            onConfirm = { minutes ->
                showGoalInput = false
                viewModel.setGoalMinutes(minutes)
            },
            onDismiss = { showGoalInput = false }
        )
    }
}

private val MonthDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

@Composable
private fun periodLabel(range: FocusRange): String {
    val today = LocalDate.now()
    return when (range) {
        FocusRange.WEEK -> stringResource(
            R.string.focus_stats_period_week,
            FocusStats.currentStart(range, today).format(MonthDayFormatter),
            today.format(MonthDayFormatter)
        )
        FocusRange.MONTH -> stringResource(R.string.focus_stats_period_month, today.year, today.monthValue)
        FocusRange.YEAR -> stringResource(R.string.focus_stats_period_year, today.year)
    }
}

@Composable
private fun bucketLabels(range: FocusRange): List<String> {
    val today = LocalDate.now()
    return when (range) {
        FocusRange.WEEK -> {
            val headers = stringArrayResource(R.array.weekday_headers)
            (1..FocusStats.RECENT_DAYS).map { slot ->
                val date = today.minusDays((FocusStats.RECENT_DAYS - slot).toLong())
                headers[date.dayOfWeek.value - 1]
            }
        }
        FocusRange.MONTH -> (1..today.dayOfMonth).map { it.toString() }
        FocusRange.YEAR -> (1..12).map { "${it}月" }
    }
}