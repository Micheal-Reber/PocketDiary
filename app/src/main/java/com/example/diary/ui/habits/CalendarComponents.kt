package com.example.diary.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.diary.data.local.Habit
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
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
internal fun WeekdayHeader() {
    // Always render Monday-first (the user-facing choice this app committed
    // to) but localize the abbreviation so a system set to English shows "Mon"
    // not "周一". Locale.getDefault() follows the user's system locale, which
    // is the right default for a personal diary.
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
            Text(
                day.getDisplayName(TextStyle.SHORT, locale),
                Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun CalendarGrid(
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
                        val dotColor = habitColorOrPrimary(firstHabit)

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

private fun habitColorOrPrimary(firstHabit: Habit?): Color =
    if (firstHabit != null) habitColor(firstHabit.colorIndex) else MaterialTheme.colorScheme.primary
