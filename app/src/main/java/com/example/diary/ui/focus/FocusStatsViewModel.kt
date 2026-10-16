package com.example.diary.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diary.data.focus.FocusRange
import com.example.diary.data.focus.FocusStats
import com.example.diary.data.preferences.FocusPreferences
import com.example.diary.data.repository.FocusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class FocusDayCell(val date: LocalDate, val millis: Long, val level: Int)

data class FocusBucketUi(val index: Int, val millis: Long)

data class FocusPeriodUi(
    val range: FocusRange,
    val totalMillis: Long,
    val sessionCount: Int,
    val completedCount: Int,
    val averageMillis: Long,
    val changePercent: Float?,
    val buckets: List<FocusBucketUi>
)

data class FocusTaskRowUi(
    val taskId: Long?,
    val title: String,
    val millis: Long,
    val completedCount: Int,
    val archived: Boolean
)

data class FocusStatsUiState(
    val todayMillis: Long = 0L,
    val todayCompleted: Int = 0,
    val goalMinutes: Int = FocusPreferences.DEFAULT_DAILY_GOAL_MINUTES,
    val recentDays: List<FocusDayCell> = emptyList(),
    val heatmap: List<FocusDayCell> = emptyList(),
    val period: FocusPeriodUi? = null,
    val tasks: List<FocusTaskRowUi> = emptyList(),
    val loaded: Boolean = false
)

class FocusStatsViewModel(
    private val repository: FocusRepository,
    private val focusPreferences: FocusPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusStatsUiState())
    val uiState: StateFlow<FocusStatsUiState> = _uiState.asStateFlow()

    private val range = MutableStateFlow(FocusRange.WEEK)
    private val loadMutex = Mutex()

    init {
        reload()
    }

    fun setRange(next: FocusRange) {
        if (range.value == next) return
        range.value = next
        reload()
    }

    fun reload() {
        viewModelScope.launch { loadMutex.withLock { load() } }
    }

    fun setGoalMinutes(minutes: Int) {
        viewModelScope.launch {
            focusPreferences.setDailyGoalMinutes(minutes)
            loadMutex.withLock { load() }
        }
    }

    private suspend fun load() {
        val today = LocalDate.now()
        val current = range.value
        val goal = focusPreferences.dailyGoalMinutes.first()
        val totals = repository.focusMillisBetween(FocusStats.queryStart(current, today), today)

        val heatStart = FocusStats.heatmapStart(today)
        val heatMapValues = buildList {
            var date = heatStart
            while (!date.isAfter(today)) {
                add(totals[date] ?: 0L)
                date = date.plusDays(1)
            }
        }
        val heatMax = heatMapValues.maxOrNull() ?: 0L
        val heatCells = heatMapValues.mapIndexed { index, millis ->
            FocusDayCell(heatStart.plusDays(index.toLong()), millis, FocusStats.heatLevel(millis, heatMax))
        }

        val recentCells = (0 until FocusStats.RECENT_DAYS).map { offset ->
            val date = today.minusDays((FocusStats.RECENT_DAYS - 1 - offset).toLong())
            val millis = totals[date] ?: 0L
            FocusDayCell(date, millis, FocusStats.heatLevel(millis, goal * 60_000L))
        }

        val todayStat = repository.focusStatBetween(today, today)
        val curStat = repository.focusStatBetween(
            FocusStats.currentStart(current, today), FocusStats.currentEnd(current, today)
        )
        val prevStat = repository.focusStatBetween(
            FocusStats.previousStart(current, today), FocusStats.previousEnd(current, today)
        )

        val bucketCount = FocusStats.bucketCount(current, today)
        val average = FocusStats.averagePerBucket(curStat.totalMillis, bucketCount)
        val prevAverage = FocusStats.averagePerBucket(prevStat.totalMillis, bucketCount)

        _uiState.value = FocusStatsUiState(
            todayMillis = todayStat.totalMillis,
            todayCompleted = todayStat.completedCount,
            goalMinutes = goal,
            recentDays = recentCells,
            heatmap = heatCells,
            period = FocusPeriodUi(
                range = current,
                totalMillis = curStat.totalMillis,
                sessionCount = curStat.sessionCount,
                completedCount = curStat.completedCount,
                averageMillis = average,
                changePercent = FocusStats.changePercent(average, prevAverage),
                buckets = buildBuckets(current, today, totals)
            ),
            tasks = loadTaskRows(),
            loaded = true
        )
    }

    private fun buildBuckets(
        current: FocusRange,
        today: LocalDate,
        totals: Map<LocalDate, Long>
    ): List<FocusBucketUi> = when (current) {
        FocusRange.WEEK -> (1..FocusStats.RECENT_DAYS).map { slot ->
            val date = today.minusDays((FocusStats.RECENT_DAYS - slot).toLong())
            FocusBucketUi(slot, totals[date] ?: 0L)
        }
        FocusRange.MONTH -> (1..today.dayOfMonth).map { day ->
            FocusBucketUi(day, totals[today.withDayOfMonth(day)] ?: 0L)
        }
        FocusRange.YEAR -> (1..12).map { month ->
            val sum = totals.entries
                .filter { it.key.year == today.year && it.key.monthValue == month }
                .sumOf { it.value }
            FocusBucketUi(month, sum)
        }
    }

    private suspend fun loadTaskRows(): List<FocusTaskRowUi> {
        val totals = repository.focusTaskTotalsSince(LocalDate.of(1970, 1, 1))
        val tasks = repository.getAllTasks().associateBy { it.id }
        return totals.map { row ->
            val task = row.taskId?.let { tasks[it] }
            FocusTaskRowUi(
                taskId = row.taskId,
                title = task?.title.orEmpty(),
                millis = row.total,
                completedCount = row.completedCount,
                archived = task?.archived ?: false
            )
        }.sortedByDescending { it.millis }
    }
}

class FocusStatsViewModelFactory(
    private val repository: FocusRepository,
    private val focusPreferences: FocusPreferences
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FocusStatsViewModel::class.java)) {
            return FocusStatsViewModel(repository, focusPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}