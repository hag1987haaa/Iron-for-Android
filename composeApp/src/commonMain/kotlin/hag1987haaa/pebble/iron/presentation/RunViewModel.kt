package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.domain.tracker.RunStatistics

class RunViewModel : ViewModel() {
    val statistics: StateFlow<RunStatistics> = RunState.currentStats
    val isRunning: StateFlow<Boolean> = RunState.isServiceRunning
}
