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
    private var lastNotifiedTimeCount: Int = 0
    private var autoConnectJob: Job? = null

    // 心拍データ管理用
    private var lastHrTimestamp: Long = 0L // 最後に何らかの心拍データが届いた時刻
    private var lastBleValueChangeTimestamp: Long = 0L // BLEの数値が最後に「変化」した時刻
    private var lastBleBpm: Int? = null // 最後に届いたBLEの数値
    private var lastBpmValue: Int? = null // 表示に使用している直近の有効な心拍数

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
        
        // 改善：設定画面での ON/OFF 切り替えに即座に反応して自動接続を開始/停止する
        scope.launch {
            val settings = appSettings ?: return@launch
            // SettingsViewModel 等で settings.save() が呼ばれるたびに
            // 内部的な callback (onSettingsChanged) 経由でここをトリガーできると理想的だが、
            // 今はシンプルかつ確実に、ステータス変更時に startSmartAutoConnect を呼ぶように
            // 外部（ViewModel）から triggerStatisticsUpdate 経由などで誘導する。
            // ここでは初期起動時の開始のみ行う。
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
        
        // 2. メインソースの決定（BLEが信頼でき、かつ優先設定ならBLE、そうでなければPEBBLE）
        val newSource = if (isBleReliable && isBlePreferred) "BLE" else "PEBBLE"
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
            
            // 無効化されている場合は何もしない（既に接続されているなら切断する）
            if (!settings.isBleHeartRateEnabled) {
                if (hrManager.isConnected.value) hrManager.close()
                return@launch
            }
            
            // 既に接続されているなら何もしない
            if (hrManager.isConnected.value) return@launch

            delay(1000)
            
            // 1. 最優先（お気に入り）デバイスへの接続試行
            settings.preferredBleHrAddress?.let { 
                println("RunTrackerEngine AutoConnect: Attempting preferred $it")
                hrManager.connect(it)
                delay(5000)
                if (hrManager.isConnected.value) return@launch
            }

            // 2. 最後に使ったデバイスへの接続試行
            settings.bleHeartRateDeviceAddress?.let {
                if (it != settings.preferredBleHrAddress) {
                    println("RunTrackerEngine AutoConnect: Attempting last used $it")
                    hrManager.connect(it)
                    delay(5000)
                    if (hrManager.isConnected.value) return@launch
                }
            }

            // 3. 登録済みリスト全件を対象としたスキャン＆自動接続ループ
            val registeredAddresses = settings.registeredBleHrDevices.map { it.substringBefore("|") }
            if (registeredAddresses.isEmpty()) return@launch

            while (isActive && settings.isBleHeartRateEnabled) {
                // 接続されたらループ終了
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
                
                // 見つからなかった場合はしばらく待って再試行
                delay(10000)
            }
        }
    }

    fun setActivityType(type: ActivityType) {
        _statistics.update { it.copy(activityType = type) }
        appSettings?.let { it.lastActivityType = type.name; it.save() }
        val newStats = _statistics.value
        RunState.updateStats(newStats)
        
        // 改善：種別変更を即座に「確定した状態」としてウォッチに送り出す
        // これにより、他の定期更新に上書きされる前にウォッチ側の表示を書き換える
        pebbleMessenger?.sendState(newStats.status, newStats)
    }

    fun launchWatchApp() { pebbleMessenger?.launchWatchApp() }

    fun prepare() {
        if (RunState.status.value == RunStatus.RESULT || RunState.status.value == RunStatus.IDLE) clearWorkoutData()
        if (trackingJob != null) return
        startSmartAutoConnect()
        _statistics.update { it.copy(status = RunStatus.PREPARING) }
        RunState.updateStats(_statistics.value)
        RunState.setStatus(RunStatus.PREPARING)
        pebbleMessenger?.launchWatchApp(); pebbleMessenger?.sendState(RunStatus.PREPARING, _statistics.value)
        trackingJob = locationTracker.startTracking().onEach { handleNewLocation(it) }.launchIn(scope)
    }

    private fun clearWorkoutData() {
        isStartPending = false; timeoutJob?.cancel(); timeoutJob = null; trackingJob?.cancel(); trackingJob = null; timerJob?.cancel(); timerJob = null
        _statistics.value = RunStatistics(activityType = _statistics.value.activityType)
        RunState.updateStats(_statistics.value); lastProcessedLocation = null; lastRawLocation = null; rawLocationWindow.clear(); fullRoute.clear()
        lastHrTimestamp = 0L; lastBpmValue = null
    }

    fun start() {
        if (!_statistics.value.hasGpsFix) { isStartPending = true; pebbleMessenger?.sendState(RunStatus.PREPARING, _statistics.value); return }
        isStartPending = false; timeoutJob?.cancel()
        
        // 先に統計データの状態を更新
        _statistics.update { it.copy(startTime = Clock.System.now(), status = RunStatus.ACTIVE) }
        RunState.updateStats(_statistics.value)
        
        // その後にグローバル状態を更新（これによりService側の同期が最新データを掴む）
        RunState.setStatus(RunStatus.ACTIVE)
        pebbleMessenger?.sendState(RunStatus.ACTIVE, _statistics.value)
        startTimer()
    }

    fun pause() {
        // 1. まずタイマーを即座に停止し、バックグラウンドからの意図しない送信を断つ
        timerJob?.cancel(); timerJob = null
        
        // 2. その後、静止した状態でステータスを更新
        _statistics.update { it.copy(status = RunStatus.PAUSED) }
        RunState.updateStats(_statistics.value)
        RunState.setStatus(RunStatus.PAUSED)
        
        // 3. 最後に確定した情報をウォッチへ送る
        pebbleMessenger?.sendState(RunStatus.PAUSED, _statistics.value)
    }

    fun resume() {
        // 1つ前の位置をリセットするが、Windowは維持して精度を保つ
        lastProcessedLocation = null
        
        // 先に統計データを更新
        _statistics.update { it.copy(status = RunStatus.ACTIVE) }
        RunState.updateStats(_statistics.value)
        
        // その後にグローバル状態を更新
        RunState.setStatus(RunStatus.ACTIVE)
        pebbleMessenger?.sendState(RunStatus.ACTIVE, _statistics.value)

        startTimer()
    }

    fun finish() {
        // 1. タイマーとトラッキングを即座に停止
        timerJob?.cancel(); timerJob = null
        trackingJob?.cancel(); trackingJob = null
        locationTracker.stopTracking()
        
        val now = Clock.System.now(); val localTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val defaultName = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}-${localTime.hour.toString().padStart(2, '0')}${localTime.minute.toString().padStart(2, '0')}"
        
        // 2. ステータスと名前を更新
        _statistics.update { it.copy(status = RunStatus.FINISHED, name = defaultName) }
        RunState.updateStats(_statistics.value)
        RunState.setStatus(RunStatus.FINISHED)
        
        // 3. 確定情報を送る
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
        
        // 1. タイムスタンプと履歴の更新
        if (source == "BLE") {
            // BLEの場合：数値が変化したときのみ「変化時刻」を更新（ホールド検知用）
            if (validBpm != null && validBpm != lastBleBpm) {
                lastBleValueChangeTimestamp = now
                lastBleBpm = validBpm
            }
            lastHrTimestamp = now // 通信自体の生存確認
        } else {
            // Pebbleの場合：数値が変化した（または初回）場合のみ更新
            if (validBpm != null && validBpm != lastBpmValue) {
                lastBpmValue = validBpm
            }
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
                RunState.updateStats(s)
                // 修正：計測中（ACTIVE）または一時停止中（PAUSED）であれば統計を送信する。
                // これにより、休憩中の心拍数更新をウォッチに反映しつつ、
                // 開始前（PREPARING/READY）のウォッチ側での種別選択操作を邪魔しないようにする。
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
        startSmartAutoConnect() // 改善：同期リクエストのついでに自動接続の状態もチェック・再始動する
        pebbleMessenger?.sendFullSync(_statistics.value) 
    }
    fun rotateGraphType() { pebbleMessenger?.rotateGraphType(_statistics.value) }
    fun rotateMidData() { pebbleMessenger?.rotateMidData(_statistics.value) }
    fun sendTouchConfig(enabled: Boolean) { pebbleMessenger?.sendTouchConfig(enabled) }

    private fun reset() {
        timeoutJob?.cancel(); trackingJob?.cancel(); timerJob?.cancel(); locationTracker.stopTracking()
        _statistics.value = RunStatistics(activityType = _statistics.value.activityType)
        RunState.updateStats(_statistics.value); RunState.setStatus(RunStatus.IDLE); pebbleMessenger?.sendState(RunStatus.IDLE, _statistics.value)
        lastProcessedLocation = null; lastRawLocation = null; rawLocationWindow.clear(); fullRoute.clear()
        lastHrTimestamp = 0L; lastBpmValue = null; lastIncomingSteps = -1; totalAccumulatedSteps = 0
        lastNotifiedDistanceKm = 0; lastNotifiedTimeCount = 0
        bleHrManager?.close() 
    }

    private fun startTimer() {
        if (timerJob != null) return
        timerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1000)
                
                // ACTIVE 状態でない場合は送信・更新をスキップする（二重ガード）
                if (_statistics.value.status != RunStatus.ACTIVE) continue

                reEvaluateHeartRateSource()
                _statistics.update { stats ->
                    // 更新中にもう一度ステータスを確認（アトミック性の確保）
                    if (stats.status != RunStatus.ACTIVE) return@update stats

                    val nextSeconds = stats.totalSeconds + 1
                    val weight = appSettings?.userWeightKg ?: 70.0f
                    val currentCalories = HealthUtils.calculateCalories(stats.activityType, weight, nextSeconds, stats.totalDistanceMeters, stats.totalElevationGain, if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null)
                    stats.copy(totalSeconds = nextSeconds, calories = currentCalories).also { s ->
                        
                        // --- 時間通知判定 ---
                        val timeStep = appSettings?.notificationTimeSeconds ?: 0
                        if (timeStep > 0) {
                            val currentIntervalCount = (nextSeconds / timeStep).toInt()
                            if (currentIntervalCount > lastNotifiedTimeCount) {
                                lastNotifiedTimeCount = currentIntervalCount
                                if (appSettings?.isAutoLaunchOnTimeNotificationEnabled == true) pebbleMessenger?.launchWatchApp()
                                pebbleMessenger?.sendNotification(1) // 1: 時間通知
                            }
                        }

                        // --- 距離通知判定 (タイマー側へ集約) ---
                        val distStep = appSettings?.notificationDistanceStep ?: 0.0f
                        if (distStep > 0.0f) {
                            val unitMeters = if (appSettings?.isMetric == true) 1000.0 else 1609.344
                            val threshold = distStep * unitMeters
                            val currentLapIdx = (s.totalDistanceMeters / threshold).toInt()
                            if (currentLapIdx > lastNotifiedDistanceKm) {
                                lastNotifiedDistanceKm = currentLapIdx
                                if (appSettings?.isAutoLaunchOnDistanceNotificationEnabled == true) pebbleMessenger?.launchWatchApp()
                                pebbleMessenger?.sendNotification(0) // 0: 距離通知
                            }
                        }

                        // --- 定期的なグラフデータ更新 (10秒おき) ---
                        if (nextSeconds % 10 == 0L) {
                            pebbleMessenger?.sendGraphData(s)
                        }

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
                RunState.setStatus(RunStatus.READY); pebbleMessenger?.sendState(RunStatus.READY, _statistics.value)
            }
        }
        if (RunState.status.value != RunStatus.ACTIVE) return
        rawLocationWindow.add(location); if (rawLocationWindow.size > windowSize) rawLocationWindow.removeAt(0)
        val filteredLocation = calculateWeightedAverage(rawLocationWindow)
        
        // 1つ前の位置からの移動距離と標高差（獲得標高）を計算
        var distanceDelta = 0.0
        var elevationDelta = 0.0
        lastProcessedLocation?.let { prev ->
            distanceDelta = LocationUtils.calculateDistance(
                prev.latitude, prev.longitude,
                filteredLocation.latitude, filteredLocation.longitude
            )
            val prevAlt = prev.altitude
            val currAlt = filteredLocation.altitude
            if (prevAlt != null && currAlt != null && currAlt > prevAlt) {
                val diff = currAlt - prevAlt
                if (diff > 0.5) elevationDelta = diff // ノイズ対策: 0.5m以上の時のみ加算
            }
        }
        lastProcessedLocation = filteredLocation

        val finalLocation = filteredLocation.copy(
            heartRate = location.heartRate ?: _statistics.value.currentHeartRate, 
            steps = _statistics.value.steps
        )
        fullRoute.add(finalLocation)

        _statistics.update { it.copy(
            totalDistanceMeters = it.totalDistanceMeters + distanceDelta, 
            totalElevationGain = it.totalElevationGain + elevationDelta,
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
