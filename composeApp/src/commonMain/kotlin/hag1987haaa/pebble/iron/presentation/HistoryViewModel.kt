package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.repository.RunRepository

class HistoryViewModel(private val repository: RunRepository) : ViewModel() {
    val runs: StateFlow<List<RunActivity>> = repository.getAllRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getRunDetails(id: Long): RunActivity? {
        return repository.getRunDetails(id)
    }

    fun deleteRun(id: Long) {
        viewModelScope.launch {
            repository.deleteRun(id)
        }
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
