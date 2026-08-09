package hag1987haaa.pebble.iron.domain.tracker

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import hag1987haaa.pebble.iron.domain.location.LocationTracker
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.LocationPoint
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.repository.RunRepository
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.ble.BleHeartRateManager
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
    val status: RunStatus = RunStatus.IDLE,
    val hrSource: String = "PEBBLE",
    val isBleHrActive: Boolean = false,
    val latestBleHeartRate: Int? = null,
    val latestPebbleHeartRate: Int? = null
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
        if (paceSecondsPerKm > 3600) return "60:00"
        val m = paceSecondsPerKm / 60
        val s = paceSecondsPerKm % 60
        val ss = if (s < 10) "0$s" else "$s"
        return "$m:$ss"
    }
    val formattedAvgPace: String? get() = formattedPace 
    val formattedSpeed: String? get() {
        if (totalSeconds <= 0) return "0.0"
        val km = totalDistanceMeters / 1000.0
        val speed = km / (totalSeconds / 3600.0)
        return ( (speed * 10).toInt() / 10.0 ).toString()
    }
}

class RunTrackerEngine(
    private val locationTracker: LocationTracker,
    private val runRepository: RunRepository? = null,
    private val pebbleMessenger: PebbleMessenger? = null,
    private val appSettings: AppSettings? = null,
    private val bleHrManager: BleHeartRateManager? = null,
    private val scope: CoroutineScope
) {
    private val _statistics = MutableStateFlow(RunStatistics(
        activityType = appSettings?.lastActivityType?.let { 
            try { ActivityType.valueOf(it) } catch(e: Exception) { ActivityType.RUNNING }
        } ?: ActivityType.RUNNING
    ))
    val statistics: StateFlow<RunStatistics> = _statistics.asStateFlow()

    private var lastProcessedLocation: LocationPoint? = null
    private var lastRawLocation: LocationPoint? = null
    private val rawLocationWindow = mutableListOf<LocationPoint>()
    private val fullRoute = mutableListOf<LocationPoint>() 
    private val windowSize = 3

    private var trackingJob: Job? = null
    private var timerJob: Job? = null
    private var timeoutJob: Job? = null
    private var isStartPending = false

    private var lastIncomingSteps: Int = -1
    private var totalAccumulatedSteps: Int = 0
    private var lastNotifiedDistanceKm: Int = 0
    private var lastNotifiedTimeMinutes: Int = 0
    private var autoConnectJob: Job? = null

    // 心拍不動検知用の時刻保持
    private var lastHrTimestamp: Long = 0L
    private var lastBpmValue: Int? = null

    init {
        scope.launch {
            bleHrManager?.heartRateBpm?.collect { bpm ->
                if (appSettings?.isBleHeartRateEnabled == true) {
                    addHeartRate(bpm, source = "BLE")
                }
            }
        }
        scope.launch {
            bleHrManager?.isDataActive?.collect { active ->
                _statistics.update { it.copy(isBleHrActive = active) }
                reEvaluateHeartRateSource()
            }
        }
        startSmartAutoConnect()
    }

    private fun reEvaluateHeartRateSource() {
        val now = Clock.System.now().toEpochMilliseconds()
        val stats = _statistics.value
        
        // データの沈黙チェック
        val isStale = lastHrTimestamp > 0 && (now - lastHrTimestamp) > 10000L
        
        // メインソースの決定
        val isBleActive = bleHrManager?.isDataActive?.value ?: false
        val isBlePreferred = appSettings?.preferBleHeartRate ?: true
        
        // 沈黙しておらず、BLE がアクティブなら BLE、そうでなければ Pebble を採用
        val newSource = if (isBleActive && isBlePreferred && !isStale) "BLE" else "PEBBLE"
        val newBpm = if (newSource == "BLE") stats.latestBleHeartRate else stats.latestPebbleHeartRate

        if (stats.currentHeartRate != newBpm || stats.hrSource != newSource) {
            _statistics.update { it.copy(currentHeartRate = newBpm, hrSource = newSource) }
            RunState.updateStats(_statistics.value)
            pebbleMessenger?.sendStatistics(_statistics.value)
        }
    }

    private fun startSmartAutoConnect() {
        autoConnectJob?.cancel()
        autoConnectJob = scope.launch {
            val settings = appSettings ?: return@launch
            val hrManager = bleHrManager ?: return@launch
            if (!settings.isBleHeartRateEnabled || hrManager.isConnected.value) return@launch

            delay(1500)
            settings.preferredBleHrAddress?.let { hrManager.connect(it); return@launch }
            settings.bleHeartRateDeviceAddress?.let {
                hrManager.connect(it)
                delay(3000)
                if (hrManager.isConnected.value) return@launch
            }

            val registeredAddresses = settings.registeredBleHrDevices.map { it.substringBefore("|") }
            if (registeredAddresses.isEmpty()) return@launch

            while (isActive) {
                if (RunState.status.value == RunStatus.IDLE) break
                hag1987haaa.pebble.iron.KmpDependencies.bleScanner.startScan("0000180d-0000-1000-8000-00805f9b34fb")
                try {
                    withTimeout(15000) {
                        hag1987haaa.pebble.iron.KmpDependencies.bleScanner.foundDevices.collect { devices ->
                            val match = devices.find { it.address in registeredAddresses }
                            if (match != null) {
                                hag1987haaa.pebble.iron.KmpDependencies.bleScanner.stopScan()
                                delay(500)
                                hrManager.connect(match.address)
                                throw CancellationException("Match found")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) break
                } finally {
                    hag1987haaa.pebble.iron.KmpDependencies.bleScanner.stopScan()
                }
                delay(10000)
            }
        }
    }

    fun setActivityType(type: ActivityType) {
        _statistics.update { it.copy(activityType = type) }
        appSettings?.let { it.lastActivityType = type.name; it.save() }
        RunState.updateStats(_statistics.value); triggerStatisticsUpdate()
    }

    fun launchWatchApp() { pebbleMessenger?.launchWatchApp() }

    fun prepare() {
        if (RunState.status.value == RunStatus.RESULT || RunState.status.value == RunStatus.IDLE) clearWorkoutData()
        if (trackingJob != null) return
        startSmartAutoConnect()
        _statistics.update { it.copy(status = RunStatus.PREPARING) }
        RunState.setStatus(RunStatus.PREPARING)
        pebbleMessenger?.launchWatchApp(); pebbleMessenger?.sendState(RunStatus.PREPARING)
        trackingJob = locationTracker.startTracking().onEach { handleNewLocation(it) }.launchIn(scope)
    }

    private fun clearWorkoutData() {
        isStartPending = false; timeoutJob?.cancel(); timeoutJob = null; trackingJob?.cancel(); trackingJob = null; timerJob?.cancel(); timerJob = null
        _statistics.value = RunStatistics(activityType = _statistics.value.activityType)
        RunState.updateStats(_statistics.value); lastProcessedLocation = null; lastRawLocation = null; rawLocationWindow.clear(); fullRoute.clear()
        lastHrTimestamp = 0L; lastBpmValue = null
    }

    fun start() {
        if (!_statistics.value.hasGpsFix) { isStartPending = true; pebbleMessenger?.sendState(RunStatus.PREPARING); return }
        isStartPending = false; timeoutJob?.cancel()
        _statistics.update { it.copy(startTime = Clock.System.now(), status = RunStatus.ACTIVE) }
        RunState.setStatus(RunStatus.ACTIVE); pebbleMessenger?.sendState(RunStatus.ACTIVE)
        startTimer()
    }

    fun pause() {
        _statistics.update { it.copy(status = RunStatus.PAUSED) }
        RunState.setStatus(RunStatus.PAUSED); pebbleMessenger?.sendState(RunStatus.PAUSED)
        timerJob?.cancel(); timerJob = null
    }

    fun resume() {
        lastProcessedLocation = null; rawLocationWindow.clear()
        _statistics.update { it.copy(status = RunStatus.ACTIVE) }
        RunState.setStatus(RunStatus.ACTIVE); pebbleMessenger?.sendState(RunStatus.ACTIVE)
        startTimer()
    }

    fun finish() {
        timerJob?.cancel(); trackingJob?.cancel(); locationTracker.stopTracking()
        val now = Clock.System.now(); val localTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val defaultName = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}-${localTime.hour.toString().padStart(2, '0')}${localTime.minute.toString().padStart(2, '0')}"
        _statistics.update { it.copy(status = RunStatus.FINISHED, name = defaultName) }
        // 修正: 生成したデフォルト名をアプリ全体(RunState)にも即座に反映させる
        RunState.updateStats(_statistics.value)
        RunState.setStatus(RunStatus.FINISHED)
        pebbleMessenger?.sendState(RunStatus.FINISHED)
    }

    fun discard() { reset() }
    fun saveToResult() {
        timerJob?.cancel(); trackingJob?.cancel(); locationTracker.stopTracking()
        _statistics.update { it.copy(status = RunStatus.RESULT) }
        RunState.setStatus(RunStatus.RESULT); pebbleMessenger?.sendState(RunStatus.RESULT)
    }

    fun resetToIdle() { reset() }

    fun addHeartRate(bpm: Int, source: String = "PEBBLE") {
        val currentStatus = RunState.status.value
        if (currentStatus == RunStatus.IDLE || currentStatus == RunStatus.RESULT || currentStatus == RunStatus.FINISHED) return

        // 1. 各センサーの最新値を更新
        val validBpm = if (bpm in 30..220) bpm else null
        
        // 数値が変化した（または初回）場合のみタイムスタンプを更新
        if (validBpm != null && (validBpm != lastBpmValue || source == "PEBBLE")) {
            lastHrTimestamp = Clock.System.now().toEpochMilliseconds()
            lastBpmValue = validBpm
        }

        _statistics.update { stats ->
            if (source == "BLE") stats.copy(latestBleHeartRate = validBpm)
            else stats.copy(latestPebbleHeartRate = validBpm)
        }

        reEvaluateHeartRateSource()

        // 2. 有効な心拍数があれば履歴に反映
        val mainBpm = _statistics.value.currentHeartRate ?: return
        _statistics.update { stats ->
            val updatedRoute = if (stats.status == RunStatus.ACTIVE && stats.route.isNotEmpty()) {
                val lastPoint = stats.route.last()
                stats.route.dropLast(1) + lastPoint.copy(heartRate = mainBpm)
            } else stats.route
            stats.copy(heartRates = stats.heartRates + mainBpm, route = updatedRoute).also { s -> 
                // --- オートラップ (距離通知) 判定 ---
                val step = appSettings?.notificationDistanceStep ?: 0.0f
                if (step > 0.0f) {
                    val unitMeters = if (appSettings?.isMetric == true) 1000.0 else 1609.344
                    val threshold = step * unitMeters
                    val currentLapIdx = (s.totalDistanceMeters / threshold).toInt()
                    
                    if (currentLapIdx > lastNotifiedDistanceKm) {
                        lastNotifiedDistanceKm = currentLapIdx
                        if (appSettings?.isAutoLaunchOnDistanceNotificationEnabled == true) pebbleMessenger?.launchWatchApp()
                        pebbleMessenger?.sendNotification(0) 
                    }
                }

                RunState.updateStats(s)
                pebbleMessenger?.sendStatistics(s)
            }
        }
    }

    fun updateSteps(totalSteps: Int) {
        if (totalSteps <= 0) return 
        if (lastIncomingSteps != -1 && totalSteps >= lastIncomingSteps) {
            val delta = totalSteps - lastIncomingSteps
            if (_statistics.value.status == RunStatus.ACTIVE) {
                totalAccumulatedSteps += delta
                _statistics.update { it.copy(steps = totalAccumulatedSteps) }
                RunState.updateStats(_statistics.value)
            }
        }
        lastIncomingSteps = totalSteps
    }

    fun triggerStatisticsUpdate() { pebbleMessenger?.sendFullSync(_statistics.value) }
    fun rotateGraphType() { pebbleMessenger?.rotateGraphType(_statistics.value) }
    fun rotateMidData() { pebbleMessenger?.rotateMidData(_statistics.value) }
    fun sendTouchConfig(enabled: Boolean) { pebbleMessenger?.sendTouchConfig(enabled) }

    private fun reset() {
        timeoutJob?.cancel(); trackingJob?.cancel(); timerJob?.cancel(); locationTracker.stopTracking()
        _statistics.value = RunStatistics(activityType = _statistics.value.activityType)
        RunState.updateStats(_statistics.value); RunState.setStatus(RunStatus.IDLE); pebbleMessenger?.sendState(RunStatus.IDLE)
        lastProcessedLocation = null; lastRawLocation = null; rawLocationWindow.clear(); fullRoute.clear()
        lastHrTimestamp = 0L; lastBpmValue = null; lastIncomingSteps = -1; totalAccumulatedSteps = 0
        bleHrManager?.close() 
    }

    private fun startTimer() {
        if (timerJob != null) return
        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                reEvaluateHeartRateSource()
                _statistics.update { stats ->
                    val nextSeconds = stats.totalSeconds + 1
                    val weight = appSettings?.userWeightKg ?: 70.0f
                    val currentCalories = HealthUtils.calculateCalories(stats.activityType, weight, nextSeconds, stats.totalDistanceMeters, stats.totalElevationGain, if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null)
                    stats.copy(totalSeconds = nextSeconds, calories = currentCalories).also { s ->
                        pebbleMessenger?.sendStatistics(s)
                        RunState.updateStats(s)
                    }
                }
            }
        }
    }

    private fun handleNewLocation(location: LocationPoint) {
        if (RunState.status.value == RunStatus.IDLE) return
        lastRawLocation = location
        if (!_statistics.value.hasGpsFix) {
            _statistics.update { it.copy(hasGpsFix = true) }
            if (isStartPending) start()
            else if (RunState.status.value == RunStatus.PREPARING) {
                _statistics.update { it.copy(status = RunStatus.READY) }
                RunState.setStatus(RunStatus.READY); pebbleMessenger?.sendState(RunStatus.READY)
            }
        }
        if (RunState.status.value != RunStatus.ACTIVE) return
        rawLocationWindow.add(location); if (rawLocationWindow.size > windowSize) rawLocationWindow.removeAt(0)
        val filteredLocation = calculateWeightedAverage(rawLocationWindow)
        
        // 1つ前の位置からの移動距離を計算して加算
        var distanceDelta = 0.0
        lastProcessedLocation?.let { prev ->
            distanceDelta = LocationUtils.calculateDistance(
                prev.latitude, prev.longitude,
                filteredLocation.latitude, filteredLocation.longitude
            )
        }
        lastProcessedLocation = filteredLocation

        val finalLocation = filteredLocation.copy(
            heartRate = location.heartRate ?: _statistics.value.currentHeartRate, 
            steps = _statistics.value.steps
        )
        fullRoute.add(finalLocation)

        _statistics.update { it.copy(
            totalDistanceMeters = it.totalDistanceMeters + distanceDelta, 
            route = fullRoute.toList()
        ).also { s ->
            pebbleMessenger?.sendStatistics(s); RunState.updateStats(s)
        } }
    }

    private fun calculateWeightedAverage(window: List<LocationPoint>): LocationPoint {
        if (window.isEmpty()) return LocationPoint(0.0, 0.0, timestamp = Clock.System.now())
        val latest = window.last()
        var totalWeight = 0.0; var latSum = 0.0; var lonSum = 0.0; var altSum = 0.0
        window.forEachIndexed { index, point ->
            val weight = (index + 1).toDouble().pow(2.0); latSum += point.latitude * weight; lonSum += point.longitude * weight; altSum += (point.altitude ?: 0.0) * weight; totalWeight += weight
        }
        return latest.copy(latitude = latSum / totalWeight, longitude = lonSum / totalWeight, altitude = if (latest.altitude != null) altSum / totalWeight else null)
    }

    private fun resetTimeoutTimer() {
        val currentStatus = RunState.status.value
        if (currentStatus != RunStatus.PREPARING && currentStatus != RunStatus.READY) return
        timeoutJob?.cancel(); timeoutJob = scope.launch { delay(5 * 60 * 1000L); reset() }
    }
}
