package com.example.diary.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diary.data.local.DailyStat
import com.example.diary.data.local.Habit
import com.example.diary.data.local.MonthlyStat
import com.example.diary.data.local.RecentWeeklyStat
import com.example.diary.data.local.YearlyStat
import com.example.diary.data.repository.HabitRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth

enum class StatView { WEEKLY, MONTHLY, YEARLY }

class HabitsViewModel(private val habitRepository: HabitRepository) : ViewModel() {

    val habits: StateFlow<List<Habit>> = habitRepository.getActiveHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Statistics
    private val _statView = MutableStateFlow(StatView.WEEKLY)
    val statView: StateFlow<StatView> = _statView.asStateFlow()

    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _selectedStatMonth = MutableStateFlow(YearMonth.now())
    val selectedStatMonth: StateFlow<YearMonth> = _selectedStatMonth.asStateFlow()

    // Which habits' lines are drawn on the statistics chart. Seeded to "all"
    // once the first non-empty habit list arrives; new habits auto-select.
    private val _selectedHabitIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedHabitIds: StateFlow<Set<Long>> = _selectedHabitIds.asStateFlow()

    private var selectionSeeded = false

    // Calendar
    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // Per-habit check-in dates (shown as colored dots on calendar, max 3 per day)
    private val _calendarCheckInDates = MutableStateFlow<Map<Long, Set<LocalDate>>>(emptyMap())
    val calendarCheckInDates: StateFlow<Map<Long, Set<LocalDate>>> = _calendarCheckInDates.asStateFlow()

    // Today's check-in summary for the stats summary row
    private val _todayCheckInCount = MutableStateFlow(0)
    val todayCheckInCount: StateFlow<Int> = _todayCheckInCount.asStateFlow()

    // Dialogs
    private val _showCheckInDialog = MutableStateFlow(false)
    val showCheckInDialog: StateFlow<Boolean> = _showCheckInDialog.asStateFlow()

    private val _showAddHabitDialog = MutableStateFlow(false)
    val showAddHabitDialog: StateFlow<Boolean> = _showAddHabitDialog.asStateFlow()

    private val _showManageHabits = MutableStateFlow(false)
    val showManageHabits: StateFlow<Boolean> = _showManageHabits.asStateFlow()

    private val _dateCheckIns = MutableStateFlow<Map<Long, Boolean>>(emptyMap())
    val dateCheckIns: StateFlow<Map<Long, Boolean>> = _dateCheckIns.asStateFlow()

    // Stats data
    private val _monthlyStats = MutableStateFlow<List<MonthlyStat>>(emptyList())
    val monthlyStats: StateFlow<List<MonthlyStat>> = _monthlyStats.asStateFlow()

    private val _yearlyStats = MutableStateFlow<List<YearlyStat>>(emptyList())
    val yearlyStats: StateFlow<List<YearlyStat>> = _yearlyStats.asStateFlow()

    private val _recentWeeklyStats = MutableStateFlow<List<RecentWeeklyStat>>(emptyList())
    val recentWeeklyStats: StateFlow<List<RecentWeeklyStat>> = _recentWeeklyStats.asStateFlow()

    private val _dailyStats = MutableStateFlow<List<DailyStat>>(emptyList())
    val dailyStats: StateFlow<List<DailyStat>> = _dailyStats.asStateFlow()

    // Serializes loadStats/loadCalendarCheckIns/loadTodayCount so a slow
    // first call can't have its result clobbered by a faster second call.
    // Without this, viewModelScope.launch { block() } kicks off every call
    // in parallel and the order of StateFlow writes is whatever the IO
    // scheduler picks — not whatever order the user invoked them in.
    private val loadMutex = Mutex()

    init {
        loadAllData()
        // habits is a StateFlow seeded with emptyList() — without this watcher,
        // the first emission of the underlying Room flow lands after init()'s
        // loadCalendarCheckIns() runs, so the calendar dots never paint even
        // though the user has habits. Drop the empty seed and refresh whenever
        // the list goes from empty to non-empty (or any time the first habit
        // changes identity).
        viewModelScope.launch {
            habits.collect { list ->
                if (!selectionSeeded && list.isNotEmpty()) {
                    _selectedHabitIds.value = list.map { it.id }.toSet()
                    selectionSeeded = true
                }
                if (list.isNotEmpty()) {
                    loadCalendarCheckIns()
                }
            }
        }
    }

    fun setStatView(view: StatView) { _statView.value = view }

    // Each stats query only depends on specific inputs — reload just what
    // changed instead of all four datasets on every interaction:
    //   monthlyStats  ← selectedYear      (year navigation)
    //   dailyStats    ← selectedStatMonth (month navigation)
    //   recentWeekly  ← "now"             (record changes)
    //   yearlyStats   ← record changes
    fun previousYear() { _selectedYear.value = _selectedYear.value - 1; loadMonthlyStats() }
    fun nextYear() { _selectedYear.value = _selectedYear.value + 1; loadMonthlyStats() }

    fun previousStatMonth() { _selectedStatMonth.value = _selectedStatMonth.value.minusMonths(1); loadDailyStats() }
    fun nextStatMonth() { _selectedStatMonth.value = _selectedStatMonth.value.plusMonths(1); loadDailyStats() }

    fun toggleHabitSelected(habitId: Long) {
        _selectedHabitIds.value = if (habitId in _selectedHabitIds.value) {
            _selectedHabitIds.value - habitId
        } else {
            _selectedHabitIds.value + habitId
        }
    }

    fun previousMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
        loadCalendarCheckIns()
    }

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
        loadCalendarCheckIns()
    }

    fun onDateClick(date: LocalDate) {
        _selectedDate.value = date
        viewModelScope.launch {
            val records = habitRepository.getRecordsForDate(date.toString())
            val map = mutableMapOf<Long, Boolean>()
            records.forEach { map[it.habitId] = true }
            _dateCheckIns.value = map
            _showCheckInDialog.value = true
        }
    }

    fun dismissCheckInDialog() { _showCheckInDialog.value = false }

    fun toggleHabitOnDate(habitId: Long, date: String) {
        viewModelScope.launch {
            habitRepository.toggleCheckIn(habitId, date)
            // Reflect the new state in the dialog's local map. toggleCheckIn has
            // no return value, so we infer the next state by reading the DB.
            val nowChecked = habitRepository.getRecordsForDate(date).any { it.habitId == habitId }
            val current = _dateCheckIns.value.toMutableMap()
            if (nowChecked) current[habitId] = true else current.remove(habitId)
            _dateCheckIns.value = current
            // Only refresh the derived views that actually depend on records —
            // calendar dots, all four chart datasets (counts changed), and
            // today's summary. Avoiding the calendar reload keeps the check-in
            // dialog from re-rendering on every checkbox tap.
            loadCalendarCheckIns()
            loadAllStats()
            loadTodayCount()
        }
    }

    fun showAddHabitDialog() { _showAddHabitDialog.value = true }
    fun dismissAddHabitDialog() { _showAddHabitDialog.value = false }

    fun addHabit(name: String, emoji: String) {
        viewModelScope.launch {
            // Reuse colors modulo the palette size so adding more than 10 habits
            // doesn't silently collide on the same color (the old "max + 1"
            // approach did because habitColor() does modulo at render time).
            val colorIndex = habits.value.size % HabitColorPalette.size
            val newId = habitRepository.addHabit(name, emoji, colorIndex)
            // New habits appear on the chart immediately.
            _selectedHabitIds.value = _selectedHabitIds.value + newId
            loadAllStats()
            _showAddHabitDialog.value = false
        }
    }

    fun showManageHabits() { _showManageHabits.value = true }
    fun dismissManageHabits() { _showManageHabits.value = false }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habit.id)
            _selectedHabitIds.value = _selectedHabitIds.value - habit.id
            loadAllData()
        }
    }

    private fun loadAllData() {
        loadAllStats()
        loadCalendarCheckIns()
        loadTodayCount()
    }

    /** Re-reads every chart dataset — used when record data itself changed. */
    private fun loadAllStats() = launchSerialized {
        _monthlyStats.value = habitRepository.getMonthlyStats(_selectedYear.value)
        _yearlyStats.value = habitRepository.getYearlyStats()
        _recentWeeklyStats.value = habitRepository.getRecentWeeklyStats()
        _dailyStats.value = habitRepository.getDailyStats(_selectedStatMonth.value.toString())
    }

    private fun loadMonthlyStats() = launchSerialized {
        _monthlyStats.value = habitRepository.getMonthlyStats(_selectedYear.value)
    }

    private fun loadDailyStats() = launchSerialized {
        _dailyStats.value = habitRepository.getDailyStats(_selectedStatMonth.value.toString())
    }

    private fun loadCalendarCheckIns() = launchSerialized {
        val prefix = _currentMonth.value.toString() // yyyy-MM
        val allHabits = habits.value
        if (allHabits.isNotEmpty()) {
            val map = mutableMapOf<Long, Set<LocalDate>>()
            for (habit in allHabits) {
                val dates = habitRepository.getCheckInDates(habit.id, prefix)
                map[habit.id] = dates.map { LocalDate.parse(it) }.toSet()
            }
            _calendarCheckInDates.value = map
        } else {
            _calendarCheckInDates.value = emptyMap()
        }
    }

    private fun loadTodayCount() = launchSerialized {
        val records = habitRepository.getRecordsForDate(LocalDate.now().toString())
        _todayCheckInCount.value = records.size
    }

    private fun launchSerialized(block: suspend () -> Unit) =
        viewModelScope.launch { loadMutex.withLock { block() } }

    fun isFutureDate(date: LocalDate): Boolean = date.isAfter(LocalDate.now())
}

class HabitsViewModelFactory(
    private val habitRepository: HabitRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitsViewModel::class.java)) {
            return HabitsViewModel(habitRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
