package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.domain.tracker.RunStatistics
import kotlinx.coroutines.flow.StateFlow

class RunViewModel : ViewModel() {
    val statistics: StateFlow<RunStatistics> = KmpDependencies.trackerEngine.statistics
}
