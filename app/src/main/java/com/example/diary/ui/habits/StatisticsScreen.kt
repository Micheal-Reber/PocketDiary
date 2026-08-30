package com.example.diary.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    habitsViewModel: HabitsViewModel,
    onBack: () -> Unit
) {
    val habits by habitsViewModel.habits.collectAsState()
    val statView by habitsViewModel.statView.collectAsState()
    val selectedYear by habitsViewModel.selectedYear.collectAsState()
    val selectedStatMonth by habitsViewModel.selectedStatMonth.collectAsState()
    val selectedHabitIds by habitsViewModel.selectedHabitIds.collectAsState()
    val recentWeeklyStats by habitsViewModel.recentWeeklyStats.collectAsState()
    val monthlyStats by habitsViewModel.monthlyStats.collectAsState()
    val dailyStats by habitsViewModel.dailyStats.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                title = { },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // 顶栏下方单独一行：分段选择器
            Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                SegmentedStatTabs(
                    current = statView,
                    onSelect = { habitsViewModel.setStatView(it) }
                )
            }
            // 视觉居中：顶部适度下垫，避免贴顶
            Spacer(Modifier.height(40.dp))
            StatisticsSection(
                statView = statView,
                selectedYear = selectedYear,
                selectedStatMonth = selectedStatMonth,
                habits = habits,
                selectedHabitIds = selectedHabitIds,
                recentWeeklyStats = recentWeeklyStats,
                monthlyStats = monthlyStats,
                dailyStats = dailyStats,
                onToggleHabit = { habitsViewModel.toggleHabitSelected(it) },
                onPreviousYear = { habitsViewModel.previousYear() },
                onNextYear = { habitsViewModel.nextYear() },
                onPreviousStatMonth = { habitsViewModel.previousStatMonth() },
                onNextStatMonth = { habitsViewModel.nextStatMonth() }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SegmentedStatTabs(current: StatView, onSelect: (StatView) -> Unit) {
    val entries = listOf(
        StatView.WEEKLY to "周频率",
        StatView.MONTHLY to "月视图",
        StatView.YEARLY to "年视图"
    )
    // Official M3 segmented control — selected segment gets the expressive
    // shape-morph animation for free.
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        entries.forEachIndexed { index, (view, label) ->
            SegmentedButton(
                selected = view == current,
                onClick = { onSelect(view) },
                shape = SegmentedButtonDefaults.itemShape(index, entries.size)
            ) {
                Text(label)
            }
        }
    }
}
