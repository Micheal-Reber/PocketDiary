package com.example.diary.ui.habits

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.diary.data.local.Habit
import com.example.diary.data.local.MonthlyStat
import com.example.diary.data.local.YearlyStat

@Composable
internal fun StatsSummaryRow(
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

@Composable
internal fun StatisticsSection(
    statView: StatView,
    selectedYear: Int,
    habits: List<Habit>,
    monthlyStats: List<MonthlyStat>,
    yearlyStats: List<YearlyStat>,
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
