package com.example.diary.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diary.data.local.DailyStat
import com.example.diary.data.local.Habit
import com.example.diary.data.local.MonthlyStat
import com.example.diary.data.local.RecentWeeklyStat
import java.time.LocalDate
import java.time.YearMonth

@Composable
internal fun StatsSummaryRow(
    todayCount: Int,
    habitCount: Int,
    onOpenStatistics: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .clickable { onOpenStatistics() },
        color = Color.Transparent
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("统计", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "今日打卡 $todayCount/$habitCount · 点击查看统计",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Full statistics block shown inside StatisticsScreen:
 * chart (per [statView]) → period navigation/title → habit checklist that
 * toggles which lines are drawn.
 */
@Composable
internal fun StatisticsSection(
    statView: StatView,
    selectedYear: Int,
    selectedStatMonth: YearMonth,
    habits: List<Habit>,
    selectedHabitIds: Set<Long>,
    weeklyStats: List<RecentWeeklyStat>,
    monthlyStats: List<MonthlyStat>,
    dailyStats: List<DailyStat>,
    onToggleHabit: (Long) -> Unit,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    onPreviousStatMonth: () -> Unit,
    onNextStatMonth: () -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            "请勾选要查看的记录",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        if (habits.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("添加习惯后开始统计", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            when (statView) {
                StatView.WEEKLY -> {
                    // Fixed rolling window: the last 10 Monday-based weeks,
                    // bucket 10 being the current week — no navigation.
                    val byHabit = weeklyStats.groupBy { it.habitId }
                    val lines = habits.filter { it.id in selectedHabitIds }.map { habit ->
                        val group = byHabit[habit.id].orEmpty()
                        val values = (1..10).map { week ->
                            group.filter { it.weekIndex == week }.sumOf { it.count }.toFloat()
                        }
                        ChartLine("${habit.emoji} ${habit.name}", habitColor(habit.colorIndex), values)
                    }
                    val xLabels = (1..9).map { "第${it}周" } + "本周"
                    LineChart(lines, xLabels, highlightXIndex = 9)

                    Text(
                        "最近十周的数据",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                StatView.MONTHLY -> {
                    val daysInMonth = selectedStatMonth.lengthOfMonth()
                    val isCurrentMonth = selectedStatMonth == YearMonth.now()
                    val today = LocalDate.now().dayOfMonth

                    val byHabit = dailyStats.groupBy { it.habitId }
                    val lines = habits.filter { it.id in selectedHabitIds }.map { habit ->
                        val group = byHabit[habit.id].orEmpty()
                        val values = (1..daysInMonth).map { day ->
                            group.filter { it.day == day }.sumOf { it.count }.toFloat()
                        }
                        ChartLine("${habit.emoji} ${habit.name}", habitColor(habit.colorIndex), values)
                    }
                    val xLabels = (1..daysInMonth).map { "${it}日" }

                    LineChart(
                        lines, xLabels,
                        highlightXIndex = if (isCurrentMonth) today - 1 else null,
                        minStepDp = 40f
                    )

                    MonthNavigationRow(selectedStatMonth, onPreviousStatMonth, onNextStatMonth)
                }

                StatView.YEARLY -> {
                    val isCurrentYear = selectedYear == LocalDate.now().year
                    val currentMonth = LocalDate.now().monthValue

                    val byHabit = monthlyStats.groupBy { it.habitId }
                    val lines = habits.filter { it.id in selectedHabitIds }.map { habit ->
                        val group = byHabit[habit.id].orEmpty()
                        val values = (1..12).map { month ->
                            group.filter { it.month == month }.sumOf { it.count }.toFloat()
                        }
                        ChartLine("${habit.emoji} ${habit.name}", habitColor(habit.colorIndex), values)
                    }
                    val xLabels = (1..11).map { "${it}月" } + listOf(if (isCurrentYear) "本月" else "12月")

                    LineChart(
                        lines, xLabels,
                        highlightXIndex = if (isCurrentYear) currentMonth - 1 else null
                    )

                    YearNavigationRow(selectedYear, onPreviousYear, onNextYear)
                }
            }
        }

        // Period totals per habit + selection checkboxes driving the chart.
        val totalsByHabit: Map<Long, Int> = when (statView) {
            StatView.WEEKLY -> weeklyStats.groupBy { it.habitId }
                .mapValues { (_, list) -> list.sumOf { it.count } }
            StatView.MONTHLY -> dailyStats.groupBy { it.habitId }
                .mapValues { (_, list) -> list.sumOf { it.count } }
            StatView.YEARLY -> monthlyStats.groupBy { it.habitId }
                .mapValues { (_, list) -> list.sumOf { it.count } }
        }
        HabitChecklist(
            habits = habits,
            selectedHabitIds = selectedHabitIds,
            totals = totalsByHabit,
            countPrefix = when (statView) {
                StatView.WEEKLY -> ""
                StatView.MONTHLY -> "该月共"
                StatView.YEARLY -> "该年共"
            },
            onToggleHabit = onToggleHabit
        )
    }
}

@Composable
private fun HabitChecklist(
    habits: List<Habit>,
    selectedHabitIds: Set<Long>,
    totals: Map<Long, Int>,
    countPrefix: String,
    onToggleHabit: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        habits.chunked(2).forEach { rowHabits ->
            Row(Modifier.fillMaxWidth()) {
                rowHabits.forEach { habit ->
                    Row(
                        Modifier.weight(1f).padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = habit.id in selectedHabitIds,
                            onCheckedChange = { onToggleHabit(habit.id) },
                            colors = CheckboxDefaults.colors(checkedColor = habitColor(habit.colorIndex))
                        )
                        Text(
                            "${habit.emoji} ${habit.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "$countPrefix${totals[habit.id] ?: 0}天",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (rowHabits.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun YearNavigationRow(year: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ChevronLeft, "上一年", modifier = Modifier.size(22.dp))
        }
        Text("${year}年", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ChevronRight, "下一年", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun MonthNavigationRow(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ChevronLeft, "上一月", modifier = Modifier.size(22.dp))
        }
        Text(
            "${month.year}年${month.monthValue}月",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.ChevronRight, "下一月", modifier = Modifier.size(22.dp))
        }
    }
}
