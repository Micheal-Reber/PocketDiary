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
    val weeklyStats by habitsViewModel.recentWeeklyStats.collectAsState()
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
                // Segmented pill tabs centered in the app bar, per the
                // reference design.
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SegmentedStatTabs(
                            current = statView,
                            onSelect = { habitsViewModel.setStatView(it) }
                        )
                    }
                },
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
            StatisticsSection(
                statView = statView,
                selectedYear = selectedYear,
                selectedStatMonth = selectedStatMonth,
                habits = habits,
                selectedHabitIds = selectedHabitIds,
                weeklyStats = weeklyStats,
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
    Surface(
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    ) {
        Row {
            entries.forEach { (view, label) ->
                val selected = view == current
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelect(view) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
