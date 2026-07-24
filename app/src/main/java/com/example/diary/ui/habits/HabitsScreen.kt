package com.example.diary.ui.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diary.data.repository.HabitRepository

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    habitRepository: HabitRepository,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModelFactory(habitRepository))
) {
    val habits by viewModel.habits.collectAsState()
    val statView by viewModel.statView.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val showCheckInDialog by viewModel.showCheckInDialog.collectAsState()
    val showAddHabitDialog by viewModel.showAddHabitDialog.collectAsState()
    val showManageHabits by viewModel.showManageHabits.collectAsState()
    val showStats by viewModel.showStats.collectAsState()
    val dateCheckIns by viewModel.dateCheckIns.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val monthlyStats by viewModel.monthlyStats.collectAsState()
    val yearlyStats by viewModel.yearlyStats.collectAsState()
    val calendarCheckInDates by viewModel.calendarCheckInDates.collectAsState()
    val todayCheckInCount by viewModel.todayCheckInCount.collectAsState()
    val firstHabit = habits.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日历", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.showManageHabits() }) {
                        Icon(Icons.Default.Edit, "管理习惯")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddHabitDialog() },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, "添加习惯", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                StatsSummaryRow(
                    todayCount = todayCheckInCount,
                    habitCount = habits.size,
                    showStats = showStats,
                    onToggle = { viewModel.toggleStats() }
                )
            }

            item {
                AnimatedVisibility(
                    visible = showStats,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    StatisticsSection(
                        statView = statView,
                        selectedYear = selectedYear,
                        habits = habits,
                        monthlyStats = monthlyStats,
                        yearlyStats = yearlyStats,
                        onToggleView = { viewModel.toggleStatView() },
                        onPreviousYear = { viewModel.previousYear() },
                        onNextYear = { viewModel.nextYear() }
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            item {
                MonthHeader(
                    month = currentMonth,
                    onPrevious = { viewModel.previousMonth() },
                    onNext = { viewModel.nextMonth() }
                )
            }

            item { WeekdayHeader() }

            item {
                CalendarGrid(
                    month = currentMonth,
                    firstHabit = firstHabit,
                    checkInDates = calendarCheckInDates,
                    onDateClick = { date ->
                        if (!viewModel.isFutureDate(date)) {
                            viewModel.onDateClick(date)
                        }
                    },
                    isFutureDate = { viewModel.isFutureDate(it) }
                )
                Spacer(Modifier.height(8.dp))
                if (firstHabit != null) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(habitColor(firstHabit.colorIndex))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "= ${firstHabit.emoji} ${firstHabit.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${calendarCheckInDates.size}天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showCheckInDialog) {
        CheckInDialog(
            date = selectedDate,
            habits = habits,
            checkedMap = dateCheckIns,
            onToggle = { habitId -> viewModel.toggleHabitOnDate(habitId, selectedDate.toString()) },
            onDismiss = { viewModel.dismissCheckInDialog() }
        )
    }

    if (showAddHabitDialog) {
        AddHabitDialog(
            onDismiss = { viewModel.dismissAddHabitDialog() },
            onConfirm = { name, emoji -> viewModel.addHabit(name, emoji) }
        )
    }

    if (showManageHabits) {
        ManageHabitsDialog(
            habits = habits,
            onDelete = { viewModel.deleteHabit(it) },
            onDismiss = { viewModel.dismissManageHabits() }
        )
    }
}
