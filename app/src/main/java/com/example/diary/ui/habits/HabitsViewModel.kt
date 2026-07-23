package com.example.diary.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diary.data.local.Habit
import com.example.diary.data.local.MonthlyStat
import com.example.diary.data.local.YearlyStat
import com.example.diary.data.repository.HabitRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class StatView { MONTHLY, YEARLY }

class HabitsViewModel(private val habitRepository: HabitRepository) : ViewModel() {

    val habits: StateFlow<List<Habit>> = habitRepository.getActiveHabits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Statistics (collapsible)
    private val _showStats = MutableStateFlow(false)
    val showStats: StateFlow<Boolean> = _showStats.asStateFlow()

    private val _statView = MutableStateFlow(StatView.MONTHLY)
    val statView: StateFlow<StatView> = _statView.asStateFlow()

    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    // Calendar
    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth: StateFlow<YearMonth> = _currentMonth.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    // First habit's check-in dates (shown on calendar)
    private val _calendarCheckInDates = MutableStateFlow<Set<LocalDate>>(emptySet())
    val calendarCheckInDates: StateFlow<Set<LocalDate>> = _calendarCheckInDates.asStateFlow()

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

    init {
        loadAllData()
    }

    fun toggleStats() { _showStats.value = !_showStats.value }
    fun toggleStatView() {
        _statView.value = if (_statView.value == StatView.MONTHLY) StatView.YEARLY else StatView.MONTHLY
    }

    fun previousYear() { _selectedYear.value = _selectedYear.value - 1; loadStats() }
    fun nextYear() { _selectedYear.value = _selectedYear.value + 1; loadStats() }

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
            val current = _dateCheckIns.value.toMutableMap()
            current[habitId] = !(current[habitId] ?: false)
            if (current[habitId] == false) current.remove(habitId)
            _dateCheckIns.value = current
            loadAllData() // Refresh stats + calendar dots
        }
    }

    fun showAddHabitDialog() { _showAddHabitDialog.value = true }
    fun dismissAddHabitDialog() { _showAddHabitDialog.value = false }

    fun addHabit(name: String, emoji: String) {
        viewModelScope.launch {
            val colorIndex = (habits.value.maxOfOrNull { it.colorIndex } ?: -1) + 1
            habitRepository.addHabit(name, emoji, colorIndex)
            _showAddHabitDialog.value = false
        }
    }

    fun showManageHabits() { _showManageHabits.value = true }
    fun dismissManageHabits() { _showManageHabits.value = false }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habit.id)
            loadAllData()
        }
    }

    private fun loadAllData() {
        loadStats()
        loadCalendarCheckIns()
        loadTodayCount()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _monthlyStats.value = habitRepository.getMonthlyStats(_selectedYear.value)
            _yearlyStats.value = habitRepository.getYearlyStats()
        }
    }

    private fun loadCalendarCheckIns() {
        viewModelScope.launch {
            val firstHabit = habits.value.firstOrNull()
            if (firstHabit != null) {
                val prefix = _currentMonth.value.toString() // yyyy-MM
                val dates = habitRepository.getCheckInDates(firstHabit.id, prefix)
                _calendarCheckInDates.value = dates.map { LocalDate.parse(it) }.toSet()
            } else {
                _calendarCheckInDates.value = emptySet()
            }
        }
    }

    private fun loadTodayCount() {
        viewModelScope.launch {
            val records = habitRepository.getRecordsForDate(LocalDate.now().toString())
            _todayCheckInCount.value = records.size
        }
    }

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
