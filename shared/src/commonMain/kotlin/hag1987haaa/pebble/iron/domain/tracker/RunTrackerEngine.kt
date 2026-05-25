package hag1987haaa.pebble.iron.domain.tracker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import hag1987haaa.pebble.iron.domain.location.LocationTracker
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.repository.RunRepository
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.util.LocationUtils
import hag1987haaa.pebble.iron.util.HealthUtils
import kotlin.math.pow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class RunStatistics(
    val activityType: ActivityType = ActivityType.RUNNING,
    val name: String? = null,
    val startTime: Instant? = null,
    val totalDistanceMeters: Double = 0.0,
    val totalSeconds: Long = 0,
    val currentPaceSecondsPerKm: Double = 0.0,
    val calories: Double = 0.0,
    val steps: Int = 0,
    val currentHeartRate: Int? = null,
    val heartRates: List<Int> = emptyList(),
    val totalElevationGain: Double = 0.0,
    val route: List<LocationPoint> = emptyList(),
    val hasGpsFix: Boolean = false,
    val status: RunStatus = RunStatus.IDLE
) {
    val formattedTime: String get() {
        val h = (totalSeconds / 3600).toInt()
        val m = ((totalSeconds % 3600) / 60).toInt()
        val s = (totalSeconds % 60).toInt()
        val mm = if (m < 10) "0$m" else "$m"
        val ss = if (s < 10) "0$s" else "$s"
        return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
    }
    val formattedDistance: String get() {
        val km = totalDistanceMeters / 1000.0
        val integerPart = km.toInt()
        val fractionalPart = ((km - integerPart) * 100).toInt()
        val ff = if (fractionalPart < 10) "0$fractionalPart" else "$fractionalPart"
        return "$integerPart.$ff"
    }
    val formattedPace: String get() {
        if (totalDistanceMeters <= 0 || totalSeconds <= 0) return "--:--"
        val km = totalDistanceMeters / 1000.0
        val paceSecondsPerKm = (totalSeconds / km).toInt()
        if (paceSecondsPerKm > 3600) return ">60:00"
        val m = paceSecondsPerKm / 60
        val s = paceSecondsPerKm % 60
        val ss = if (s < 10) "0$s" else "$s"
        return "$m:$ss"
    }
}

class RunTrackerEngine(
    private val locationTracker: LocationTracker,
    private val runRepository: RunRepository? = null,
    private val pebbleMessenger: PebbleMessenger? = null,
    private val appSettings: AppSettings? = null,
    private val scope: CoroutineScope
) {
    private val _statistics = MutableStateFlow(RunStatistics())
    val statistics: StateFlow<RunStatistics> = _statistics.asStateFlow()

    private var lastProcessedLocation: LocationPoint? = null
    private var lastRawLocation: LocationPoint? = null
    private val rawLocationWindow = mutableListOf<LocationPoint>()
    private val fullRoute = mutableListOf<LocationPoint>() // 高速な記録用の内部リスト
    private val WINDOW_SIZE = 3

    private var trackingJob: Job? = null
    private var timerJob: Job? = null
    private var isStartPending = false

    // 一時停止（PAUSE）中の計測除外用変数
    private var workoutStartSteps: Int? = null
    private var totalPausedSteps: Int = 0
    private var pauseStartSteps: Int? = null
    private var lastIncomingSteps: Int = -1

    fun setActivityType(type: ActivityType) {
        _statistics.update { it.copy(activityType = type) }
        RunState.updateStats(_statistics.value)
        // ウォッチ側に即座に同期
        triggerStatisticsUpdate()
    }

    fun launchWatchApp() {
        pebbleMessenger?.launchWatchApp()
    }

    fun prepare() {
        println("Engine: PREPARE called. Current: ${RunState.status.value}")
        // もし現在リザルト画面（STATE 6）や異常状態にある場合は、確実にクリーンアップ
        if (RunState.status.value == RunStatus.RESULT || RunState.status.value == RunStatus.IDLE) {
            reset()
        }
        
        if (trackingJob != null) return
        
        _statistics.update { it.copy(status = RunStatus.PREPARING) }
        RunState.setStatus(RunStatus.PREPARING)
        RunState.updateStats(_statistics.value)
        
        pebbleMessenger?.launchWatchApp()
        pebbleMessenger?.sendState(RunStatus.PREPARING)
        pebbleMessenger?.sendTouchConfig(appSettings?.isTouchControlEnabled ?: false)
        pebbleMessenger?.sendStatistics(_statistics.value)
        
        trackingJob = locationTracker.startTracking()
            .onEach { handleNewLocation(it) }
            .launchIn(scope)
    }

    fun start() {
        println("Engine: START called. Current: ${_statistics.value.status}, HasGps: ${_statistics.value.hasGpsFix}")
        
        // GPS信号がない場合は保留状態にする
        if (!_statistics.value.hasGpsFix) {
            println("Engine: GPS fix not acquired yet. Setting start to PENDING.")
            isStartPending = true
            // Watch側に「GPS検索中」の状態を維持させる
            pebbleMessenger?.sendState(RunStatus.PREPARING)
            return
        }
        
        isStartPending = false
        // ワークアウト開始時に有効な歩数データがあれば基準値として設定
        if (workoutStartSteps == null && lastIncomingSteps != -1) {
            workoutStartSteps = lastIncomingSteps
        }
        _statistics.update { it.copy(startTime = Clock.System.now(), status = RunStatus.ACTIVE) }
        RunState.setStatus(RunStatus.ACTIVE)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.sendState(RunStatus.ACTIVE)
        pebbleMessenger?.sendStatistics(_statistics.value)
        startTimer()
    }

    fun pause() {
        pauseStartSteps = lastIncomingSteps
        _statistics.update { it.copy(status = RunStatus.PAUSED) }
        RunState.setStatus(RunStatus.PAUSED)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.sendState(RunStatus.PAUSED)
        pebbleMessenger?.sendStatistics(_statistics.value)
        timerJob?.cancel(); timerJob = null
    }

    fun resume() {
        // 一時停止中に増加した歩数を控除
        pauseStartSteps?.let { start ->
            totalPausedSteps += (lastIncomingSteps - start)
        }
        pauseStartSteps = null
        
        // ワープ距離除外のため、直前の座標をリセット
        // これにより RESUME 直後の最初の座標が「新しい計測セグメントの開始点」となる
        lastProcessedLocation = null
        rawLocationWindow.clear()

        _statistics.update { it.copy(status = RunStatus.ACTIVE) }
        RunState.setStatus(RunStatus.ACTIVE)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.sendState(RunStatus.ACTIVE)
        pebbleMessenger?.sendStatistics(_statistics.value)
        startTimer()
    }

    fun finish() {
        timerJob?.cancel(); timerJob = null
        val now = Clock.System.now()
        val localTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val dateStr = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}"
        val timeStr = "${localTime.hour.toString().padStart(2, '0')}${localTime.minute.toString().padStart(2, '0')}"
        val defaultName = "${_statistics.value.activityType.displayName}-$dateStr-$timeStr"
        
        _statistics.update { it.copy(status = RunStatus.FINISHED, name = defaultName) }
        RunState.setStatus(RunStatus.FINISHED)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.sendState(RunStatus.FINISHED)
        pebbleMessenger?.sendStatistics(_statistics.value)
    }

    fun discard() {
        reset()
    }

    fun saveToResult() {
        timerJob?.cancel()
        timerJob = null
        trackingJob?.cancel()
        trackingJob = null
        locationTracker.stopTracking()
        
        // 状態を RESULT に更新
        _statistics.update { it.copy(status = RunStatus.RESULT) }
        RunState.setStatus(RunStatus.RESULT)
        RunState.updateStats(_statistics.value)
        
        pebbleMessenger?.sendState(RunStatus.RESULT)
        pebbleMessenger?.sendStatistics(_statistics.value)
        
        println("Engine: State transitioned to RESULT")
    }

    fun resetToIdle() {
        println("Engine: resetToIdle called")
        reset()
    }

    fun addHeartRate(bpm: Int) {
        // IDLE/RESULT/FINISHED状態では心拍履歴を更新しない（不整合防止）
        val currentStatus = RunState.status.value
        if (currentStatus == RunStatus.IDLE || 
            currentStatus == RunStatus.RESULT || 
            currentStatus == RunStatus.FINISHED) return

        _statistics.update { stats ->
            val updatedRoute = if (stats.status == RunStatus.ACTIVE && stats.route.isNotEmpty()) {
                // 最新の地点に心拍数がない、または最新地点の心拍数を更新したい場合（任意）
                // ここでは最新の地点にも心拍数を即座に反映させる
                val lastPoint = stats.route.last()
                stats.route.dropLast(1) + lastPoint.copy(heartRate = bpm)
            } else {
                stats.route
            }

            stats.copy(
                currentHeartRate = bpm,
                heartRates = stats.heartRates + bpm,
                route = updatedRoute
            ).also { s -> 
                RunState.updateStats(s) 
                pebbleMessenger?.sendStatistics(s)
            }
        }
    }

    fun updateSteps(totalSteps: Int) {
        lastIncomingSteps = totalSteps
        if (_statistics.value.status == RunStatus.ACTIVE) {
            // 運動開始後に初めてデータが届いた場合に基準値を設定（開始時の漏れ対策）
            val start = workoutStartSteps ?: totalSteps.also { workoutStartSteps = it }
            val displaySteps = (totalSteps - start - totalPausedSteps).coerceAtLeast(0)
            _statistics.update { it.copy(steps = displaySteps) }
            RunState.updateStats(_statistics.value)
        }
    }

    fun triggerStatisticsUpdate() {
        pebbleMessenger?.sendFullSync(_statistics.value)
    }

    fun rotateGraphType() {
        pebbleMessenger?.rotateGraphType(_statistics.value)
    }

    fun sendTouchConfig(enabled: Boolean) {
        pebbleMessenger?.sendTouchConfig(enabled)
    }

    private fun reset() {
        println("Engine: Performing thorough RESET")
        isStartPending = false
        
        trackingJob?.cancel()
        trackingJob = null
        timerJob?.cancel()
        timerJob = null
        locationTracker.stopTracking()
        
        // 統計情報を完全に初期化 (Status = IDLE)
        val freshStats = RunStatistics()
        _statistics.value = freshStats
        
        // グローバルな状態も即座に同期して初期化
        RunState.updateStats(freshStats)
        RunState.setStatus(RunStatus.IDLE)
        
        pebbleMessenger?.sendState(RunStatus.IDLE)
        
        // 内部状態のリセット
        lastProcessedLocation = null
        lastRawLocation = null
        rawLocationWindow.clear()
        fullRoute.clear()
        
        workoutStartSteps = null
        totalPausedSteps = 0
        pauseStartSteps = null
        lastIncomingSteps = -1
        
        println("Engine: RESET complete. Current state: ${RunState.status.value}")
    }

    private fun startTimer() {
        if (timerJob != null) return
        timerJob = scope.launch(Dispatchers.Default) {
            var counter = 0
            while (true) {
                delay(1000)
                counter++
                _statistics.update { stats ->
                    val nextSeconds = stats.totalSeconds + 1
                    // リアルタイムカロリー計算 (心拍数と傾斜を考慮した詳細版)
                    val weight = appSettings?.userWeightKg ?: 70.0f
                    val currentCalories = HealthUtils.calculateCalories(
                        type = stats.activityType,
                        weightKg = weight,
                        durationSeconds = nextSeconds,
                        distanceMeters = stats.totalDistanceMeters,
                        elevationGainMeters = stats.totalElevationGain,
                        avgHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null
                    )
                    
                    stats.copy(
                        totalSeconds = nextSeconds,
                        calories = currentCalories
                    ).also { s ->
                        pebbleMessenger?.sendStatistics(s)
                        // 10秒ごとにグラフデータを更新
                        if (counter % 10 == 0) {
                            pebbleMessenger?.sendGraphData(s)
                        }
                        RunState.updateStats(s)
                    }
                }
            }
        }
    }

    private fun handleNewLocation(location: LocationPoint) {
        // IDLE状態またはRESET直後は位置情報を処理しない（状態不整合を防止）
        if (RunState.status.value == RunStatus.IDLE) return

        val rawPrev = lastRawLocation
        lastRawLocation = location

        if (!_statistics.value.hasGpsFix) {
            println("Engine: GPS Fix acquired!")
            _statistics.update { it.copy(hasGpsFix = true) }
            
            if (isStartPending) {
                println("Engine: GPS Fix acquired and start was pending. Starting workout.")
                start()
            } else if (RunState.status.value == RunStatus.PREPARING) {
                println("Engine: Transitioning PREPARING -> READY")
                _statistics.update { it.copy(status = RunStatus.READY) }
                RunState.setStatus(RunStatus.READY)
                RunState.updateStats(_statistics.value)
                pebbleMessenger?.sendState(RunStatus.READY)
                pebbleMessenger?.sendStatistics(_statistics.value)
            }
        }

        // 計測中以外はこれ以上の統計更新（距離加算など）を行わない
        if (RunState.status.value != RunStatus.ACTIVE) {
            rawLocationWindow.clear()
            return
        }

        // 1. 異常値の棄却 (Outlier Rejection)
        // 前のRAW座標から物理的に不可能な距離（秒速40m = 時速144km以上）に飛んだ場合は破棄
        if (rawPrev != null) {
            val d = LocationUtils.calculateDistance(rawPrev.latitude, rawPrev.longitude, location.latitude, location.longitude)
            val dt = (location.timestamp.toEpochMilliseconds() - rawPrev.timestamp.toEpochMilliseconds()) / 1000.0
            if (dt > 0 && (d / dt) > 40.0) {
                println("Engine: Outlier detected! Speed: ${d/dt} m/s. Discarding.")
                return 
            }
        }

        // 2. スライディングウィンドウへの追加と加重移動平均の計算
        rawLocationWindow.add(location)
        if (rawLocationWindow.size > WINDOW_SIZE) {
            rawLocationWindow.removeAt(0)
        }

        val filteredLocation = calculateWeightedAverage(rawLocationWindow)
        val prevFiltered = lastProcessedLocation
        lastProcessedLocation = filteredLocation

        // 内部リストに高速追加
        val finalLocation = filteredLocation.copy(
            heartRate = location.heartRate ?: _statistics.value.currentHeartRate,
            steps = _statistics.value.steps
        )
        fullRoute.add(finalLocation)

        // prevFiltered が null の場合は、このセグメント（開始・再開直後）の基準点として扱う
        if (prevFiltered == null) return

        val delta = LocationUtils.calculateDistance(prevFiltered.latitude, prevFiltered.longitude, filteredLocation.latitude, filteredLocation.longitude)
        
        val elevationDelta = if (prevFiltered.altitude != null && filteredLocation.altitude != null) {
            (filteredLocation.altitude - prevFiltered.altitude).coerceAtLeast(0.0)
        } else 0.0

        _statistics.update { it.copy(
            totalDistanceMeters = it.totalDistanceMeters + delta,
            totalElevationGain = it.totalElevationGain + elevationDelta,
            currentHeartRate = location.heartRate ?: it.currentHeartRate,
            heartRates = if (location.heartRate != null) it.heartRates + location.heartRate else it.heartRates,
            route = fullRoute.toList() // 最新の全ルートを反映
        ).also { s ->
            pebbleMessenger?.sendStatistics(s)
            RunState.updateStats(s)
        } }
    }

    /**
     * 加重移動平均（WMA）を計算する。
     * 最新の座標ほど高い重みを置き、速度が速い場合は窓の実質的な影響を下げてコーナーカットを抑制する。
     */
    private fun calculateWeightedAverage(window: List<LocationPoint>): LocationPoint {
        if (window.isEmpty()) return LocationPoint(0.0, 0.0, timestamp = Clock.System.now())
        if (window.size == 1) return window.first()
        
        val latest = window.last()
        val speed = latest.speed ?: 0.0
        
        var totalWeight = 0.0
        var latSum = 0.0
        var lonSum = 0.0
        var altSum = 0.0
        
        window.forEachIndexed { index, point ->
            // 最新ほど重みを大きく (1, 4, 9...)
            var weight = (index + 1).toDouble().pow(2.0)
            
            // 時速 18km (5m/s) 以上の場合は、過去の重みを半分にして追従性を高める（コーナーカット防止）
            if (speed > 5.0 && index < window.size - 1) {
                weight *= 0.5
            }
            
            latSum += point.latitude * weight
            lonSum += point.longitude * weight
            altSum += (point.altitude ?: 0.0) * weight
            totalWeight += weight
        }
        
        return latest.copy(
            latitude = latSum / totalWeight,
            longitude = lonSum / totalWeight,
            altitude = if (latest.altitude != null) altSum / totalWeight else null
        )
    }
}
