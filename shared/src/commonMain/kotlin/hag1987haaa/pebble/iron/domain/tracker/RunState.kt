package hag1987haaa.pebble.iron.domain.tracker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ランニングの進行状態を定義
enum class RunStatus {
    IDLE,       // 未開始
    PREPARING,  // GPS探索中
    READY,      // GPS捕捉・開始待ち
    ACTIVE,     // 計測中
    PAUSED,     // 一時停止
    FINISHED,   // 終了・保存待ち
    RESULT      // リザルト表示
}

object RunState {
    private val _currentStats = MutableStateFlow(RunStatistics())
    val currentStats: StateFlow<RunStatistics> = _currentStats.asStateFlow()

    private val _status = MutableStateFlow(RunStatus.IDLE)
    val status: StateFlow<RunStatus> = _status.asStateFlow()

    fun updateStats(stats: RunStatistics) {
        _currentStats.value = stats
    }

    fun setStatus(newStatus: RunStatus) {
        _status.value = newStatus
    }
}
