package com.example.diary.ui.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.diary.data.local.Habit
import com.example.diary.data.repository.HabitRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
            // Stats summary row (always visible, tap to expand)
            item {
                StatsSummaryRow(
                    todayCount = todayCheckInCount,
                    habitCount = habits.size,
                    showStats = showStats,
                    onToggle = { viewModel.toggleStats() }
                )
            }

            // Expandable statistics chart
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

            // Month header
            item {
                MonthHeader(
                    month = currentMonth,
                    onPrevious = { viewModel.previousMonth() },
                    onNext = { viewModel.nextMonth() }
                )
            }

            // Weekday headers
            item { WeekdayHeader() }

            // Calendar grid with first habit check-in dots
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

    // Check-in dialog
    if (showCheckInDialog) {
        CheckInDialog(
            date = selectedDate,
            habits = habits,
            checkedMap = dateCheckIns,
            onToggle = { habitId -> viewModel.toggleHabitOnDate(habitId, selectedDate.toString()) },
            onDismiss = { viewModel.dismissCheckInDialog() }
        )
    }

    // Add habit dialog
    if (showAddHabitDialog) {
        AddHabitDialog(
            onDismiss = { viewModel.dismissAddHabitDialog() },
            onConfirm = { name, emoji -> viewModel.addHabit(name, emoji) }
        )
    }

    // Manage habits dialog
    if (showManageHabits) {
        ManageHabitsDialog(
            habits = habits,
            onDelete = { viewModel.deleteHabit(it) },
            onDismiss = { viewModel.dismissManageHabits() }
        )
    }
}

// ── Stats Summary Row ──

@Composable
private fun StatsSummaryRow(
    todayCount: Int,
    habitCount: Int,
    showStats: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "今日打卡 $todayCount/$habitCount · 点击${if (showStats) "收起" else "展开"}图表",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (showStats) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ── Statistics Section ──

@Composable
private fun StatisticsSection(
    statView: StatView,
    selectedYear: Int,
    habits: List<Habit>,
    monthlyStats: List<com.example.diary.data.local.MonthlyStat>,
    yearlyStats: List<com.example.diary.data.local.YearlyStat>,
    onToggleView: () -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (statView == StatView.MONTHLY) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPreviousYear, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ChevronLeft, "上一年", modifier = Modifier.size(18.dp))
                    }
                    Text("${selectedYear}年", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onNextYear, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.ChevronRight, "下一年", modifier = Modifier.size(18.dp))
                    }
                }
            } else {
                Text("全部年份", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Row {
                FilterChip(
                    selected = statView == StatView.MONTHLY,
                    onClick = { if (statView != StatView.MONTHLY) onToggleView() },
                    label = { Text("月") },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                FilterChip(
                    selected = statView == StatView.YEARLY,
                    onClick = { if (statView != StatView.YEARLY) onToggleView() },
                    label = { Text("年") },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (habits.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("添加习惯后开始统计", color = MaterialTheme.colorScheme.outline)
            }
        } else if (statView == StatView.MONTHLY) {
            val allValues = monthlyStats.groupBy { it.habitId }
            val maxVal = monthlyStats.maxOfOrNull { it.count }?.toFloat()?.coerceAtLeast(1f) ?: 1f
            val chartLines = habits.map { habit ->
                val group = allValues[habit.id] ?: emptyList()
                val values = (1..12).map { month ->
                    group.filter { it.month == month }.sumOf { it.count }.toFloat()
                }
                ChartLine("${habit.emoji} ${habit.name}", habitColor(habit.colorIndex), values)
            }
            LineChart(chartLines, (1..12).map { "${it}月" }, maxY = maxVal * 1.15f)
        } else {
            val allValues = yearlyStats.groupBy { it.habitId }
            val years = yearlyStats.map { it.year }.distinct().sorted()
            val maxVal = yearlyStats.maxOfOrNull { it.count }?.toFloat()?.coerceAtLeast(1f) ?: 1f
            if (years.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("暂无年度数据", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                val chartLines = habits.map { habit ->
                    val group = allValues[habit.id] ?: emptyList()
                    val values = years.map { year ->
                        group.filter { it.year == year }.sumOf { it.count }.toFloat()
                    }
                    ChartLine("${habit.emoji} ${habit.name}", habitColor(habit.colorIndex), values)
                }
                LineChart(chartLines, years.map { "${it}年" }, maxY = maxVal * 1.15f)
            }
        }
    }
}

// ── Calendar ──

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) { Icon(Icons.Default.ChevronLeft, "上一月") }
        Text("${month.year}年 ${month.monthValue}月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "下一月") }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
            Text(
                day.getDisplayName(TextStyle.SHORT, Locale.CHINA),
                Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    firstHabit: Habit?,
    checkInDates: Set<LocalDate>,
    onDateClick: (LocalDate) -> Unit,
    isFutureDate: (LocalDate) -> Boolean
) {
    val first = month.atDay(1)
    val days = month.lengthOfMonth()
    val start = (first.dayOfWeek.value - 1)
    val rows = (start + days + 6) / 7
    val today = LocalDate.now()

    Column(Modifier.padding(horizontal = 4.dp)) {
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val day = row * 7 + col - start + 1
                    if (day in 1..days) {
                        val date = month.atDay(day)
                        val isToday = date == today
                        val isFuture = isFutureDate(date)
                        val isChecked = date in checkInDates
                        val dotColor = if (firstHabit != null) habitColor(firstHabit.colorIndex) else MaterialTheme.colorScheme.primary

                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    else Modifier
                                )
                                .then(if (isFuture) Modifier else Modifier.clickable { onDateClick(date) }),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$day",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isFuture)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                if (isChecked && !isFuture) {
                                    Box(
                                        Modifier.size(6.dp).clip(CircleShape).background(dotColor)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

// ── Check-In Dialog ──

@Composable
private fun CheckInDialog(
    date: LocalDate,
    habits: List<Habit>,
    checkedMap: Map<Long, Boolean>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}月${date.dayOfMonth}日 打卡") },
        text = {
            if (habits.isEmpty()) {
                Text("还没有习惯，请先添加", color = MaterialTheme.colorScheme.outline)
            } else {
                Column {
                    habits.forEach { habit ->
                        val checked = checkedMap[habit.id] == true
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(habit.id) }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggle(habit.id) },
                                colors = CheckboxDefaults.colors(checkedColor = habitColor(habit.colorIndex))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${habit.emoji} ${habit.name}", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

// ── Add Habit Dialog (custom emoji) ──

@Composable
private fun AddHabitDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("✅") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加习惯") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("习惯名称") },
                    placeholder = { Text("如：早起、跑步、喝水") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = emoji, onValueChange = { if (it.length <= 2) emoji = it.ifEmpty { "✅" } },
                    label = { Text("图标 (emoji)") },
                    modifier = Modifier.width(120.dp), singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, emoji) }, enabled = name.isNotBlank()) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── Manage Habits Dialog ──

@Composable
private fun ManageHabitsDialog(
    habits: List<Habit>,
    onDelete: (Habit) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理习惯") },
        text = {
            if (habits.isEmpty()) {
                Text("还没有习惯", color = MaterialTheme.colorScheme.outline)
            } else {
                Column {
                    habits.forEach { habit ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${habit.emoji} ${habit.name}", style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { onDelete(habit) }) {
                                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
