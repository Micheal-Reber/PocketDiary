package com.example.diary.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diary.R
import com.example.diary.data.repository.HabitRepository
import com.example.diary.ui.navigation.BottomBarContentInset
import com.example.diary.ui.navigation.GlassFab
import com.example.diary.ui.navigation.rememberGlassBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    habitRepository: HabitRepository,
    onOpenStatistics: () -> Unit,
    viewModel: HabitsViewModel = viewModel(factory = HabitsViewModelFactory(habitRepository))
) {
    val habits by viewModel.habits.collectAsState()
    val currentMonth by viewModel.currentMonth.collectAsState()
    val showCheckInDialog by viewModel.showCheckInDialog.collectAsState()
    val showAddHabitDialog by viewModel.showAddHabitDialog.collectAsState()
    val showManageHabits by viewModel.showManageHabits.collectAsState()
    val dateCheckIns by viewModel.dateCheckIns.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val calendarCheckInDates by viewModel.calendarCheckInDates.collectAsState()
    val todayCheckInCount by viewModel.todayCheckInCount.collectAsState()

    val listBackdrop = rememberGlassBackdrop()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_calendar), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.showManageHabits() }) {
                        Icon(Icons.Default.Edit, stringResource(R.string.habit_manage_title))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            GlassFab(
                onClick = { viewModel.showAddHabitDialog() },
                backdrop = listBackdrop,
                modifier = Modifier.padding(bottom = BottomBarContentInset)
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.habit_add_title), tint = MaterialTheme.colorScheme.primary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).layerBackdrop(listBackdrop),
            contentPadding = PaddingValues(bottom = BottomBarContentInset)
        ) {
            item {
                StatsSummaryRow(
                    todayCount = todayCheckInCount,
                    habitCount = habits.size,
                    onOpenStatistics = onOpenStatistics
                )
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
                    habits = habits,
                    checkInsByHabit = calendarCheckInDates,
                    onDateClick = { date ->
                        if (!viewModel.isFutureDate(date)) {
                            viewModel.onDateClick(date)
                        }
                    },
                    isFutureDate = { viewModel.isFutureDate(it) }
                )
                Spacer(Modifier.height(8.dp))
                // Legend: every habit with its color dot and this month's
                // check-in day count, stacked vertically.
                if (habits.isNotEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        habits.forEach { habit ->
                            val days = calendarCheckInDates[habit.id]?.size ?: 0
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape)
                                        .background(habitColor(habit.colorIndex))
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.habit_days_count, "${habit.emoji} ${habit.name}", days),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
