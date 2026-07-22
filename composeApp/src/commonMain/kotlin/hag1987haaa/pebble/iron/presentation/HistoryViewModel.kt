package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.repository.RunRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

enum class HistoryViewMode {
    SCROLL, MONTHLY, WEEKLY
}

data class HistoryFilter(
    val selectedTypes: Set<ActivityType> = emptySet(),
    val startYear: Int? = null,
    val startMonth: Int? = null,
    val endYear: Int? = null,
    val endMonth: Int? = null,
    val minDistance: Double? = null,
    val isGreaterDistance: Boolean = true,
    val minCalories: Double? = null,
    val isGreaterCalories: Boolean = true
)

class HistoryViewModel(private val repository: RunRepository) : ViewModel() {
    private val _viewMode = MutableStateFlow(HistoryViewMode.SCROLL)
    val viewMode = _viewMode.asStateFlow()

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter = _filter.asStateFlow()

    private val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val _calendarDate = MutableStateFlow(now)
    val calendarDate = _calendarDate.asStateFlow()

    private val _selectedSpecificDate = MutableStateFlow<LocalDate?>(null)
    val selectedSpecificDate = _selectedSpecificDate.asStateFlow()

    private val allRuns = repository.getAllRuns()

    val runs: StateFlow<List<RunActivity>> = combine(allRuns, _filter, _viewMode, _calendarDate, _selectedSpecificDate) { runs, filter, mode, calDate, specDate ->
        runs.filter { run ->
            val dt = run.startTime.toLocalDateTime(TimeZone.currentSystemDefault())
            
            val typeMatch = filter.selectedTypes.isEmpty() || run.type in filter.selectedTypes
            
            val distanceMatch = if (filter.minDistance == null) true else {
                if (filter.isGreaterDistance) run.distanceMeters >= filter.minDistance
                else run.distanceMeters < filter.minDistance
            }
            val calMatch = if (filter.minCalories == null) true else {
                val cal = run.calories ?: 0.0
                if (filter.isGreaterCalories) cal >= filter.minCalories
                else cal < filter.minCalories
            }

            val periodMatch = when (mode) {
                HistoryViewMode.SCROLL -> {
                    val startVal = filter.startYear?.let { it * 100 + (filter.startMonth ?: 1) } ?: 0
                    val endVal = filter.endYear?.let { it * 100 + (filter.endMonth ?: 12) } ?: Int.MAX_VALUE
                    val currentVal = dt.year * 100 + dt.monthNumber
                    currentVal in startVal..endVal
                }
                HistoryViewMode.MONTHLY -> {
                    if (specDate != null) {
                        dt.date == specDate
                    } else {
                        dt.year == calDate.year && dt.monthNumber == calDate.monthNumber
                    }
                }
                HistoryViewMode.WEEKLY -> {
                    if (specDate != null) {
                        dt.date == specDate
                    } else {
                        // 精確な週の判定: calDate を含む月〜日の範囲
                        val dayOffset = calDate.dayOfWeek.ordinal // Mon=0
                        val weekStart = calDate.minus(dayOffset, DateTimeUnit.DAY)
                        val weekEnd = weekStart.plus(6, DateTimeUnit.DAY)
                        dt.date in weekStart..weekEnd
                    }
                }
            }
            
            typeMatch && distanceMatch && calMatch && periodMatch
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableYears: StateFlow<List<Int>> = allRuns.map { runs ->
        runs.map { it.startTime.toLocalDateTime(TimeZone.currentSystemDefault()).year }.distinct().sortedDescending()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setViewMode(mode: HistoryViewMode) {
        _viewMode.value = mode
        _selectedSpecificDate.value = null
    }

    fun toggleTypeFilter(type: ActivityType) {
        val current = _filter.value.selectedTypes
        val next = if (type in current) current - type else current + type
        _filter.value = _filter.value.copy(selectedTypes = next)
    }

    fun setDateRange(startYear: Int?, startMonth: Int?, endYear: Int?, endMonth: Int?) {
        _filter.value = _filter.value.copy(
            startYear = startYear, startMonth = startMonth,
            endYear = endYear, endMonth = endMonth
        )
    }

    fun toggleDistanceComparison() {
        _filter.value = _filter.value.copy(isGreaterDistance = !_filter.value.isGreaterDistance)
    }

    fun toggleCaloriesComparison() {
        _filter.value = _filter.value.copy(isGreaterCalories = !_filter.value.isGreaterCalories)
    }

    fun setDistanceFilter(valMeters: Double?) {
        _filter.value = _filter.value.copy(minDistance = valMeters)
    }

    fun setCaloriesFilter(valKcal: Double?) {
        _filter.value = _filter.value.copy(minCalories = valKcal)
    }

    fun clearFilters() {
        _filter.value = HistoryFilter()
        _selectedSpecificDate.value = null
        // クリア時に表示基準日を「今日」に戻す
        _calendarDate.value = now
    }

    fun goToToday() {
        _calendarDate.value = now
        _selectedSpecificDate.value = null
    }

    fun shiftMonth(months: Int) {
        _calendarDate.value = if (months > 0) _calendarDate.value.plus(months, DateTimeUnit.MONTH)
                             else _calendarDate.value.minus(-months, DateTimeUnit.MONTH)
        _selectedSpecificDate.value = null
    }

    fun shiftWeek(weeks: Int) {
        _calendarDate.value = if (weeks > 0) _calendarDate.value.plus(weeks, DateTimeUnit.WEEK)
                             else _calendarDate.value.minus(-weeks, DateTimeUnit.WEEK)
        _selectedSpecificDate.value = null
    }

    fun selectSpecificDate(date: LocalDate?) {
        _selectedSpecificDate.value = if (_selectedSpecificDate.value == date) null else date
    }

    suspend fun getRunDetails(id: Long): RunActivity? {
        return repository.getRunDetails(id)
    }

    fun updateRunName(id: Long, name: String) {
        viewModelScope.launch {
            repository.updateRunName(id, name)
        }
    }

    fun updateActivityType(id: Long, type: hag1987haaa.pebble.iron.domain.model.ActivityType, userWeight: Float? = null) {
        viewModelScope.launch {
            repository.updateActivityType(id, type, userWeight)
        }
    }
}
