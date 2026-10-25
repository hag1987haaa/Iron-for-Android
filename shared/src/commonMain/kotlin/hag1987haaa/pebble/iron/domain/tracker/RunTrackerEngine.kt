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
import hag1987haaa.pebble.iron.domain.ble.BleCscManager
import hag1987haaa.pebble.iron.domain.ble.BlePowerManager
import hag1987haaa.pebble.iron.util.LocationUtils
import hag1987haaa.pebble.iron.util.HealthUtils
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
    val currentCadence: Int? = null,
    val cadences: List<Int> = emptyList(),
    val isBleCscActive: Boolean = false,
    val currentPower: Int? = null,
    val powers: List<Int> = emptyList(),
    val isBlePowerActive: Boolean = false,
    val currentSensorSpeedKmh: Double? = null,
    val totalElevationGain: Double = 0.0,
    val route: List<LocationPoint> = emptyList(),
    val currentLocation: LocationPoint? = null,
    val hasGpsFix: Boolean = false,
    val status: RunStatus = RunStatus.IDLE,
    val hrSource: String = "PEBBLE",
    val isBleHrActive: Boolean = false,
    val latestBleHeartRate: Int? = null,
    val latestPebbleHeartRate: Int? = null
) {
    val avgCadence: Double? get() = if (cadences.isNotEmpty()) cadences.average() else null
    val maxCadence: Int? get() = cadences.maxOrNull()
    // 0回転（足止め・惰性走行）を除外したペダリング時平均ケイデンス
    val nonZeroAvgCadence: Double? get() {
        val nonZero = cadences.filter { it > 0 }
        return if (nonZero.isNotEmpty()) nonZero.average() else null
    }

    val avgPower: Double? get() = if (powers.isNotEmpty()) powers.average() else null
    val maxPower: Int? get() = powers.maxOrNull()

    // 直近N秒間の移動平均パワー（サイコンの 3s / 10s / 30s / 5m Power）
    fun getWindowedPower(windowSeconds: Int): Int? {
        if (powers.isEmpty()) return currentPower
        val takeCount = windowSeconds.coerceAtLeast(1)
        val window = powers.takeLast(takeCount)
        return if (window.isNotEmpty()) window.average().toInt() else null
    }

    val power3s: Int? get() = getWindowedPower(3)
    val power10s: Int? get() = getWindowedPower(10)
    val power30s: Int? get() = getWindowedPower(30)
    val power5m: Int? get() = getWindowedPower(300)

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
    val pebbleMessenger: PebbleMessenger? = null,
    private val appSettings: AppSettings? = null,
    private val bleHrManager: BleHeartRateManager? = null,
    private val bleCscManager: BleCscManager? = null,
    private val blePowerManager: BlePowerManager? = null,
    private val scope: CoroutineScope
) {
    private val _statistics = MutableStateFlow(RunStatistics(
        activityType = appSettings?.lastActivityType?.let { 
            try { ActivityType.valueOf(it) } catch(e: Exception) { ActivityType.RUNNING }
        } ?: ActivityType.RUNNING
    ))
    val statistics: StateFlow<RunStatistics> = _statistics.asStateFlow()
    var onTimeout: (() -> Unit)? = null

    private var lastAcceptedRawLocation: LocationPoint? = null
    private var lastDistanceLocation: LocationPoint? = null
    private val locationFilter = WorkoutLocationFilter()
    private val elevationFilter = WorkoutElevationFilter()
    private val locationSmoother = AccuracyAwareLocationSmoother()
    private val rawLocationWindow = mutableListOf<LocationPoint>()
    private val fullRoute = mutableListOf<LocationPoint>() 
    private val windowSize = 3

    private var trackingJob: Job? = null
    private var timerJob: Job? = null
    private var timeoutJob: Job? = null
    private var isStartPending = false
    private var isResumePending = false

    private var lastIncomingSteps: Int = -1
    private var totalAccumulatedSteps: Int = 0
    private var lastNotifiedDistanceKm: Int = 0
    private var lastNotifiedTimeCount: Int = 0

    // 設定変更を検知するための記憶用
    private var lastTimeStep: Int = -1
    private var lastDistStep: Float = -1.0f

    private var autoConnectJob: Job? = null

    // 心拍データ管理用
    private var lastHrTimestamp: Long = 0L // 最後に何らかの心拍データが届いた時刻
    private var lastBleValueChangeTimestamp: Long = 0L // BLEの数値が最後に「変化」した時刻
    private var lastBleBpm: Int? = null // 最後に届いたBLEの数値
    private var lastPebbleHrTimestamp: Long = 0L // Pebbleの心拍データが最後に届いた時刻
    private var lastBpmValue: Int? = null // 表示に使用している直近の有効な心拍数

    // 心拍ゾーン通知管理用
    private enum class HrZoneState { INITIAL, IN_ZONE, TOO_HIGH, TOO_LOW }
    private var hrZoneState: HrZoneState = HrZoneState.INITIAL
    private var secondsInTargetZone: Int = 0
    private var secondsTooHigh: Int = 0
    private var secondsTooLow: Int = 0

    init {
        // 心拍センサー連携
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

        // CSC (ケイデンス & スピード) センサー連携
        scope.launch {
            bleCscManager?.cadenceRpm?.collect { rpm ->
                if (appSettings?.isBleCscEnabled == true) {
                    addCadence(rpm)
                }
            }
        }
        scope.launch {
            bleCscManager?.wheelSpeedKmh?.collect { speed ->
                if (appSettings?.isBleCscEnabled == true) {
                    _statistics.update { it.copy(currentSensorSpeedKmh = speed) }
                }
            }
        }
        scope.launch {
            bleCscManager?.isDataActive?.collect { active ->
                _statistics.update { it.copy(isBleCscActive = active) }
            }
        }

        // Cycling Power (パワー & ケイデンス) センサー連携
        scope.launch {
            blePowerManager?.powerWatts?.collect { watts ->
                if (appSettings?.isBlePowerEnabled == true) {
                    addPower(watts)
                }
            }
        }
        scope.launch {
            blePowerManager?.cadenceRpm?.collect { rpm ->
                // パワーメーターからケイデンスが得られる場合、CSCが動いていなければフォールバックとして採用
                if (appSettings?.isBlePowerEnabled == true && (_statistics.value.currentCadence == null || !_statistics.value.isBleCscActive)) {
                    addCadence(rpm)
                }
            }
        }
        scope.launch {
            blePowerManager?.isDataActive?.collect { active ->
                _statistics.update { it.copy(isBlePowerActive = active) }
            }
        }

        startSmartAutoConnect()
    }

    private fun reEvaluateHeartRateSource() {
        val now = Clock.System.now().toEpochMilliseconds()
        val stats = _statistics.value

        // 1. BLEソースの信頼性チェック
        val isBleActive = bleHrManager?.isDataActive?.value ?: false
        val isBlePreferred = appSettings?.preferBleHeartRate ?: true

        // 通信が10秒以上途絶えているか、数値が15秒以上変化していない（ホールド状態）場合は「信頼不可」
        val isBleStale = lastHrTimestamp > 0 && (now - lastHrTimestamp) > 10000L
        val isBleFrozen = lastBleValueChangeTimestamp > 0 && (now - lastBleValueChangeTimestamp) > 15000L
        val isBleValidValue = (stats.latestBleHeartRate ?: 0) in 30..220

        val isBleReliable = isBleActive && !isBleStale && !isBleFrozen && isBleValidValue
        val isPebbleStale = lastPebbleHrTimestamp > 0 && (now - lastPebbleHrTimestamp) > 20000L
        val isPebbleReliable = (stats.latestPebbleHeartRate ?: 0) in 30..220 && (lastPebbleHrTimestamp > 0 && !isPebbleStale)

        // 2. メインソースの決定
        val (newSource, newBpm) = when {
            isBleReliable && isPebbleReliable -> {
                if (isBlePreferred) "BLE" to stats.latestBleHeartRate
                else "PEBBLE" to stats.latestPebbleHeartRate
            }
            isBleReliable -> "BLE" to stats.latestBleHeartRate
            isPebbleReliable -> "PEBBLE" to stats.latestPebbleHeartRate
            else -> {
                val fallbackSource = if (isBlePreferred) "BLE" else "PEBBLE"
                fallbackSource to null
            }
        }

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

            // --- 1. CSC センサー接続試行 ---
            if (settings.isBleCscEnabled && bleCscManager?.isConnected?.value != true) {
                settings.preferredBleCscAddress?.let {
                    println("RunTrackerEngine AutoConnect: Attempting preferred CSC $it")
                    bleCscManager?.connect(it)
                } ?: settings.bleCscDeviceAddress?.let {
                    println("RunTrackerEngine AutoConnect: Attempting last CSC $it")
                    bleCscManager?.connect(it)
                }
            }

            // --- 2. Cycling Power センサー接続試行 ---
            if (settings.isBlePowerEnabled && blePowerManager?.isConnected?.value != true) {
                settings.preferredBlePowerAddress?.let {
                    println("RunTrackerEngine AutoConnect: Attempting preferred Power $it")
                    blePowerManager?.connect(it)
                } ?: settings.blePowerDeviceAddress?.let {
                    println("RunTrackerEngine AutoConnect: Attempting last Power $it")
                    blePowerManager?.connect(it)
                }
            }

            // --- 3. 心拍センサー接続試行 ---
            if (!settings.isBleHeartRateEnabled) {
                if (hrManager.isConnected.value) hrManager.close()
                return@launch
            }

            if (hrManager.isConnected.value) return@launch

            delay(1000)

            settings.preferredBleHrAddress?.let { 
                println("RunTrackerEngine AutoConnect: Attempting preferred $it")
                hrManager.connect(it)
                delay(5000)
                if (hrManager.isConnected.value) return@launch
            }

            settings.bleHeartRateDeviceAddress?.let {
                if (it != settings.preferredBleHrAddress) {
                    println("RunTrackerEngine AutoConnect: Attempting last used $it")
                    hrManager.connect(it)
                    delay(5000)
                    if (hrManager.isConnected.value) return@launch
                }
            }

            val registeredAddresses = settings.registeredBleHrDevices.map { it.substringBefore("|") }
            if (registeredAddresses.isEmpty()) return@launch

            while (isActive && settings.isBleHeartRateEnabled) {
                if (hrManager.isConnected.value) break

                println("RunTrackerEngine AutoConnect: Scanning for registered devices...")
                hag1987haaa.pebble.iron.KmpDependencies.bleScanner.startScan("0000180d-0000-1000-8000-00805f9b34fb")
                try {
                    withTimeout(15000) {
                        hag1987haaa.pebble.iron.KmpDependencies.bleScanner.foundDevices.collect { devices ->
                            val match = devices.find { it.address in registeredAddresses }
                            if (match != null) {
                                println("RunTrackerEngine AutoConnect: Found registered device ${match.name}")
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

    fun warmupGps() {
        if (_statistics.value.status != RunStatus.IDLE) return
        scope.launch {
            try {
                val lastLoc = locationTracker.getLastKnownLocation()
                if (lastLoc != null && _statistics.value.currentLocation == null) {
                    _statistics.update { it.copy(currentLocation = lastLoc) }
                    RunState.updateStats(_statistics.value)
                }
            } catch (_: Exception) {}
        }
    }

    fun setActivityType(type: ActivityType) {
        _statistics.update { it.copy(activityType = type) }
        appSettings?.let { it.lastActivityType = type.name; it.save() }
        val newStats = _statistics.value
        RunState.updateStats(newStats)

        pebbleMessenger?.sendState(newStats.status, newStats)
    }

    fun launchWatchApp() { pebbleMessenger?.launchWatchApp() }

    fun prepare() {
        clearWorkoutData()

        if (trackingJob != null) return
        startSmartAutoConnect()

        _statistics.update { it.copy(status = RunStatus.PREPARING) }
        RunState.updateStats(_statistics.value)
        RunState.setStatus(RunStatus.PREPARING)
        resetTimeoutTimer()

        pebbleMessenger?.launchWatchApp()
        pebbleMessenger?.requestWatchInfo()
        pebbleMessenger?.sendState(RunStatus.PREPARING, _statistics.value)
        pebbleMessenger?.sendGraphData(_statistics.value)

        scope.launch {
            try {
                val lastLoc = locationTracker.getLastKnownLocation()
                if (lastLoc != null && !_statistics.value.hasGpsFix) {
                    _statistics.update { it.copy(currentLocation = lastLoc) }
                    RunState.updateStats(_statistics.value)
                }
            } catch (_: Exception) {}
        }

        trackingJob = locationTracker.startTracking().onEach { handleNewLocation(it) }.launchIn(scope)
    }

    private fun clearWorkoutData() {
        timeoutJob?.cancel(); timeoutJob = null
        trackingJob?.cancel(); trackingJob = null
        timerJob?.cancel(); timerJob = null
        locationTracker.stopTracking()

        _statistics.update { current ->
            RunStatistics(activityType = current.activityType)
        }
        RunState.updateStats(_statistics.value)

        lastAcceptedRawLocation = null; lastDistanceLocation = null
        elevationFilter.reset()
        rawLocationWindow.clear(); fullRoute.clear()
        pebbleMessenger?.clearMapCache()

        isStartPending = false
        isResumePending = false
        lastIncomingSteps = -1; totalAccumulatedSteps = 0
        lastNotifiedDistanceKm = 0; lastNotifiedTimeCount = 0
        lastTimeStep = -1; lastDistStep = -1.0f
        lastHrTimestamp = 0L; lastBpmValue = null
        lastBleValueChangeTimestamp = 0L; lastBleBpm = null; lastPebbleHrTimestamp = 0L
        resetHrZoneState()

        bleHrManager?.close()
        bleCscManager?.close()
        blePowerManager?.close()
    }

    fun start() {
        if (!_statistics.value.hasGpsFix) { isStartPending = true; pebbleMessenger?.sendState(RunStatus.PREPARING, _statistics.value); return }
        isStartPending = false
        isResumePending = false; timeoutJob?.cancel()

        _statistics.update { it.copy(startTime = Clock.System.now(), status = RunStatus.ACTIVE) }
        RunState.updateStats(_statistics.value)

        RunState.setStatus(RunStatus.ACTIVE)
        pebbleMessenger?.sendState(RunStatus.ACTIVE, _statistics.value)
        pebbleMessenger?.sendGraphData(_statistics.value)
        startTimer()
    }

    fun pause() {
        timerJob?.cancel(); timerJob = null

        _statistics.update { it.copy(status = RunStatus.PAUSED) }
        RunState.updateStats(_statistics.value)
        RunState.setStatus(RunStatus.PAUSED)

        pebbleMessenger?.sendState(RunStatus.PAUSED, _statistics.value)
    }

    fun resume() {
        isResumePending = true
        rawLocationWindow.clear()
        lastAcceptedRawLocation = null
        lastDistanceLocation = null
        elevationFilter.reset()
        _statistics.update { it.copy(status = RunStatus.ACTIVE) }
        RunState.updateStats(_statistics.value)

        RunState.setStatus(RunStatus.ACTIVE)
        pebbleMessenger?.sendState(RunStatus.ACTIVE, _statistics.value)
        pebbleMessenger?.sendGraphData(_statistics.value)

        startTimer()
    }

    fun finish() {
        timerJob?.cancel(); timerJob = null
        trackingJob?.cancel(); trackingJob = null
        locationTracker.stopTracking()

        val now = Clock.System.now(); val localTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val defaultName = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}-${localTime.hour.toString().padStart(2, '0')}${localTime.minute.toString().padStart(2, '0')}"

        _statistics.update { it.copy(status = RunStatus.FINISHED, name = defaultName) }
        RunState.updateStats(_statistics.value)
        RunState.setStatus(RunStatus.FINISHED)

        pebbleMessenger?.sendState(RunStatus.FINISHED, _statistics.value)
    }

    fun discard() { reset() }
    fun saveToResult() {
        timerJob?.cancel(); trackingJob?.cancel(); locationTracker.stopTracking()
        _statistics.update { it.copy(status = RunStatus.RESULT) }
        RunState.setStatus(RunStatus.RESULT); pebbleMessenger?.sendState(RunStatus.RESULT, _statistics.value)
    }

    fun resetToIdle() { reset() }

    fun addHeartRate(bpm: Int, source: String = "PEBBLE") {
        val currentStatus = RunState.status.value
        if (currentStatus == RunStatus.IDLE || currentStatus == RunStatus.RESULT || currentStatus == RunStatus.FINISHED) return

        val now = Clock.System.now().toEpochMilliseconds()
        val validBpm = if (bpm in 30..220) bpm else null

        if (source == "BLE") {
            if (validBpm != null && validBpm != lastBleBpm) {
                lastBleValueChangeTimestamp = now
                lastBleBpm = validBpm
            }
            lastHrTimestamp = now
        } else {
            if (validBpm != null && validBpm != lastBpmValue) {
                lastBpmValue = validBpm
            }
            if (validBpm != null) {
                lastPebbleHrTimestamp = now
            }
        }

        _statistics.update { stats ->
            if (source == "BLE") stats.copy(latestBleHeartRate = validBpm)
            else stats.copy(latestPebbleHeartRate = validBpm)
        }

        reEvaluateHeartRateSource()

        val mainBpm = _statistics.value.currentHeartRate ?: return
        _statistics.update { stats ->
            val updatedRoute = if (stats.status == RunStatus.ACTIVE && stats.route.isNotEmpty()) {
                val lastPoint = stats.route.last()
                stats.route.dropLast(1) + lastPoint.copy(heartRate = mainBpm)
            } else stats.route
            stats.copy(heartRates = stats.heartRates + mainBpm, route = updatedRoute).also { s -> 
                RunState.updateStats(s)
                if (s.status == RunStatus.ACTIVE || s.status == RunStatus.PAUSED) {
                    pebbleMessenger?.sendStatistics(s)
                }
            }
        }
    }

    fun addCadence(rpm: Int?) {
        val currentStatus = RunState.status.value
        if (currentStatus == RunStatus.IDLE || currentStatus == RunStatus.RESULT || currentStatus == RunStatus.FINISHED) return
        val validRpm = rpm?.takeIf { it in 0..250 }

        _statistics.update { stats ->
            val updatedCadences = if (stats.status == RunStatus.ACTIVE && validRpm != null && validRpm > 0) {
                stats.cadences + validRpm
            } else stats.cadences

            val updatedRoute = if (stats.status == RunStatus.ACTIVE && stats.route.isNotEmpty()) {
                val lastPoint = stats.route.last()
                stats.route.dropLast(1) + lastPoint.copy(cadence = validRpm)
            } else stats.route

            stats.copy(
                currentCadence = validRpm,
                cadences = updatedCadences,
                route = updatedRoute
            ).also { s ->
                RunState.updateStats(s)
                if (s.status == RunStatus.ACTIVE || s.status == RunStatus.PAUSED) {
                    pebbleMessenger?.sendStatistics(s)
                }
            }
        }
    }

    fun addPower(watts: Int?) {
        val currentStatus = RunState.status.value
        if (currentStatus == RunStatus.IDLE || currentStatus == RunStatus.RESULT || currentStatus == RunStatus.FINISHED) return
        val validWatts = watts?.takeIf { it >= 0 }

        _statistics.update { stats ->
            val updatedPowers = if (stats.status == RunStatus.ACTIVE && validWatts != null) {
                stats.powers + validWatts
            } else stats.powers

            val updatedRoute = if (stats.status == RunStatus.ACTIVE && stats.route.isNotEmpty()) {
                val lastPoint = stats.route.last()
                stats.route.dropLast(1) + lastPoint.copy(power = validWatts)
            } else stats.route

            stats.copy(
                currentPower = validWatts,
                powers = updatedPowers,
                route = updatedRoute
            ).also { s ->
                RunState.updateStats(s)
                if (s.status == RunStatus.ACTIVE || s.status == RunStatus.PAUSED) {
                    pebbleMessenger?.sendStatistics(s)
                }
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

    fun triggerStatisticsUpdate() { 
        startSmartAutoConnect()
        pebbleMessenger?.sendFullSync(_statistics.value) 
    }
    fun rotateGraphType() { pebbleMessenger?.rotateGraphType(_statistics.value) }
    fun rotateMidData() { pebbleMessenger?.rotateMidData(_statistics.value) }
    fun sendTouchConfig(enabled: Boolean) { pebbleMessenger?.sendTouchConfig(enabled) }

    fun setCurrentMidId(id: Int) { pebbleMessenger?.setCurrentMidId(id) }
    fun setCurrentLowerId(id: Int) { pebbleMessenger?.setCurrentLowerId(id) }
    fun openMap() { pebbleMessenger?.openMap() }
    fun setMapState(isActive: Boolean) { pebbleMessenger?.setMapState(isActive) }

    val isMapActive: Boolean get() = pebbleMessenger?.isMapActive ?: false
    fun zoomInMap() { pebbleMessenger?.zoomInMap() }
    fun zoomOutMap() { pebbleMessenger?.zoomOutMap() }
    fun recenterMap() { pebbleMessenger?.recenterMap() }
    fun panMap(dx: Int, dy: Int) { pebbleMessenger?.panMap(dx, dy) }
    fun resetAndToggleMapOrientation() { pebbleMessenger?.resetAndToggleMapOrientation() }
    fun resetMapAutoCloseTimer() { pebbleMessenger?.resetMapAutoCloseTimer() }

    private fun reset() {
        clearWorkoutData()
        RunState.setStatus(RunStatus.IDLE)
        pebbleMessenger?.sendState(RunStatus.IDLE, _statistics.value)
    }

    private fun startTimer() {
        if (timerJob != null) return
        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)

                if (_statistics.value.status != RunStatus.ACTIVE) continue

                reEvaluateHeartRateSource()

                var triggerTimeNotif = false
                var triggerDistNotif = false

                _statistics.update { stats ->
                    if (stats.status != RunStatus.ACTIVE) return@update stats

                    val nextSeconds = stats.totalSeconds + 1
                    val weight = appSettings?.userWeightKg ?: 70.0f
                    val currentCalories = HealthUtils.calculateCalories(stats.activityType, weight, nextSeconds, stats.totalDistanceMeters, stats.totalElevationGain, if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null)

                    // パワー & ケイデンスの毎秒サンプリング（移動平均計算用）
                    val pwrSample = stats.currentPower ?: if (stats.isBlePowerActive) 0 else null
                    val updatedPowers = if (pwrSample != null) stats.powers + pwrSample else stats.powers

                    val cadSample = stats.currentCadence ?: if (stats.isBleCscActive) 0 else null
                    val updatedCadences = if (cadSample != null) stats.cadences + cadSample else stats.cadences

                    val updatedStats = stats.copy(
                        totalSeconds = nextSeconds, 
                        calories = currentCalories,
                        powers = updatedPowers,
                        cadences = updatedCadences
                    )

                    val timeStep = appSettings?.notificationTimeSeconds ?: 0
                    if (timeStep > 0) {
                        val currentIntervalCount = (nextSeconds / timeStep).toInt()

                        if (lastTimeStep != -1 && lastTimeStep != timeStep) {
                            lastNotifiedTimeCount = currentIntervalCount
                        }
                        lastTimeStep = timeStep

                        if (currentIntervalCount > lastNotifiedTimeCount) {
                            triggerTimeNotif = true
                        } else if (currentIntervalCount < lastNotifiedTimeCount) {
                            lastNotifiedTimeCount = currentIntervalCount
                        }
                    }

                    val distStep = appSettings?.notificationDistanceStep ?: 0.0f
                    if (distStep > 0.0f) {
                        val unitMeters = if (appSettings?.isMetric == true) 1000.0 else 1609.344
                        val threshold = distStep * unitMeters
                        val currentLapIdx = (updatedStats.totalDistanceMeters / threshold).toInt()

                        if (lastDistStep != -1.0f && lastDistStep != distStep) {
                            lastNotifiedDistanceKm = currentLapIdx
                        }
                        lastDistStep = distStep

                        if (currentLapIdx > lastNotifiedDistanceKm) {
                            triggerDistNotif = true
                        } else if (currentLapIdx < lastNotifiedDistanceKm) {
                            lastNotifiedDistanceKm = currentLapIdx
                        }
                    }

                    updatedStats
                }

                val s = _statistics.value
                if (s.status != RunStatus.ACTIVE) continue

                if (triggerTimeNotif) {
                    val timeStep = appSettings?.notificationTimeSeconds ?: 0
                    lastNotifiedTimeCount = (s.totalSeconds / timeStep).toInt()
                    println("RunTrackerEngine: Triggering Time Notification at ${s.totalSeconds}s")
                    if (appSettings?.isAutoLaunchOnTimeNotificationEnabled == true) pebbleMessenger?.launchWatchApp()
                    if (appSettings?.isTimeNotificationVibrationEnabled != false) {
                        pebbleMessenger?.sendNotification(1)
                    }
                    val timeMapDelay = appSettings?.autoShowMapAfterTimeNotificationSeconds ?: 0
                    if (timeMapDelay > 0) {
                        scope.launch {
                            delay(timeMapDelay * 1000L)
                            if (_statistics.value.status == RunStatus.ACTIVE) {
                                println("RunTrackerEngine: Auto-opening map $timeMapDelay s after time notification")
                                openMap()
                            }
                        }
                    }
                }

                if (triggerDistNotif) {
                    val distStep = appSettings?.notificationDistanceStep ?: 0.0f
                    val unitMeters = if (appSettings?.isMetric == true) 1000.0 else 1609.344
                    val threshold = distStep * unitMeters
                    lastNotifiedDistanceKm = (s.totalDistanceMeters / threshold).toInt()
                    println("RunTrackerEngine: Triggering Distance Notification at ${s.totalDistanceMeters}m")
                    if (appSettings?.isAutoLaunchOnDistanceNotificationEnabled == true) pebbleMessenger?.launchWatchApp()
                    if (appSettings?.isDistNotificationVibrationEnabled != false) {
                        pebbleMessenger?.sendNotification(0)
                    }
                    val distMapDelay = appSettings?.autoShowMapAfterDistNotificationSeconds ?: 0
                    if (distMapDelay > 0) {
                        scope.launch {
                            delay(distMapDelay * 1000L)
                            if (_statistics.value.status == RunStatus.ACTIVE) {
                                println("RunTrackerEngine: Auto-opening map $distMapDelay s after dist notification")
                                openMap()
                            }
                        }
                    }
                }

                if (appSettings?.isHrZoneNotificationEnabled == true) {
                    checkHeartRateZoneNotification(s.currentHeartRate)
                } else {
                    resetHrZoneState()
                }

                if (s.totalSeconds % 10 == 0L) {
                    pebbleMessenger?.sendGraphData(s)
                }

                pebbleMessenger?.sendStatistics(s)
                RunState.updateStats(s)
            }
        }
    }

    internal fun checkHeartRateZoneNotification(currentHr: Int?) {
        val settings = appSettings ?: return
        if (!settings.isHrZoneNotificationEnabled) {
            resetHrZoneState()
            return
        }

        if (currentHr == null || currentHr <= 0) {
            secondsInTargetZone = 0
            secondsTooHigh = 0
            secondsTooLow = 0
            return
        }

        val currentType = _statistics.value.activityType
        val minBpm = settings.getHrZoneMin(currentType)
        val maxBpm = settings.getHrZoneMax(currentType)
        val outDuration = settings.hrZoneOutDurationSeconds.coerceAtLeast(3)
        val repeatInterval = settings.hrZoneRepeatIntervalSeconds

        when {
            currentHr > maxBpm -> {
                secondsTooHigh++
                secondsTooLow = 0
                secondsInTargetZone = 0

                if (hrZoneState != HrZoneState.TOO_HIGH && secondsTooHigh >= outDuration) {
                    hrZoneState = HrZoneState.TOO_HIGH
                    secondsTooHigh = 0
                    println("RunTrackerEngine: HR too high ($currentHr bpm > $maxBpm bpm for ${outDuration}s)")
                    pebbleMessenger?.sendNotification(21)
                } else if (hrZoneState == HrZoneState.TOO_HIGH && repeatInterval > 0 && secondsTooHigh >= repeatInterval) {
                    secondsTooHigh = 0
                    println("RunTrackerEngine: HR still too high ($currentHr bpm > $maxBpm bpm, repeated after ${repeatInterval}s)")
                    pebbleMessenger?.sendNotification(21)
                }
            }
            currentHr < minBpm -> {
                secondsTooLow++
                secondsTooHigh = 0
                secondsInTargetZone = 0

                if (hrZoneState != HrZoneState.TOO_LOW && secondsTooLow >= outDuration) {
                    hrZoneState = HrZoneState.TOO_LOW
                    secondsTooLow = 0
                    println("RunTrackerEngine: HR too low ($currentHr bpm < $minBpm bpm for ${outDuration}s)")
                    pebbleMessenger?.sendNotification(22)
                } else if (hrZoneState == HrZoneState.TOO_LOW && repeatInterval > 0 && secondsTooLow >= repeatInterval) {
                    secondsTooLow = 0
                    println("RunTrackerEngine: HR still too low ($currentHr bpm < $minBpm bpm, repeated after ${repeatInterval}s)")
                    pebbleMessenger?.sendNotification(22)
                }
            }
            else -> {
                secondsInTargetZone++
                secondsTooHigh = 0
                secondsTooLow = 0

                if (hrZoneState != HrZoneState.IN_ZONE && secondsInTargetZone >= 3) {
                    hrZoneState = HrZoneState.IN_ZONE
                    secondsInTargetZone = 0
                    println("RunTrackerEngine: HR in zone ($currentHr bpm in $minBpm..$maxBpm for 3s)")
                    pebbleMessenger?.sendNotification(20)
                }
            }
        }
    }

    internal fun resetHrZoneState() {
        hrZoneState = HrZoneState.INITIAL
        secondsInTargetZone = 0
        secondsTooHigh = 0
        secondsTooLow = 0
    }

    private fun handleNewLocation(location: LocationPoint) {
        if (RunState.status.value == RunStatus.IDLE) return

        val activityType = _statistics.value.activityType
        if (!locationFilter.isAcceptable(
                lastAcceptedRawLocation,
                location,
                activityType,
            )
        ) {
            return
        }
        lastAcceptedRawLocation = location

        if (!_statistics.value.hasGpsFix) {
            _statistics.update {
                it.copy(hasGpsFix = true, currentLocation = location)
            }
            if (isStartPending) {
                start()
            } else if (RunState.status.value == RunStatus.PREPARING) {
                _statistics.update {
                    it.copy(
                        status = RunStatus.READY,
                        currentLocation = location,
                    )
                }
                RunState.setStatus(RunStatus.READY)
                RunState.updateStats(_statistics.value)
                pebbleMessenger?.sendState(
                    RunStatus.READY,
                    _statistics.value,
                )
                resetTimeoutTimer()

                if (appSettings?.isAutoShowMapOnReadyEnabled == true) {
                    val defaultZoom = when (activityType) {
                        hag1987haaa.pebble.iron.domain.model.ActivityType.WALKING,
                        hag1987haaa.pebble.iron.domain.model.ActivityType.HIKING -> 16
                        hag1987haaa.pebble.iron.domain.model.ActivityType.CYCLING -> 14
                        else -> 15
                    }
                    pebbleMessenger?.setMapZoom(defaultZoom)
                    scope.launch {
                        kotlinx.coroutines.delay(600)
                        if (RunState.status.value == RunStatus.READY) {
                            pebbleMessenger?.sendMap(listOf(location), 0, 0)
                        }
                    }
                } else if (pebbleMessenger?.isMapActive == true) {
                    scope.launch {
                        kotlinx.coroutines.delay(600)
                        if (pebbleMessenger?.isMapActive == true) {
                            pebbleMessenger?.sendMap(listOf(location), 0, 0)
                        }
                    }
                }
            }
        } else {
            _statistics.update { it.copy(currentLocation = location) }
            RunState.updateStats(_statistics.value)
        }

        if (RunState.status.value != RunStatus.ACTIVE) return

        val startTime = _statistics.value.startTime
        if (startTime != null &&
            location.timestamp.toEpochMilliseconds() < startTime.toEpochMilliseconds() - 15_000L
        ) {
            return
        }

        rawLocationWindow.add(location)
        if (rawLocationWindow.size > windowSize) {
            rawLocationWindow.removeAt(0)
        }
        val filteredLocation = locationSmoother.smooth(rawLocationWindow)

        val isSegStart = isResumePending || fullRoute.isEmpty()
        isResumePending = false

        var distanceDelta = 0.0

        val distanceAnchor = lastDistanceLocation
        if (isSegStart || distanceAnchor == null) {
            lastDistanceLocation = filteredLocation
        } else {
            val distanceFromAnchor = LocationUtils.calculateDistance(
                distanceAnchor.latitude,
                distanceAnchor.longitude,
                filteredLocation.latitude,
                filteredLocation.longitude,
            )

            if (locationFilter.shouldAccumulateDistance(
                    distanceAnchor,
                    filteredLocation,
                    distanceFromAnchor,
                )
            ) {
                distanceDelta = distanceFromAnchor
                lastDistanceLocation = filteredLocation
            }
        }

        val elevationDelta = elevationFilter.process(
            candidate = location,
            isSegmentStart = isSegStart,
        )

        val prevTimestampMs = fullRoute.lastOrNull()?.timestamp?.toEpochMilliseconds() ?: Long.MIN_VALUE
        val safeTimestampMs = maxOf(filteredLocation.timestamp.toEpochMilliseconds(), prevTimestampMs)
        val safeTimestamp = if (safeTimestampMs == filteredLocation.timestamp.toEpochMilliseconds()) {
            filteredLocation.timestamp
        } else {
            Instant.fromEpochMilliseconds(safeTimestampMs)
        }

        val statsNow = _statistics.value
        val finalLocation = filteredLocation.copy(
            timestamp = safeTimestamp,
            heartRate = location.heartRate ?: statsNow.currentHeartRate,
            cadence = statsNow.currentCadence,
            power = statsNow.currentPower,
            sensorSpeed = statsNow.currentSensorSpeedKmh?.let { it / 3.6 },
            steps = statsNow.steps,
            isSegmentStart = isSegStart,
        )

        fullRoute.add(finalLocation)

        _statistics.update {
            it.copy(
                totalDistanceMeters =
                    it.totalDistanceMeters + distanceDelta,
                totalElevationGain =
                    it.totalElevationGain + elevationDelta,
                route = fullRoute.toList(),
            ).also { s ->
                pebbleMessenger?.sendStatistics(s)
                RunState.updateStats(s)
            }
        }
    }

    private fun resetTimeoutTimer() {
        val currentStatus = RunState.status.value
        if (currentStatus != RunStatus.PREPARING && currentStatus != RunStatus.READY) return
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(5 * 60 * 1000L)
            println("RunTrackerEngine: Timeout reached in PREPARING/READY (5 min). Resetting to IDLE.")
            reset()
            onTimeout?.invoke()
        }
    }
}
