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

    init {
        scope.launch {
            bleHrManager?.heartRateBpm?.collect { bpm ->
                if (bpm > 0 && appSettings?.isBleHeartRateEnabled == true) {
                    addHeartRate(bpm, source = "BLE")
                }
            }
        }
        scope.launch {
            bleHrManager?.isDataActive?.collect { active ->
                _statistics.update { it.copy(isBleHrActive = active) }
                if (!active) {
                    _statistics.update { stats ->
                        stats.copy(
                            latestBleHeartRate = null,
                            currentHeartRate = if (stats.hrSource == "BLE") null else stats.currentHeartRate
                        )
                    }
                    triggerStatisticsUpdate()
                }
            }
        }
        startSmartAutoConnect()
    }

    private fun startSmartAutoConnect() {
        autoConnectJob?.cancel()
        autoConnectJob = scope.launch {
            val settings = appSettings ?: return@launch
            val hrManager = bleHrManager ?: return@launch
            if (!settings.isBleHeartRateEnabled) return@launch

            // すでに接続中なら何もしない
            if (hrManager.isConnected.value) {
                println("RunTrackerEngine: Already connected, skip scanning.")
                return@launch
            }

            delay(2000) // 終了後のBluetoothスタック安定待ち

            // --- 知っているデバイスには「スキャンなし」で直接繋ぎに行く ---
            
            // 1. ピン留め（優先）があれば、スキャンせず即座に直接接続（待機モード）
            settings.preferredBleHrAddress?.let { preferred ->
                println("RunTrackerEngine: Direct connecting to PINNED sensor: $preferred")
                hrManager.connect(preferred)
                return@launch
            }

            // 2. ピン留めがなくても、最後に使ったデバイスがあれば、それにも直接接続を試みる
            settings.bleHeartRateDeviceAddress?.let { lastUsed ->
                println("RunTrackerEngine: Direct connecting to LAST USED sensor: $lastUsed")
                hrManager.connect(lastUsed)
                // 直接接続後、少し待って繋がらなければスキャンへ移行する（フォールバック）
                delay(3000)
                if (hrManager.isConnected.value) return@launch
            }

            // 3. 上記で見つからない・繋がらない場合は、登録済みリスト全体をスキャン
            val registeredAddresses = settings.registeredBleHrDevices.map { it.substringBefore("|") }
            if (registeredAddresses.isEmpty()) return@launch

            println("RunTrackerEngine: Falling back to scanning for registered BLE sensors...")
            
            while (isActive) {
                if (RunState.status.value == RunStatus.IDLE) break
                if (hrManager.isConnected.value) break

                hag1987haaa.pebble.iron.KmpDependencies.bleScanner.startScan("0000180d-0000-1000-8000-00805f9b34fb")
                
                try {
                    withTimeout(15000) {
                        hag1987haaa.pebble.iron.KmpDependencies.bleScanner.foundDevices.collect { devices ->
                            val match = devices.find { it.address in registeredAddresses }
                            if (match != null) {
                                println("RunTrackerEngine: Found via scan: ${match.name}. Connecting...")
                                hag1987haaa.pebble.iron.KmpDependencies.bleScanner.stopScan()
                                delay(1000) // スキャン停止直後のBluetoothスタック安定待ちを長めに
                                hrManager.connect(match.address)
                                throw CancellationException("Match found")
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException && e.message == "Match found") break
                } finally {
                    hag1987haaa.pebble.iron.KmpDependencies.bleScanner.stopScan()
                }
                
                delay(5000)
            }
        }
    }

    private val hrRawBuffer = mutableListOf<Int>()
    private val hrSmoothingBuffer = mutableListOf<Int>()
    private val HR_FILTER_WINDOW = 5

    fun setActivityType(type: ActivityType) {
        _statistics.update { it.copy(activityType = type) }
        appSettings?.let {
            it.lastActivityType = type.name
            it.save()
        }
        RunState.updateStats(_statistics.value)
        triggerStatisticsUpdate()
        resetTimeoutTimer()
    }

    fun launchWatchApp() {
        pebbleMessenger?.launchWatchApp()
    }

    fun prepare() {
        if (RunState.status.value == RunStatus.RESULT || RunState.status.value == RunStatus.IDLE) {
            clearWorkoutData()
        }
        if (trackingJob != null) return
        startSmartAutoConnect()
        _statistics.update { it.copy(status = RunStatus.PREPARING) }
        RunState.setStatus(RunStatus.PREPARING)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.launchWatchApp()
        pebbleMessenger?.sendState(RunStatus.PREPARING)
        pebbleMessenger?.sendStatistics(_statistics.value)
        resetTimeoutTimer()
        trackingJob = locationTracker.startTracking().onEach { handleNewLocation(it) }.launchIn(scope)
    }

    private fun clearWorkoutData() {
        isStartPending = false
        timeoutJob?.cancel()
        timeoutJob = null
        trackingJob?.cancel()
        trackingJob = null
        timerJob?.cancel()
        timerJob = null
        val currentType = _statistics.value.activityType
        val freshStats = RunStatistics(activityType = currentType)
        _statistics.value = freshStats
        RunState.updateStats(freshStats)
        lastProcessedLocation = null
        lastRawLocation = null
        rawLocationWindow.clear()
        fullRoute.clear()
        lastNotifiedDistanceKm = 0
        lastNotifiedTimeMinutes = 0
        lastIncomingSteps = -1
        totalAccumulatedSteps = 0
        hrRawBuffer.clear()
        hrSmoothingBuffer.clear()
    }

    fun start() {
        if (!_statistics.value.hasGpsFix) {
            isStartPending = true
            pebbleMessenger?.sendState(RunStatus.PREPARING)
            resetTimeoutTimer()
            return
        }
        isStartPending = false
        timeoutJob?.cancel()
        timeoutJob = null
        startSmartAutoConnect()
        _statistics.update { it.copy(startTime = Clock.System.now(), status = RunStatus.ACTIVE) }
        RunState.setStatus(RunStatus.ACTIVE)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.sendState(RunStatus.ACTIVE)
        pebbleMessenger?.sendStatistics(_statistics.value)
        startTimer()
    }

    fun pause() {
        _statistics.update { it.copy(status = RunStatus.PAUSED) }
        RunState.setStatus(RunStatus.PAUSED)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.sendState(RunStatus.PAUSED)
        pebbleMessenger?.sendStatistics(_statistics.value)
        timerJob?.cancel(); timerJob = null
    }

    fun resume() {
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
        trackingJob?.cancel(); trackingJob = null
        locationTracker.stopTracking()
        timeoutJob?.cancel(); timeoutJob = null
        val now = Clock.System.now()
        val localTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val defaultName = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}-${localTime.hour.toString().padStart(2, '0')}${localTime.minute.toString().padStart(2, '0')}"
        _statistics.update { it.copy(status = RunStatus.FINISHED, name = defaultName) }
        RunState.setStatus(RunStatus.FINISHED)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.sendState(RunStatus.FINISHED)
        pebbleMessenger?.sendStatistics(_statistics.value)
    }

    fun discard() { reset() }

    fun saveToResult() {
        timerJob?.cancel(); timerJob = null
        trackingJob?.cancel(); trackingJob = null
        locationTracker.stopTracking()
        _statistics.update { it.copy(status = RunStatus.RESULT) }
        RunState.setStatus(RunStatus.RESULT)
        RunState.updateStats(_statistics.value)
        pebbleMessenger?.sendState(RunStatus.RESULT)
        pebbleMessenger?.sendStatistics(_statistics.value)
    }

    fun resetToIdle() { reset() }

    fun addHeartRate(bpm: Int, source: String = "PEBBLE") {
        val currentStatus = RunState.status.value
        if (currentStatus == RunStatus.IDLE || currentStatus == RunStatus.RESULT || currentStatus == RunStatus.FINISHED) return
        if (bpm <= 0 || bpm < 30 || bpm > 220) return
        val updateLatestFields: (RunStatistics) -> RunStatistics = { stats ->
            stats.copy(
                latestBleHeartRate = if (source == "BLE") bpm else stats.latestBleHeartRate,
                latestPebbleHeartRate = if (source == "PEBBLE") bpm else stats.latestPebbleHeartRate
            )
        }
        val shouldUpdateMain = !(source == "PEBBLE" && appSettings?.preferBleHeartRate == true && bleHrManager?.isDataActive?.value == true)
        if (!shouldUpdateMain) {
            _statistics.update { stats -> updateLatestFields(stats).also { s -> RunState.updateStats(s); pebbleMessenger?.sendStatistics(s) } }
            return
        }
        val interval = appSettings?.hrSamplingInterval ?: 0
        val processedBpm: Int
        if (interval > 0 && source == "PEBBLE") {
            hrRawBuffer.add(bpm)
            if (hrRawBuffer.size > HR_FILTER_WINDOW) hrRawBuffer.removeAt(0)
            val medianBpm = if (hrRawBuffer.size >= 3) hrRawBuffer.sorted()[hrRawBuffer.size / 2] else bpm
            hrSmoothingBuffer.add(medianBpm)
            if (hrSmoothingBuffer.size > HR_FILTER_WINDOW) hrSmoothingBuffer.removeAt(0)
            processedBpm = hrSmoothingBuffer.average().toInt()
        } else if (source == "BLE") { processedBpm = bpm } else { hrRawBuffer.clear(); hrSmoothingBuffer.clear(); processedBpm = bpm }

        _statistics.update { stats ->
            val updatedRoute = if (stats.status == RunStatus.ACTIVE && stats.route.isNotEmpty()) {
                val lastPoint = stats.route.last()
                stats.route.dropLast(1) + lastPoint.copy(heartRate = processedBpm)
            } else stats.route
            stats.copy(
                currentHeartRate = processedBpm,
                heartRates = stats.heartRates + processedBpm,
                route = updatedRoute,
                hrSource = source,
                latestBleHeartRate = if (source == "BLE") bpm else stats.latestBleHeartRate,
                latestPebbleHeartRate = if (source == "PEBBLE") bpm else stats.latestPebbleHeartRate
            ).also { s -> RunState.updateStats(s); pebbleMessenger?.sendStatistics(s) }
        }
        resetTimeoutTimer()
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
        resetTimeoutTimer()
    }

    fun triggerStatisticsUpdate() { pebbleMessenger?.sendFullSync(_statistics.value); resetTimeoutTimer() }
    fun rotateGraphType() { pebbleMessenger?.rotateGraphType(_statistics.value); resetTimeoutTimer() }
    fun rotateMidData() { pebbleMessenger?.rotateMidData(_statistics.value); resetTimeoutTimer() }
    fun sendTouchConfig(enabled: Boolean) { pebbleMessenger?.sendTouchConfig(enabled) }

    private fun reset() {
        isStartPending = false
        timeoutJob?.cancel(); timeoutJob = null
        trackingJob?.cancel(); trackingJob = null
        timerJob?.cancel(); timerJob = null
        locationTracker.stopTracking()
        
        val currentType = _statistics.value.activityType
        val freshStats = RunStatistics(activityType = currentType)
        _statistics.value = freshStats
        RunState.updateStats(freshStats)
        RunState.setStatus(RunStatus.IDLE)
        pebbleMessenger?.sendState(RunStatus.IDLE)
        
        lastProcessedLocation = null
        lastRawLocation = null
        rawLocationWindow.clear()
        fullRoute.clear()
        lastNotifiedDistanceKm = 0
        lastNotifiedTimeMinutes = 0
        lastIncomingSteps = -1
        totalAccumulatedSteps = 0
        hrRawBuffer.clear()
        hrSmoothingBuffer.clear()

        // ワークアウト終了時に BLE セッションを完全に終了させる
        bleHrManager?.close()
    }

    private fun startTimer() {
        if (timerJob != null) return
        timerJob = scope.launch(Dispatchers.Default) {
            var counter = 0
            while (true) {
                delay(1000); counter++
                _statistics.update { stats ->
                    val nextSeconds = stats.totalSeconds + 1
                    val timeInterval = appSettings?.notificationTimeSeconds ?: 0
                    if (timeInterval > 0) {
                        val currentMinutes = (nextSeconds / timeInterval).toInt()
                        if (currentMinutes > lastNotifiedTimeMinutes) {
                            lastNotifiedTimeMinutes = currentMinutes
                            if (appSettings?.isAutoLaunchOnTimeNotificationEnabled == true) pebbleMessenger?.launchWatchApp()
                            pebbleMessenger?.sendNotification(1) 
                        }
                    }
                    val weight = appSettings?.userWeightKg ?: 70.0f
                    val currentCalories = HealthUtils.calculateCalories(stats.activityType, weight, nextSeconds, stats.totalDistanceMeters, stats.totalElevationGain, if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null)
                    stats.copy(totalSeconds = nextSeconds, calories = currentCalories).also { s ->
                        pebbleMessenger?.sendStatistics(s)
                        if (counter % 10 == 0) pebbleMessenger?.sendGraphData(s)
                        RunState.updateStats(s)
                    }
                }
            }
        }
    }

    private fun handleNewLocation(location: LocationPoint) {
        if (RunState.status.value == RunStatus.IDLE) return
        val rawPrev = lastRawLocation
        lastRawLocation = location
        if (!_statistics.value.hasGpsFix) {
            _statistics.update { it.copy(hasGpsFix = true) }
            if (isStartPending) start()
            else if (RunState.status.value == RunStatus.PREPARING) {
                _statistics.update { it.copy(status = RunStatus.READY) }
                RunState.setStatus(RunStatus.READY)
                RunState.updateStats(_statistics.value)
                pebbleMessenger?.sendState(RunStatus.READY)
                pebbleMessenger?.sendStatistics(_statistics.value)
                resetTimeoutTimer()
            }
        }
        if (RunState.status.value != RunStatus.ACTIVE) { rawLocationWindow.clear(); return }
        if (rawPrev != null) {
            val d = LocationUtils.calculateDistance(rawPrev.latitude, rawPrev.longitude, location.latitude, location.longitude)
            val dt = (location.timestamp.toEpochMilliseconds() - rawPrev.timestamp.toEpochMilliseconds()) / 1000.0
            if (dt > 0 && (d / dt) > 40.0) return 
        }
        rawLocationWindow.add(location)
        if (rawLocationWindow.size > windowSize) rawLocationWindow.removeAt(0)
        val filteredLocation = calculateWeightedAverage(rawLocationWindow)
        val prevFiltered = lastProcessedLocation
        lastProcessedLocation = filteredLocation
        val finalLocation = filteredLocation.copy(heartRate = location.heartRate ?: _statistics.value.currentHeartRate, steps = _statistics.value.steps)
        fullRoute.add(finalLocation)
        if (prevFiltered == null) return
        val delta = LocationUtils.calculateDistance(prevFiltered.latitude, prevFiltered.longitude, filteredLocation.latitude, filteredLocation.longitude)
        val elevationDelta = if (prevFiltered.altitude != null && filteredLocation.altitude != null) (filteredLocation.altitude - prevFiltered.altitude).coerceAtLeast(0.0) else 0.0
        _statistics.update { it.copy(
            totalDistanceMeters = it.totalDistanceMeters + delta,
            totalElevationGain = it.totalElevationGain + elevationDelta,
            currentHeartRate = location.heartRate ?: it.currentHeartRate,
            heartRates = if (location.heartRate != null) it.heartRates + location.heartRate else it.heartRates,
            route = fullRoute.toList() 
        ).also { s ->
            val distInterval = appSettings?.notificationDistanceMeters ?: 0
            if (distInterval > 0) {
                val currentKmIdx = (s.totalDistanceMeters / distInterval).toInt()
                if (currentKmIdx > lastNotifiedDistanceKm) {
                    lastNotifiedDistanceKm = currentKmIdx
                    if (appSettings?.isAutoLaunchOnDistanceNotificationEnabled == true) pebbleMessenger?.launchWatchApp()
                    pebbleMessenger?.sendNotification(0) 
                }
            }
            pebbleMessenger?.sendStatistics(s)
            RunState.updateStats(s)
        } }
    }

    private fun calculateWeightedAverage(window: List<LocationPoint>): LocationPoint {
        if (window.isEmpty()) return LocationPoint(0.0, 0.0, timestamp = Clock.System.now())
        if (window.size == 1) return window.first()
        val latest = window.last()
        var totalWeight = 0.0; var latSum = 0.0; var lonSum = 0.0; var altSum = 0.0
        window.forEachIndexed { index, point ->
            var weight = (index + 1).toDouble().pow(2.0)
            if ((latest.speed ?: 0.0) > 5.0 && index < window.size - 1) weight *= 0.5
            latSum += point.latitude * weight; lonSum += point.longitude * weight; altSum += (point.altitude ?: 0.0) * weight; totalWeight += weight
        }
        return latest.copy(latitude = latSum / totalWeight, longitude = lonSum / totalWeight, altitude = if (latest.altitude != null) altSum / totalWeight else null)
    }

    private fun resetTimeoutTimer() {
        val currentStatus = RunState.status.value
        if (currentStatus != RunStatus.PREPARING && currentStatus != RunStatus.READY) { timeoutJob?.cancel(); timeoutJob = null; return }
        timeoutJob?.cancel()
        timeoutJob = scope.launch { delay(5 * 60 * 1000L); reset() }
    }
}
