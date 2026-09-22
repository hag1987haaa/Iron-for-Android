package hag1987haaa.pebble.iron.pebble

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.rebble.pebblekit2.client.DefaultPebbleInfoRetriever
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.firstOrNull
import hag1987haaa.pebble.iron.domain.tracker.RunStatistics
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.domain.tracker.PebbleMessenger
import hag1987haaa.pebble.iron.KmpDependencies
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

class AndroidPebbleMessenger(
    private val context: Context,
    private val settings: hag1987haaa.pebble.iron.domain.settings.AppSettings
) : PebbleMessenger {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandQueue = Channel<PebbleMessageRequest>(Channel.UNLIMITED)
    
    @Volatile
    private var nextStatsRequest: PebbleMessageRequest? = null
    @Volatile
    private var nextMidDataRequest: PebbleMessageRequest? = null
    @Volatile
    private var nextLowerDataRequest: PebbleMessageRequest? = null
    @Volatile
    private var nextGraphRequest: PebbleMessageRequest? = null

    private var cachedSender: DefaultPebbleSender? = null
    
    // そのワークアウト中に使う固定のリスト順序（計測中は一切変えない）
    private var sessionMidList: List<Int> = emptyList()
    private var sessionLowerList: List<Int> = emptyList()
    // 今実際に Pebble の画面に表示されている ID
    private var currentMidDataId: Int = -1
    private var currentLowerDataId: Int = -1
    private var currentGraphTypeId: Int = settings.lastGraphTypeId
    override var isMapActive: Boolean = false
    private var isMapTransferring: Boolean = false
    private var isTransmittingChunks: Boolean = false
    private var currentMapZoom: Int = 16
    private var panOffsetPixelsX: Double = 0.0
    private var panOffsetPixelsY: Double = 0.0
    @Volatile
    private var isHeadingUp: Boolean = false
    private var mapAutoCloseJob: kotlinx.coroutines.Job? = null

    // マップ操作の集約・保留制御（メモリ内のみ保持、保存・永続化は一切行わない）
    private var mapSendJob: kotlinx.coroutines.Job? = null
    private var mapDebounceJob: kotlinx.coroutines.Job? = null
    private var hasPendingMapRefresh: Boolean = false
    private var lastMapPoints: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>? = null
    private var lastMapWidth: Int = 144
    private var lastMapHeight: Int = 168
    private val tileCache = LruCache<String, Bitmap>(32)

    companion object {
        private val WATCHAPP_UUID = UUID.fromString("0ec71971-1191-4e05-87f5-27a3c749023c")
        private const val KEY_CMD = 10000u
        private const val KEY_TIME = 10001u
        private const val KEY_DISTANCE = 10002u
        private const val KEY_PACE = 10003u
        private const val KEY_STATE = 10004u
        private const val KEY_GRAPH_DATA = 10009u
        private const val KEY_HR = 10007u
        private const val KEY_STEPS = 10010u 
        private const val KEY_TOUCH_ENABLE = 10011u
        private const val KEY_TYPE = 10012u 
        private const val KEY_MID_DATA = 10013u 
        private const val KEY_HR_INTERVAL = 10014u
        private const val KEY_MID_ID = 10015u
        private const val KEY_LOWER_ID = 10016u
        private const val KEY_LOWER_DATA = 10017u
        private const val KEY_MAP_DATA = 10019u
        private const val KEY_MAP_CHUNK_IDX = 10020u
        private const val KEY_MAP_TOTAL_CHUNKS = 10021u
        private const val KEY_MAP_STATE = 10022u
        private const val KEY_COURSES_DATA = 10025u
        private const val SEND_TIMEOUT_MS = 2500L
    }

    init {
        scope.launch {
            while (isActive) {
                try {
                    var handledAnything = false
                    val cmd = commandQueue.tryReceive().getOrNull()
                    if (cmd != null) {
                        processRequest(cmd)
                        delay(150) 
                        handledAnything = true
                    }
                    val stats = nextStatsRequest
                    if (stats != null) {
                        nextStatsRequest = null 
                        processRequest(stats)
                        delay(100)
                        handledAnything = true
                    }
                    val mid = nextMidDataRequest
                    if (mid != null) {
                        nextMidDataRequest = null
                        processRequest(mid)
                        delay(100)
                        handledAnything = true
                    }
                    val lower = nextLowerDataRequest
                    if (lower != null) {
                        nextLowerDataRequest = null
                        processRequest(lower)
                        delay(100)
                        handledAnything = true
                    }
                    val graph = nextGraphRequest
                    if (graph != null) {
                        nextGraphRequest = null
                        processRequest(graph)
                        delay(150)
                        handledAnything = true
                    }
                    if (!handledAnything) delay(50)
                } catch (e: Exception) {
                    delay(500)
                }
            }
        }
    }

    private fun getSender(): DefaultPebbleSender = cachedSender ?: DefaultPebbleSender(context).also { cachedSender = it }

    private suspend fun processRequest(request: PebbleMessageRequest) {
        val targets = PebbleCommandService.lastConnectedWatch?.let { listOf(it) }
        if (request.retryCount > 0) {
            for (i in 0 until request.retryCount) {
                if (sendAttempt(request.tag, request.dictionary, targets)) return
                delay((i + 1) * 800L)
            }
        } else {
            sendAttempt(request.tag, request.dictionary, targets)
        }
    }

    private suspend fun sendAttempt(tag: String, dict: Map<UInt, PebbleDictionaryItem>, targets: List<WatchIdentifier>?): Boolean {
        return try {
            withTimeout(SEND_TIMEOUT_MS) {
                val results = getSender().sendDataToPebble(WATCHAPP_UUID, dict, targets)
                val success = results?.all { it.value == TransmissionResult.Success } ?: false
                if (success && results != null && results.isNotEmpty()) {
                    PebbleCommandService.lastConnectedWatch = results.keys.first()
                }
                
                if (success) {
                    Log.d("PebbleMessenger", "Send Success: [$tag]")
                } else {
                    Log.e("PebbleMessenger", "Send Failed: [$tag] Targets=$targets, Results=$results")
                }
                success
            }
        } catch (e: Exception) {
            Log.e("PebbleMessenger", "Send Error: [$tag] ${e.message}")
            cachedSender = null
            false
        }
    }

    override fun sendStatistics(stats: RunStatistics) {
        if (isMapTransferring) return
        val settings = this.settings
        ensureSessionInitialized(stats.status, settings)

        val dict = mapOf(
            KEY_CMD to PebbleDictionaryItem.Int32(1), 
            KEY_TIME to PebbleDictionaryItem.Text(stats.formattedTime),
            KEY_DISTANCE to PebbleDictionaryItem.Text(formatDistance(stats.totalDistanceMeters, settings.isMetric)),
            KEY_PACE to PebbleDictionaryItem.Text(formatPace(stats.totalDistanceMeters, stats.totalSeconds, settings.isMetric)),
            KEY_STATE to PebbleDictionaryItem.Int32(mapToPebbleState(stats.status)),
            KEY_HR to PebbleDictionaryItem.Text(stats.currentHeartRate?.toString() ?: "--"),
            KEY_STEPS to PebbleDictionaryItem.Int32(stats.steps),
            KEY_TYPE to PebbleDictionaryItem.Int32(stats.activityType.ordinal),
            KEY_MID_ID to PebbleDictionaryItem.Int32(currentMidDataId),
            KEY_LOWER_ID to PebbleDictionaryItem.Int32(currentLowerDataId)
        )
        nextStatsRequest = PebbleMessageRequest("STATS", dict)
        sendMidData(stats)
        sendLowerData(stats)
    }

    private fun ensureSessionInitialized(status: RunStatus, settings: hag1987haaa.pebble.iron.domain.settings.AppSettings) {
        val enabledMid = settings.enabledMidTypes
        if (enabledMid.isNotEmpty()) {
            if (sessionMidList.isEmpty() || sessionMidList.size != enabledMid.size || !sessionMidList.containsAll(enabledMid)) {
                sessionMidList = enabledMid
                currentMidDataId = if (settings.lastMidId in enabledMid) settings.lastMidId else enabledMid.first()
                Log.d("PebbleMessenger", "Session Mid initialized. List=$sessionMidList, currentId=$currentMidDataId")
            }
        }

        val enabledLower = settings.enabledLowerTypes
        if (enabledLower.isNotEmpty()) {
            if (sessionLowerList.isEmpty() || sessionLowerList.size != enabledLower.size || !sessionLowerList.containsAll(enabledLower)) {
                sessionLowerList = enabledLower
                currentLowerDataId = if (settings.lastLowerId in enabledLower) settings.lastLowerId else enabledLower.first()
                Log.d("PebbleMessenger", "Session Lower initialized. List=$sessionLowerList, currentId=$currentLowerDataId")
            }
        }

        // 計測中に一時停止から復帰した際などは、保存IDまたはリスト先頭に同期
        if (status == RunStatus.ACTIVE && currentMidDataId == 99) {
            currentMidDataId = if (settings.lastMidId in sessionMidList) settings.lastMidId else sessionMidList.firstOrNull() ?: 0
        }

        if (currentGraphTypeId == -1) {
            currentGraphTypeId = settings.lastGraphTypeId
            if (currentGraphTypeId !in settings.enabledGraphTypes) {
                currentGraphTypeId = settings.enabledGraphTypes.firstOrNull() ?: -1
            }
        }
    }

    private fun sendMidData(stats: RunStatistics) {
        if (isMapTransferring) return
        val settings = this.settings
        if (sessionMidList.isEmpty()) return
        
        // 重要：計測中は sessionMidList の順序を「絶対に」変えずに送り続ける。
        // リストの回転は行わず、Pebble 側のインデックスとの完全同期を優先する。
        val pages = sessionMidList.mapNotNull { typeId -> generateMidPageString(typeId, stats, settings) }
        if (pages.isEmpty()) return

        val finalPages = if (stats.status == RunStatus.PAUSED) {
            // 一時停止中はコックピット(99)を一時的に先頭に差し込む。
            // Pebble はこの時インデックスを 0 にリセットして表示するはず。
            val cockpitPage = generateMidPageString(99, stats, settings)
            if (cockpitPage != null) {
                listOf(cockpitPage) + pages.filter { !it.contains(",DETAIL,") }
            } else pages
        } else {
            // 計測中は「固定されたセッションリスト」をそのまま送る
            pages
        }

        val midDataString = finalPages.joinToString("|")
        Log.d("PebbleMessenger", "Sending Mid Data: currentID=$currentMidDataId, count=${finalPages.size}")
        nextMidDataRequest = PebbleMessageRequest("MID_DATA", mapOf(KEY_MID_DATA to PebbleDictionaryItem.Text(midDataString)))
    }

    private fun sendLowerData(stats: RunStatistics) {
        if (isMapTransferring) return
        val settings = this.settings
        if (sessionLowerList.isEmpty()) return
        
        val pages = sessionLowerList.mapNotNull { typeId -> generateLowerPageString(typeId, stats, settings) }
        if (pages.isEmpty()) return

        val lowerDataString = pages.joinToString("|")
        Log.d("PebbleMessenger", "Sending Lower Data: currentID=$currentLowerDataId, count=${pages.size}")
        nextLowerDataRequest = PebbleMessageRequest("LOWER_DATA", mapOf(KEY_LOWER_DATA to PebbleDictionaryItem.Text(lowerDataString)))
    }

    private fun generateLowerPageString(typeId: Int, stats: RunStatistics, settings: hag1987haaa.pebble.iron.domain.settings.AppSettings): String? {
        if (typeId >= 100) {
            val graphName = when (typeId) {
                100 -> "PACE"
                101 -> "DIST"
                102 -> "STEPS"
                103 -> "ALT"
                104 -> "HR"
                105 -> "CAL"
                else -> "GRAPH"
            }
            return "$typeId,GRAPH,$graphName,"
        }
        return generateMidPageString(typeId, stats, settings)
    }

    private fun generateMidPageString(typeId: Int, stats: RunStatistics, settings: hag1987haaa.pebble.iron.domain.settings.AppSettings): String? {
        val unitStr = if (settings.isMetric) "/km" else "/mi"
        val (name, value, unit) = when (typeId) {
            0 -> Triple("30s PACE", calculateWindowedPace(stats, 30, settings.isMetric), unitStr)
            1 -> Triple("DIST", formatDistance(stats.totalDistanceMeters, settings.isMetric), if (settings.isMetric) "km" else "mi")
            2 -> Triple("STEPS", stats.steps.toString(), "steps")
            3 -> {
                val alt = if (settings.isMetric) (stats.route.lastOrNull()?.altitude ?: 0.0).toInt() 
                          else ((stats.route.lastOrNull()?.altitude ?: 0.0) * 3.28084).toInt()
                Triple("ALT", alt.toString(), if (settings.isMetric) "m" else "ft")
            }
            4 -> {
                val hr = stats.currentHeartRate?.toString() ?: "--"
                val label = if (stats.hrSource == "BLE") "HR (Ext)" else "HR (Int)"
                Triple(label, hr, "bpm")
            }
            5 -> Triple("CAL", stats.calories.toInt().toString(), "kcal")
            7 -> Triple("AVG PACE", formatPace(stats.totalDistanceMeters, stats.totalSeconds, settings.isMetric), unitStr)
            8 -> Triple("SPEED", formatSpeed(stats.totalDistanceMeters, stats.totalSeconds, settings.isMetric), if (settings.isMetric) "km/h" else "mph")
            9 -> {
                val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                Triple("CLOCK", "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}", "")
            }
            10 -> {
                val gain = if (settings.isMetric) stats.totalElevationGain.toInt() else (stats.totalElevationGain * 3.28084).toInt()
                Triple("GAIN", gain.toString(), if (settings.isMetric) "m" else "ft")
            }
            11 -> Triple("CADENCE", calculateCurrentCadence(stats).toString(), "spm")
            12 -> Triple("BLE HR", stats.latestBleHeartRate?.toString() ?: "--", "bpm")
            13 -> Triple("WATCH HR", stats.latestPebbleHeartRate?.toString() ?: "--", "bpm")
            14 -> Triple("1m PACE", calculateWindowedPace(stats, 60, settings.isMetric), unitStr)
            15 -> Triple("2m PACE", calculateWindowedPace(stats, 120, settings.isMetric), unitStr)
            16 -> Triple("5m PACE", calculateWindowedPace(stats, 300, settings.isMetric), unitStr)
            17 -> Triple("10m PACE", calculateWindowedPace(stats, 600, settings.isMetric), unitStr)
            99 -> Triple("", "DETAIL", "")
            else -> return null
        }
        return "$typeId,$name,$value,$unit"
    }

    override fun rotateMidData(stats: RunStatistics) {
        if (sessionMidList.isEmpty()) return
        
        // Pebble で SELECT ボタンが押された：Android はリスト順序を変えず、
        // sessionMidList の中から「次の ID」を特定して currentMidDataId に記録するだけ。
        val currentIdx = sessionMidList.indexOf(currentMidDataId).coerceAtLeast(0)
        val nextIdx = (currentIdx + 1) % sessionMidList.size
        val nextId = sessionMidList[nextIdx]
        
        currentMidDataId = nextId
        
        sendMidData(stats)
    }

    override fun rotateGraphType(stats: RunStatistics) {
        val settings = this.settings
        val enabled = settings.enabledGraphTypes
        if (enabled.isEmpty()) return
        
        val currentIdx = enabled.indexOf(currentGraphTypeId).coerceAtLeast(0)
        val nextIdx = (currentIdx + 1) % enabled.size
        val nextId = enabled[nextIdx]
        
        currentGraphTypeId = nextId
        settings.lastGraphTypeId = nextId
        settings.save()
        
        sendGraphData(stats)
    }

    override fun sendGraphData(stats: RunStatistics) {
        scope.launch {
            val currentSettings = this@AndroidPebbleMessenger.settings
            val enabled = currentSettings.enabledGraphTypes
            if (enabled.isEmpty()) return@launch
            
            // 現在のIDが有効リストにない場合は、設定の最終保存値かリストの先頭を採用する
            val targetId = if (currentGraphTypeId in enabled) {
                currentGraphTypeId
            } else if (currentSettings.lastGraphTypeId in enabled) {
                currentGraphTypeId = currentSettings.lastGraphTypeId
                currentGraphTypeId
            } else {
                currentGraphTypeId = enabled[0]
                enabled[0]
            }
            
            val unifiedGraph = GraphDataGenerator.generateUnifiedGraph(stats, targetId, currentSettings)
            nextGraphRequest = PebbleMessageRequest("GRAPH", mapOf(KEY_GRAPH_DATA to PebbleDictionaryItem.Text(unifiedGraph)))
        }
    }

    private fun formatDistance(meters: Double, isMetric: Boolean): String {
        val value = if (isMetric) meters / 1000.0 else meters / 1609.344
        return if (value >= 100.0) {
            val integerPart = value.toInt()
            val fractionalPart = ((value - integerPart.toDouble()) * 10).toInt().coerceIn(0, 9)
            "$integerPart.$fractionalPart"
        } else {
            val integerPart = value.toInt()
            val fractionalPart = ((value - integerPart.toDouble()) * 100).toInt().coerceIn(0, 99)
            val ff = if (fractionalPart < 10) "0$fractionalPart" else fractionalPart.toString()
            "$integerPart.$ff"
        }
    }

    private fun formatPace(meters: Double, seconds: Long, isMetric: Boolean): String {
        if (meters <= 0 || seconds <= 0) return "--:--"
        val distance = if (isMetric) meters / 1000.0 else meters / 1609.344
        val paceSeconds = (seconds / distance).toInt()
        if (paceSeconds > 3600) return "60:00"
        val m = paceSeconds / 60
        val s = paceSeconds % 60
        return "$m:${s.toString().padStart(2, '0')}"
    }

    private fun formatSpeed(meters: Double, seconds: Long, isMetric: Boolean): String {
        if (seconds <= 0) return "0.0"
        val distance = if (isMetric) meters / 1000.0 else meters / 1609.344
        val speed = distance / (seconds / 3600.0)
        return ((speed * 10).toInt() / 10.0).toString()
    }

    private fun calculateCurrentCadence(stats: RunStatistics): Int {
        if (stats.route.size < 5) return 0
        val last = stats.route.last()
        val activityType = stats.activityType
        val maxWindowSec = when (activityType) {
            hag1987haaa.pebble.iron.domain.model.ActivityType.RUNNING -> 20
            hag1987haaa.pebble.iron.domain.model.ActivityType.WALKING,
            hag1987haaa.pebble.iron.domain.model.ActivityType.HIKING -> 30
            else -> 0
        }
        if (maxWindowSec == 0) return 0
        val firstTs = stats.route.first().timestamp.epochSeconds
        val elapsedFromStart = last.timestamp.epochSeconds - firstTs
        val currentWindowSec = elapsedFromStart.coerceIn(5, maxWindowSec.toLong()).toInt()
        var prevIndex = stats.route.size - 2
        val targetTs = last.timestamp.epochSeconds - currentWindowSec
        if (stats.route[prevIndex].timestamp.epochSeconds < (last.timestamp.epochSeconds - 30)) return 0
        while (prevIndex > 0 && stats.route[prevIndex].timestamp.epochSeconds > targetTs) prevIndex--
        val prev = stats.route[prevIndex]
        val stepDiff = (last.steps ?: 0) - (prev.steps ?: 0)
        val timeDiffSec = (last.timestamp.epochSeconds - prev.timestamp.epochSeconds).coerceAtLeast(1)
        if (timeDiffSec < 5 && elapsedFromStart < maxWindowSec) return 0
        return (stepDiff.toDouble() / timeDiffSec.toDouble() * 60.0).toInt().coerceIn(0, 250)
    }

    private fun calculateWindowedPace(stats: RunStatistics, windowSeconds: Long, isMetric: Boolean): String {
        if (stats.route.size < 2) return "--:--"
        val last = stats.route.last()
        val targetTime = last.timestamp.epochSeconds - windowSeconds
        var distSum = 0.0; var timeSum = 0L
        for (i in stats.route.size - 1 downTo 1) {
            val p1 = stats.route[i]; val p2 = stats.route[i-1]
            if (p2.timestamp.epochSeconds < targetTime) break
            distSum += hag1987haaa.pebble.iron.util.LocationUtils.calculateDistance(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            timeSum += (p1.timestamp.epochSeconds - p2.timestamp.epochSeconds)
        }
        if (distSum <= 1.0 || timeSum <= 0) return "--:--"
        return formatPace(distSum, timeSum, isMetric)
    }

    override fun sendState(status: RunStatus, stats: RunStatistics) {
        val settings = this.settings

        // 状態が変わる重要な瞬間（PREPARE / READY / ACTIVE / PAUSED等）なので、
        // 進行中の大容量マップ転送があれば即座にキャンセルしてステータス通知を最優先化
        if (mapSendJob?.isActive == true) {
            Log.d("PebbleMessenger", "sendState: Cancelling in-flight map transmission in favor of state transition ($status)")
            mapSendJob?.cancel()
            isTransmittingChunks = false
            isMapTransferring = false
        }
        
        // 状態が変わる重要な瞬間なので、送信待ちの古い統計やグラフデータを全て破棄する
        // これにより、ウォッチ側での「二転三転（情報の逆転）」を物理的に防ぐ
        nextStatsRequest = null
        nextMidDataRequest = null
        nextGraphRequest = null
        
        // 状態が変わった（計測開始・準備・再開）際は、現在の設定に基づいてセッションリストをリセット
        if (status == RunStatus.ACTIVE || status == RunStatus.READY || status == RunStatus.PREPARING) {
            sessionMidList = emptyList() // ensureSessionInitialized を強制的に走らせる
            ensureSessionInitialized(status, settings)
        }
        
        val dict = mutableMapOf<UInt, PebbleDictionaryItem>(
            KEY_CMD to PebbleDictionaryItem.Int32(1), 
            KEY_STATE to PebbleDictionaryItem.Int32(mapToPebbleState(status))
        )
        
        if (sessionMidList.isNotEmpty()) {
            val pages = sessionMidList.mapNotNull { typeId -> generateMidPageString(typeId, stats, settings) }
            if (pages.isNotEmpty()) {
                val finalPages = if (status == RunStatus.PAUSED) {
                    val cockpitPage = generateMidPageString(99, stats, settings)
                    if (cockpitPage != null) listOf(cockpitPage) + pages.filter { !it.contains(",DETAIL,") } else pages
                } else {
                    pages
                }
                dict[KEY_MID_DATA] = PebbleDictionaryItem.Text(finalPages.joinToString("|"))
            }
        }
        
        commandQueue.trySend(PebbleMessageRequest("STATE_CHANGE_WITH_DATA", dict, retryCount = 5))
    }

    override fun sendFullSync(stats: RunStatistics) {
        val settings = this.settings
        ensureSessionInitialized(stats.status, settings)
        val dict = mapOf(
            KEY_CMD to PebbleDictionaryItem.Int32(5), 
            KEY_TIME to PebbleDictionaryItem.Text(stats.formattedTime),
            KEY_DISTANCE to PebbleDictionaryItem.Text(formatDistance(stats.totalDistanceMeters, settings.isMetric)),
            KEY_PACE to PebbleDictionaryItem.Text(formatPace(stats.totalDistanceMeters, stats.totalSeconds, settings.isMetric)),
            KEY_STATE to PebbleDictionaryItem.Int32(mapToPebbleState(stats.status)),
            KEY_HR to PebbleDictionaryItem.Text(stats.currentHeartRate?.toString() ?: "--"),
            KEY_STEPS to PebbleDictionaryItem.Int32(stats.steps),
            KEY_TYPE to PebbleDictionaryItem.Int32(stats.activityType.ordinal),
            KEY_HR_INTERVAL to PebbleDictionaryItem.UInt32(settings.hrSamplingInterval.toUInt())
        )
        commandQueue.trySend(PebbleMessageRequest("SYNC", dict, retryCount = 3))
        sendGraphData(stats)

        // Pebble起動時・同期時に最新の保存済みコース一覧を自動プッシュ
        val courses = settings.savedGpxCourses
        if (courses.isNotEmpty()) {
            val coursesDataStr = courses.joinToString("|") { "${if (it.isEnabled) 1 else 0},${it.name}" }
            sendCoursesData(coursesDataStr)
        }
    }

    override fun sendTouchConfig(enabled: Boolean) {
        commandQueue.trySend(PebbleMessageRequest("TOUCH_CONFIG", mapOf(KEY_TOUCH_ENABLE to PebbleDictionaryItem.Int32(if (enabled) 1 else 0)), retryCount = 5))
    }

    override fun sendNotification(type: Int) {
        val cmdId = if (type == 0) 10 else 11
        commandQueue.trySend(PebbleMessageRequest("NOTIFICATION", mapOf(KEY_CMD to PebbleDictionaryItem.Int32(cmdId)), retryCount = 3))
    }

    override fun sendLowerData(lowerDataString: String) {
        commandQueue.trySend(PebbleMessageRequest("LOWER_DATA", mapOf(KEY_LOWER_DATA to PebbleDictionaryItem.Text(lowerDataString))))
    }

    override fun sendMidId(id: Int) {
        commandQueue.trySend(PebbleMessageRequest("MID_ID", mapOf(KEY_MID_ID to PebbleDictionaryItem.Int32(id))))
    }

    override fun sendLowerId(id: Int) {
        commandQueue.trySend(PebbleMessageRequest("LOWER_ID", mapOf(KEY_LOWER_ID to PebbleDictionaryItem.Int32(id))))
    }

    override fun sendMapState(isActive: Boolean) {
        isMapActive = isActive
        if (!isActive) {
            mapAutoCloseJob?.cancel()
            mapAutoCloseJob = null
            mapDebounceJob?.cancel()
            mapDebounceJob = null
            mapSendJob?.cancel()
            mapSendJob = null
            isTransmittingChunks = false
            hasPendingMapRefresh = false
            panOffsetPixelsX = 0.0
            panOffsetPixelsY = 0.0
        } else {
            resetAutoCloseTimer()
        }
        commandQueue.trySend(PebbleMessageRequest("MAP_STATE", mapOf(KEY_MAP_STATE to PebbleDictionaryItem.Int32(if (isActive) 1 else 0))))
    }

    override fun sendMapChunk(data: ByteArray, chunkIdx: Int, totalChunks: Int) {
        val dict = mapOf(
            KEY_MAP_DATA to PebbleDictionaryItem.Bytes(data),
            KEY_MAP_CHUNK_IDX to PebbleDictionaryItem.Int32(chunkIdx),
            KEY_MAP_TOTAL_CHUNKS to PebbleDictionaryItem.Int32(totalChunks)
        )
        Log.d("PebbleMessenger", "Queueing MAP_CHUNK $chunkIdx/$totalChunks (${data.size} bytes)")
        commandQueue.trySend(PebbleMessageRequest("MAP_CHUNK", dict))
    }

    @Volatile
    private var plannedCoursePoints: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>? = null

    override fun sendCoursesData(coursesDataString: String) {
        commandQueue.trySend(PebbleMessageRequest("COURSES_DATA", mapOf(KEY_COURSES_DATA to PebbleDictionaryItem.Text(coursesDataString))))
    }

    override fun setPlannedCourse(points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>?) {
        plannedCoursePoints = points
        Log.d("PebbleMessenger", "setPlannedCourse updated: ${points?.size ?: 0} points")
    }

    override suspend fun getMapPreviewRgba(
        points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>, 
        width: Int, 
        height: Int, 
        isMonochrome: Boolean, 
        zoom: Int?
    ): IntArray? = withContext(Dispatchers.IO) {
        val result = getMapPreviewInfo(points, width, height, isMonochrome, zoom)
        result?.rgba
    }

    override suspend fun getMapPreviewInfo(
        points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>, 
        width: Int, 
        height: Int, 
        isMonochrome: Boolean, 
        zoom: Int?
    ): hag1987haaa.pebble.iron.domain.tracker.MapPreviewResult? = withContext(Dispatchers.IO) {
        if (points.isEmpty() && plannedCoursePoints.isNullOrEmpty()) return@withContext null
        return@withContext try {
            val targetZoom = (zoom ?: currentMapZoom).coerceIn(11, 18)
            val rawBitmap = renderMapBitmapWithTiles(points, width, height, targetZoom)
            val pebblePixels = convertToPebblePixels(rawBitmap, isMonochrome, targetZoom)
            rawBitmap.recycle()
            
            val rleData = encodeRLE(pebblePixels)
            var roadPixelCount = 0
            val outPixels = IntArray(width * height)
            for (i in pebblePixels.indices) {
                val b = pebblePixels[i].toInt() and 0xFF
                if (isMonochrome) {
                    if (b != 0xFF) roadPixelCount++
                    outPixels[i] = if (b == 0xFF) -1 else -16777216
                } else {
                    if (b == 0b11000000.toByte().toInt() and 0xFF || b == 0b11010101.toByte().toInt() and 0xFF) {
                        roadPixelCount++
                    }
                    val a = 0xFF
                    val r = ((b shr 4) and 0x03) * 85
                    val g = ((b shr 2) and 0x03) * 85
                    val blue = (b and 0x03) * 85
                    outPixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or blue
                }
            }
            val roadPct = (roadPixelCount.toFloat() / (width * height).toFloat()) * 100f
            hag1987haaa.pebble.iron.domain.tracker.MapPreviewResult(
                rgba = outPixels,
                rleBytesCount = rleData.size,
                roadPixelPercent = roadPct
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PebbleMessenger", "getMapPreviewInfo failed", e)
            null
        }
    }

    private fun getNativeMapDimensions(): Pair<Int, Int> {
        val platform = settings.pebblePlatform
        return when {
            platform?.contains("Classic") == true || (platform?.contains("Time") == true && !platform.contains("Round") && !platform.contains("2")) || platform?.contains("Pebble 2") == true -> Pair(144, 128)
            platform?.contains("Round 2") == true -> Pair(260, 198)
            platform?.contains("Round") == true -> Pair(180, 136)
            platform?.contains("Time 2") == true -> Pair(200, 176)
            else -> Pair(144, 128)
        }
    }

    override fun sendMap(points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>, width: Int, height: Int, zoom: Int?) {
        if (zoom != null) {
            currentMapZoom = zoom.coerceIn(11, 18)
            Log.i("PebbleMessenger", "sendMap: Requested zoom override -> $currentMapZoom")
        }
        val (nativeW, nativeH) = getNativeMapDimensions()
        val targetW = if (width in 100..300) width else nativeW
        val targetH = if (height in 100..250 && height != 168) height else nativeH
        lastMapPoints = points
        lastMapWidth = targetW
        lastMapHeight = targetH

        // Bluetoothパケット送信フェーズ中の場合は、ウォッチ側の画面破損を防ぐため現在の送信完了を待ってから即座に再送
        if (isTransmittingChunks) {
            hasPendingMapRefresh = true
            Log.d("PebbleMessenger", "sendMap: Bluetooth chunks transmitting. Next request marked as pending.")
            return
        }

        // まだウォッチへのパケット送信が始まっていない（タイル取得・生成中）場合は、直前の処理を即キャンセルして最新操作で一発再実行！
        mapSendJob?.cancel()

        mapSendJob = scope.launch {
            isMapTransferring = true
            try {
                Log.i("PebbleMessenger", "sendMap: Instantly starting map preparation (w=$targetW, h=$targetH, zoom=$currentMapZoom)...")
                
                val activePoints = if (points.isEmpty()) {
                    val planned = plannedCoursePoints
                    if (!planned.isNullOrEmpty()) {
                        planned
                    } else {
                        val currentLoc = hag1987haaa.pebble.iron.KmpDependencies.trackerEngine.statistics.value.currentLocation
                        if (currentLoc != null) {
                            listOf(currentLoc)
                        } else {
                            val now = kotlinx.datetime.Clock.System.now()
                            listOf(
                                hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6812, 139.7671, timestamp = now), 
                                hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6812, 139.7701, timestamp = now),
                                hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6782, 139.7701, timestamp = now),
                                hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6782, 139.7671, timestamp = now),
                                hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6812, 139.7671, timestamp = now)
                            )
                        }
                    }
                } else {
                    points
                }

                // 1. タイル取得とビットマップ・RLE生成
                val bitmap = renderMapBitmapWithTiles(activePoints, targetW, targetH)
                
                val isMonochrome = settings.pebblePlatform?.let { 
                    it.contains("Classic") || it.contains("Pebble 2") 
                } ?: false

                val pebblePixels = convertToPebblePixels(bitmap, isMonochrome, currentMapZoom)
                bitmap.recycle()
                val rleData = encodeRLE(pebblePixels)
                val totalSize = rleData.size

                if (mapSendJob?.isActive != true) return@launch

                // 2. 他の重要通信（READY通知やSTATS更新）が完全に送信完了するのを待つ
                var waitCount = 0
                while ((!commandQueue.isEmpty || nextStatsRequest != null || nextMidDataRequest != null) && waitCount < 20) {
                    delay(100)
                    waitCount++
                }

                if (mapSendJob?.isActive != true) return@launch

                // 3. チャンク送信フェーズ（ウォッチがコックピット表示のままバックグラウンドで全チャンクを先行受信）
                isTransmittingChunks = true
                val chunkSize = 500 
                val totalChunks = (totalSize + chunkSize - 1) / chunkSize
                val fixedDelayMs = 130L 

                for (i in 0 until totalChunks) {
                    if (!commandQueue.isEmpty) {
                        delay(120)
                    }
                    val start = i * chunkSize
                    val end = minOf(start + chunkSize, totalSize)
                    val chunk = rleData.sliceArray(start until end)
                    
                    Log.d("PebbleMessenger", "sendMap: Sending chunk ${i + 1}/$totalChunks (${chunk.size} bytes)")
                    sendMapChunk(chunk, i, totalChunks)
                    
                    delay(fixedDelayMs) 
                }
                Log.i("PebbleMessenger", "sendMap: Fully transmitted $totalSize bytes in $totalChunks chunks.")

                // 4. 全チャンクが揃った時点でマップ表示コマンドを送信（待機時間ゼロで即座に完成マップを表示）
                sendMapState(true)
                delay(100)
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.d("PebbleMessenger", "sendMap: Pre-transmission generation cancelled in favor of newer request")
                throw e
            } catch (e: Exception) {
                Log.e("PebbleMessenger", "sendMap: Error during transmission: ${e.message}")
            } finally {
                isTransmittingChunks = false
                isMapTransferring = false
                Log.d("PebbleMessenger", "sendMap: Transmission lock released. Syncing latest stats.")
                
                nextStatsRequest?.let { commandQueue.trySend(it) }
                nextMidDataRequest?.let { commandQueue.trySend(it) }
                nextLowerDataRequest?.let { commandQueue.trySend(it) }

                // 転送中に溜まった操作（ダブルクリック、連続ズーム、スワイプ等）があれば最新状態で即座に一括再実行！
                if (hasPendingMapRefresh && isMapActive) {
                    hasPendingMapRefresh = false
                    Log.i("PebbleMessenger", "sendMap: Executing pending map refresh with coalesced latest state (zoom=$currentMapZoom)...")
                    executeMapRefresh()
                } else if (isMapActive) {
                    resetAutoCloseTimer()
                }
            }
        }
    }

    suspend fun renderMapBitmapWithTiles(points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>, width: Int, height: Int, zoomOverride: Int? = null): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.LTGRAY)

        val plannedPoints = plannedCoursePoints
        // 中心座標の決定: 
        // 1. 走行実績が2点以上ある場合は最新の走者位置
        // 2. 計画ルート（GPX）がある場合は計画ルートの始点
        // 3. それ以外は現在地またはフォールバック
        val currentLoc = hag1987haaa.pebble.iron.KmpDependencies.trackerEngine.statistics.value.currentLocation
        val currentPoint = currentLoc
            ?: (if (points.isNotEmpty() && points !== plannedPoints) points.last() else null)
            ?: plannedPoints?.firstOrNull()
            ?: points.lastOrNull()
            ?: return@withContext bitmap

        // 1. 中心の決定 (最新の地点を画像の中央にする)
        val centerLat = currentPoint.latitude
        val centerLon = currentPoint.longitude
        
        // 2. ズームレベルの設定 (デフォルト 16: 半径約200m。UP/DOWNで動的変更可能)
        val zoom = (zoomOverride ?: currentMapZoom).coerceIn(11, 18)
        val n = Math.pow(2.0, zoom.toDouble())

        // メルカトル投影での世界座標ピクセル (256pxタイル基準)
        val currentPointWorldX = (centerLon + 180.0) / 360.0 * n * 256.0
        val currentPointWorldY = (1.0 - Math.log(Math.tan(Math.toRadians(centerLat)) + (1.0 / Math.cos(Math.toRadians(centerLat)))) / Math.PI) / 2.0 * n * 256.0
        val xCenterWorld = currentPointWorldX + panOffsetPixelsX
        val yCenterWorld = currentPointWorldY + panOffsetPixelsY

        // 停止判定および進行方位角 (bearing) の計算
        val STOPPED_SPEED_THRESHOLD_MPS = 0.5 // 0.5 m/s (時速1.8km) 未満を停止と判定
        val latestActivePoint = if (points.isNotEmpty() && points !== plannedPoints) points.last() else currentPoint
        val isStopped = when {
            points.size >= 2 && points !== plannedPoints -> {
                val speed = latestActivePoint.speed
                if (speed != null && speed.isFinite()) {
                    speed < STOPPED_SPEED_THRESHOLD_MPS
                } else {
                    val prev = points[points.size - 2]
                    val timeDiffSec = (latestActivePoint.timestamp - prev.timestamp).inWholeMilliseconds / 1000.0
                    if (timeDiffSec in 0.2..10.0) {
                        val lat1 = Math.toRadians(prev.latitude)
                        val lat2 = Math.toRadians(latestActivePoint.latitude)
                        val dLat = Math.toRadians(latestActivePoint.latitude - prev.latitude)
                        val dLon = Math.toRadians(latestActivePoint.longitude - prev.longitude)
                        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
                        val dist = 6371000.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                        (dist / timeDiffSec) < STOPPED_SPEED_THRESHOLD_MPS
                    } else {
                        true
                    }
                }
            }
            points.size == 1 && points !== plannedPoints -> {
                val speed = latestActivePoint.speed
                if (speed != null && speed.isFinite()) speed < STOPPED_SPEED_THRESHOLD_MPS else true
            }
            else -> true
        }

        val bearing = if (points.isNotEmpty() && points !== plannedPoints) {
            if (points.size >= 2) {
                val last = points.last()
                val prev = points[points.size - 2]
                last.bearing?.toFloat() ?: run {
                    val lat1 = Math.toRadians(prev.latitude)
                    val lat2 = Math.toRadians(last.latitude)
                    val dLon = Math.toRadians(last.longitude - prev.longitude)
                    val y = Math.sin(dLon) * Math.cos(lat2)
                    val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
                    ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
                }
            } else {
                points.firstOrNull()?.bearing?.toFloat() ?: 0f
            }
        } else if (!plannedPoints.isNullOrEmpty() && plannedPoints.size >= 2) {
            val p0 = plannedPoints[0]
            val p1 = plannedPoints[1]
            p0.bearing?.toFloat() ?: run {
                val lat1 = Math.toRadians(p0.latitude)
                val lat2 = Math.toRadians(p1.latitude)
                val dLon = Math.toRadians(p1.longitude - p0.longitude)
                val y = Math.sin(dLon) * Math.cos(lat2)
                val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
                ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
            }
        } else {
            0f
        }

        val rotateHeadingUp = isHeadingUp
        if (rotateHeadingUp) {
            canvas.save()
            canvas.rotate(-bearing, (width / 2.0).toFloat(), (height / 2.0).toFloat())
        }

        val xtileCenter = Math.floor(xCenterWorld / 256.0).toInt()
        val ytileCenter = Math.floor(yCenterWorld / 256.0).toInt()

        // 3. 周辺4タイル (2x2) を並列取得して描画
        val xStartTile = if (xCenterWorld % 256.0 < 128.0) xtileCenter - 1 else xtileCenter
        val yStartTile = if (yCenterWorld % 256.0 < 128.0) ytileCenter - 1 else ytileCenter

        val tileJobs = (0..1).flatMap { ty ->
            (0..1).map { tx ->
                val curX = xStartTile + tx
                val curY = yStartTile + ty
                val tileUrl = "https://tile.openstreetmap.org/$zoom/$curX/$curY.png"
                async(Dispatchers.IO) {
                    try {
                        val cached = synchronized(tileCache) { tileCache.get(tileUrl) }
                        val tileBitmap = if (cached != null && !cached.isRecycled) {
                            cached
                        } else {
                            val connection = java.net.URL(tileUrl).openConnection() as java.net.HttpURLConnection
                            connection.setRequestProperty("User-Agent", "IronPebbleTracker/1.0 (Android; hag1987haaa.pebble.iron)")
                            connection.connectTimeout = 3000
                            connection.readTimeout = 3000
                            val loaded = android.graphics.BitmapFactory.decodeStream(connection.inputStream)
                            if (loaded != null) {
                                synchronized(tileCache) { tileCache.put(tileUrl, loaded) }
                            }
                            loaded
                        }

                        if (tileBitmap != null) {
                            val tileLeftWorld = curX * 256.0
                            val tileTopWorld = curY * 256.0
                            val dx = (tileLeftWorld - xCenterWorld + (width / 2.0)).toFloat()
                            val dy = (tileTopWorld - yCenterWorld + (height / 2.0)).toFloat()
                            Triple(tileBitmap, dx, dy)
                        } else null
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("PebbleMessenger", "Tile fetch failed: $tileUrl, error: ${e.message}")
                        null
                    }
                }
            }
        }

        val downloadedTiles = tileJobs.awaitAll().filterNotNull()
        for ((tileBitmap, dx, dy) in downloadedTiles) {
            canvas.drawBitmap(tileBitmap, dx, dy, null)
        }

        val PAUSE_GAP_MS = 10_000L

        // 4-A. 計画ルート (Planned Course) の描画 (マゼンタ: Garmin標準色・道路幅より少し太め・角丸)
        if (!plannedPoints.isNullOrEmpty()) {
            val routeStrokeWidth = if (width >= 200) 6f else 5f
            val plannedPaint = Paint().apply {
                color = Color.MAGENTA
                strokeWidth = routeStrokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = false
            }
            val plannedPath = android.graphics.Path()
            plannedPoints.forEachIndexed { index, point ->
                val px = (point.longitude + 180.0) / 360.0 * n * 256.0
                val py = (1.0 - Math.log(Math.tan(Math.toRadians(point.latitude)) + (1.0 / Math.cos(Math.toRadians(point.latitude)))) / Math.PI) / 2.0 * n * 256.0
                val dx = (px - xCenterWorld + (width / 2.0)).toFloat()
                val dy = (py - yCenterWorld + (height / 2.0)).toFloat()
                if (index == 0 || point.isSegmentStart) {
                    plannedPath.moveTo(dx, dy)
                } else {
                    plannedPath.lineTo(dx, dy)
                }
            }
            canvas.drawPath(plannedPath, plannedPaint)
        }

        // 4-B. 走行実績ルート (Tracked Route) の描画 (赤色: 道路幅より少し太め・角丸・ポーズ区間分離)
        if (points.isNotEmpty() && points !== plannedPoints) {
            val routeStrokeWidth = if (width >= 200) 6f else 5f
            val routePaint = Paint().apply {
                color = Color.RED
                strokeWidth = routeStrokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                isAntiAlias = false
            }

            var currentPath = android.graphics.Path()
            var isPathEmpty = true

            for (i in points.indices) {
                val point = points[i]
                val px = (point.longitude + 180.0) / 360.0 * n * 256.0
                val py = (1.0 - Math.log(Math.tan(Math.toRadians(point.latitude)) + (1.0 / Math.cos(Math.toRadians(point.latitude)))) / Math.PI) / 2.0 * n * 256.0
                val dx = (px - xCenterWorld + (width / 2.0)).toFloat()
                val dy = (py - yCenterWorld + (height / 2.0)).toFloat()

                val isNewSegment = if (i == 0) {
                    true
                } else {
                    val prev = points[i - 1]
                    val timeDiff = (point.timestamp - prev.timestamp).inWholeMilliseconds
                    point.isSegmentStart || timeDiff > PAUSE_GAP_MS
                }

                if (isNewSegment) {
                    if (!isPathEmpty) {
                        canvas.drawPath(currentPath, routePaint)
                    }
                    currentPath = android.graphics.Path()
                    currentPath.moveTo(dx, dy)
                    isPathEmpty = false
                } else {
                    currentPath.lineTo(dx, dy)
                }
            }
            if (!isPathEmpty) {
                canvas.drawPath(currentPath, routePaint)
            }
        }

        // 6. 現在地インジケータの描画 (停止時: 単色グリーンドット / 移動時: 単色グリーン二等辺三角形)
        val cx = (currentPointWorldX - xCenterWorld + (width / 2.0)).toFloat()
        val cy = (currentPointWorldY - yCenterWorld + (height / 2.0)).toFloat()

        val locationPaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.GREEN // Vivid Green
            isAntiAlias = false
        }

        if (isStopped) {
            // 停止中: 半径8px (直径16px) のグリーン単色ドット
            canvas.drawCircle(cx, cy, 8f, locationPaint)
        } else {
            // 移動中: グリーン単色・二等辺三角形 (底辺幅16px, 全長22px)
            // ノーズアップ時はキャンバスが -bearing 回転しているため、+bearing 回転で相殺して画面真上(12時方向)を向く
            // ノースアップ時はキャンバスが北上なので、+bearing 回転で進行方向を向く
            canvas.save()
            canvas.rotate(bearing, cx, cy)
            val trianglePath = android.graphics.Path().apply {
                moveTo(cx, cy - 13f)       // 先端
                lineTo(cx + 8f, cy + 9f)   // 右下
                lineTo(cx - 8f, cy + 9f)   // 左下
                close()
            }
            canvas.drawPath(trianglePath, locationPaint)
            canvas.restore()
        }
        if (rotateHeadingUp) {
            canvas.restore()
        }
        bitmap
    }

    fun createPebblePreviewBitmap(sourceBitmap: Bitmap, isMonochrome: Boolean): Bitmap {
        val width = sourceBitmap.width
        val height = sourceBitmap.height
        val pebblePixels = convertToPebblePixels(sourceBitmap, isMonochrome, currentMapZoom)
        val previewBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(width * height)
        for (i in pebblePixels.indices) {
            val b = pebblePixels[i].toInt() and 0xFF
            if (isMonochrome) {
                outPixels[i] = if (b == 0xFF) Color.WHITE else Color.BLACK
            } else {
                val a = 0xFF
                val r = ((b shr 4) and 0x03) * 85
                val g = ((b shr 2) and 0x03) * 85
                val blue = (b and 0x03) * 85
                outPixels[i] = Color.argb(a, r, g, blue)
            }
        }
        previewBitmap.setPixels(outPixels, 0, width, 0, 0, width, height)
        return previewBitmap
    }

    fun convertToPebblePixels(bitmap: Bitmap, isMonochrome: Boolean, zoom: Int = currentMapZoom): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val TYPE_BG = 0
        val TYPE_WATER = 1
        val TYPE_LOCAL_ROAD = 2
        val TYPE_HIGHWAY = 3
        val TYPE_ROUTE = 4
        val TYPE_ARROW = 5
        val TYPE_PLANNED = 6

        val types = ByteArray(totalPixels)

        // 第1パス: 各ピクセルの色分類
        for (i in 0 until totalPixels) {
            val color = pixels[i]
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            // 1. ルート線（赤系統: 走行実績）を最優先で強調
            if (r > 170 && g < 90 && b < 90) {
                types[i] = TYPE_ROUTE.toByte()
                continue
            }
            // 2. 現在地インジケータ（Vivid Green系統: 停止時ドット / 移動時二等辺三角形）
            if (g > 180 && r < 100 && b < 100) {
                types[i] = TYPE_ARROW.toByte()
                continue
            }
            // 3. 計画ルート（マゼンタ系統: Garmin標準の計画コース色）
            if (r > 150 && g < 80 && b > 150 && Math.abs(r - b) < 60) {
                types[i] = TYPE_PLANNED.toByte()
                continue
            }
            // 3. 幹線道路・高速（Voyager: 黄色・オレンジ系）-> 太い黒線
            val isHighway = (r > 230 && g in 120..210 && b < 150) ||
                            (r > 240 && g in 190..230 && b < 170 && (r - b) > 60)
            if (isHighway) {
                types[i] = TYPE_HIGHWAY.toByte()
                continue
            }
            // 4. 水域 (OSM: #aad3df または青系水域)
            val isWater = (r in 150..190 && g in 195..225 && b in 215..240) ||
                          (b > 210 && g > 180 && r < 160 && b > r + 50)
            if (isWater) {
                types[i] = TYPE_WATER.toByte()
                continue
            }
            // 5. 一般道路・生活道路・路地・歩道・トレイル・特殊道路 (OSM)
            val isTertiary = (r > 245 && g > 235 && b in 140..195)
            val isWhiteRoad = (r >= 248 && g >= 248 && b >= 245)
            val isOffWhite = (zoom >= 16 && r >= 240 && g >= 240 && b >= 236)
            // 狭い路地（路面白がなくケーシング単独線で描かれた小道・生活道路）
            val isNarrowAlley = (zoom >= 16 && Math.abs(r - g) <= 3 && Math.abs(g - b) <= 3 && r in 205..234)
            // 公園・緑道トレイル・遊歩道・階段（OSMではサーモンピンク/赤茶色系の破線や横縞）
            val isTrailOrFootway = (zoom >= 15 && r in 180..252 && g in 70..185 && b in 70..175 && (r - g) >= 25 && Math.abs(g - b) <= 25)
            // 農道・未舗装路・林道・土の道（OSMでは茶色/黄土色: #996600, #b37700）
            val isTrack = (zoom >= 15 && r in 120..195 && g in 70..150 && b in 0..100 && (r - g) >= 15 && (g - b) >= 15)
            // 工事中の道路（黄色/オレンジと茶色/グレーの破線・縞模様）
            val isConstruction = (zoom >= 15 && r >= 200 && g in 140..220 && b <= 120 && (r - b) >= 70)
            // 自転車専用道・CR（青/水色の破線: 多摩川・荒川等のサイクリングロード）
            val isCycleway = (zoom >= 15 && b >= 160 && r <= 140 && (b - r) >= 40)
            // トンネル内の道路（半透明薄グレー路面）
            val isTunnel = (zoom >= 16 && r in 210..235 && g in 210..235 && b in 210..235 && Math.abs(r - g) <= 3 && Math.abs(g - b) <= 3)

            val isSpecialWay = isTrailOrFootway || isTrack || isConstruction || isCycleway || isTunnel

            if (zoom <= 14) {
                // Zoom 13-14 (引き・広域): 幹線・主要道のみ
                if (isTertiary) {
                    types[i] = TYPE_LOCAL_ROAD.toByte()
                    continue
                }
            } else if (zoom == 15) {
                // Zoom 15 (中域・広い視野): 主要一般道＋幅広路面のみ（細線クラッターによる団子化を防止）
                if (isTertiary || isWhiteRoad) {
                    types[i] = TYPE_LOCAL_ROAD.toByte()
                    continue
                }
            } else {
                // Zoom 16, 17, 18: 生活道路・狭い路地・歩道・トレイル・特殊道まで完全抽出
                if (isTertiary || isWhiteRoad || isOffWhite || isNarrowAlley || isSpecialWay) {
                    types[i] = TYPE_LOCAL_ROAD.toByte()
                    continue
                }
            }

            types[i] = TYPE_BG.toByte()
        }

        // 道路ケーシング（細道の線状接続）補完パス: 純白路面に隣接するケーシングを拾って細線の途切れを防止
        val casingTypes = types.clone()
        if (zoom >= 16) {
            for (y in 1 until height - 1) {
                val yOffset = y * width
                for (x in 1 until width - 1) {
                    val idx = yOffset + x
                    if (types[idx].toInt() == TYPE_BG) {
                        val color = pixels[idx]
                        val r = Color.red(color)
                        val g = Color.green(color)
                        val b = Color.blue(color)
                        if (Math.abs(r - g) <= 3 && Math.abs(g - b) <= 3 && r in 200..238) {
                            var hasRoadNeighbor = false
                            for (dy in -1..1) {
                                for (dx in -1..1) {
                                    if (types[(y + dy) * width + (x + dx)].toInt() == TYPE_LOCAL_ROAD) {
                                        hasRoadNeighbor = true
                                        break
                                    }
                                }
                                if (hasRoadNeighbor) break
                            }
                            if (hasRoadNeighbor) {
                                casingTypes[idx] = TYPE_LOCAL_ROAD.toByte()
                            }
                        }
                    }
                }
            }
        }

        // 道路の実線化パス（破線＆道路名ラベルによる1〜3pxギャップ接続）:
        // 道路と道路の間に挟まれた隙間（道路名テキストや破線による分断）を直線方向に繋ぎ、綺麗な実線に補間
        val solidTypes = casingTypes.clone()

        fun canFillGap(idx: Int): Boolean {
            if (casingTypes[idx].toInt() != TYPE_BG) return false
            val c = pixels[idx]
            val cr = Color.red(c)
            val cg = Color.green(c)
            val cb = Color.blue(c)
            val isGreen = (cg > cr + 15 && cg > cb + 15 && cg > 150)
            return !isGreen
        }

        fun isRoadType(t: Byte): Boolean {
            val v = t.toInt()
            return v == TYPE_LOCAL_ROAD || v == TYPE_HIGHWAY
        }

        // 1. 1px ギャップ接続（水平・垂直・斜め）
        for (y in 1 until height - 1) {
            val yOffset = y * width
            for (x in 1 until width - 1) {
                val idx = yOffset + x
                if (canFillGap(idx)) {
                    val left = casingTypes[idx - 1]
                    val right = casingTypes[idx + 1]
                    val up = casingTypes[idx - width]
                    val down = casingTypes[idx + width]

                    val isHorizGap = isRoadType(left) && isRoadType(right)
                    val isVertGap = isRoadType(up) && isRoadType(down)

                    val ul = casingTypes[idx - width - 1]
                    val br = casingTypes[idx + width + 1]
                    val ur = casingTypes[idx - width + 1]
                    val bl = casingTypes[idx + width - 1]
                    val isDiagGap = (isRoadType(ul) && isRoadType(br)) ||
                                    (isRoadType(ur) && isRoadType(bl))

                    if (isHorizGap || isVertGap || isDiagGap) {
                        solidTypes[idx] = TYPE_LOCAL_ROAD.toByte()
                    }
                }
            }
        }

        // 2. 2px & 3px ギャップ接続（拡大ズーム Zoom 16以上限定: 広い視野で平行道路が癒着して団子化するのを完全に防止）
        if (zoom >= 16) {
            for (y in 0 until height) {
            val yOffset = y * width
            for (x in 0 until width - 3) {
                val idx0 = yOffset + x
                val idx3 = yOffset + x + 3
                if (isRoadType(solidTypes[idx0]) && isRoadType(solidTypes[idx3])) {
                    val idx1 = idx0 + 1
                    val idx2 = idx0 + 2
                    if (canFillGap(idx1) && canFillGap(idx2)) {
                        solidTypes[idx1] = TYPE_LOCAL_ROAD.toByte()
                        solidTypes[idx2] = TYPE_LOCAL_ROAD.toByte()
                    }
                }
            }
            for (x in 0 until width - 4) {
                val idx0 = yOffset + x
                val idx4 = yOffset + x + 4
                if (isRoadType(solidTypes[idx0]) && isRoadType(solidTypes[idx4])) {
                    val idx1 = idx0 + 1
                    val idx2 = idx0 + 2
                    val idx3 = idx0 + 3
                    if (canFillGap(idx1) && canFillGap(idx2) && canFillGap(idx3)) {
                        solidTypes[idx1] = TYPE_LOCAL_ROAD.toByte()
                        solidTypes[idx2] = TYPE_LOCAL_ROAD.toByte()
                        solidTypes[idx3] = TYPE_LOCAL_ROAD.toByte()
                    }
                }
            }
        }

        for (x in 0 until width) {
            for (y in 0 until height - 3) {
                val idx0 = y * width + x
                val idx3 = (y + 3) * width + x
                if (isRoadType(solidTypes[idx0]) && isRoadType(solidTypes[idx3])) {
                    val idx1 = (y + 1) * width + x
                    val idx2 = (y + 2) * width + x
                    if (canFillGap(idx1) && canFillGap(idx2)) {
                        solidTypes[idx1] = TYPE_LOCAL_ROAD.toByte()
                        solidTypes[idx2] = TYPE_LOCAL_ROAD.toByte()
                    }
                }
            }
            for (y in 0 until height - 4) {
                val idx0 = y * width + x
                val idx4 = (y + 4) * width + x
                if (isRoadType(solidTypes[idx0]) && isRoadType(solidTypes[idx4])) {
                    val idx1 = (y + 1) * width + x
                    val idx2 = (y + 2) * width + x
                    val idx3 = (y + 3) * width + x
                    if (canFillGap(idx1) && canFillGap(idx2) && canFillGap(idx3)) {
                        solidTypes[idx1] = TYPE_LOCAL_ROAD.toByte()
                        solidTypes[idx2] = TYPE_LOCAL_ROAD.toByte()
                        solidTypes[idx3] = TYPE_LOCAL_ROAD.toByte()
                    }
                }
            }
        }
        }

        // 第2パス: 孤立点（点群ノイズ）の除去（完全孤立点のみ消去し、実線化された細道や端点は保護）
        val cleanedTypes = solidTypes.clone()
        for (y in 1 until height - 1) {
            val yOffset = y * width
            for (x in 1 until width - 1) {
                val idx = yOffset + x
                val t = solidTypes[idx].toInt()

                // 水域の点群ノイズ除去（孤立した水色ドットを陸地へ）
                if (t == TYPE_WATER) {
                    var waterNeighbors = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            if (solidTypes[(y + dy) * width + (x + dx)].toInt() == TYPE_WATER) {
                                waterNeighbors++
                            }
                        }
                    }
                    if (waterNeighbors < 3) {
                        cleanedTypes[idx] = TYPE_BG.toByte()
                    }
                    continue
                }

                // 道路の孤立ノイズ除去（完全孤立点のみ消去。端点やT字路を保護）
                if (t == TYPE_LOCAL_ROAD || t == TYPE_HIGHWAY) {
                    var roadNeighbors = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val neighborType = solidTypes[(y + dy) * width + (x + dx)].toInt()
                            if (neighborType == TYPE_LOCAL_ROAD || neighborType == TYPE_HIGHWAY || neighborType == TYPE_ROUTE || neighborType == TYPE_PLANNED) {
                                roadNeighbors++
                            }
                        }
                    }
                    if (roadNeighbors == 0) {
                        cleanedTypes[idx] = TYPE_BG.toByte()
                    }
                }
            }
        }

                // 第3パス: 道路の太線化・途切れ防止（ズーム連動モルフォロジー膨張）
        val dilatedTypes = cleanedTypes.clone()
        for (y in 1 until height - 1) {
            val yOffset = y * width
            for (x in 1 until width - 1) {
                val idx = yOffset + x
                val t = cleanedTypes[idx].toInt()
                if (t == TYPE_HIGHWAY) {
                    // 幹線道路: 広域〜標準（Zoom 13〜16）で上下左右1px膨張（太い線で強調）。超広域（11-12）はシャープに維持
                    if (zoom in 13..16) {
                        for (dy in -1..1) {
                            val ny = y + dy
                            for (dx in -1..1) {
                                if (Math.abs(dy) + Math.abs(dx) == 1) {
                                    val nIdx = ny * width + (x + dx)
                                    if (dilatedTypes[nIdx].toInt() == TYPE_BG) {
                                        dilatedTypes[nIdx] = TYPE_HIGHWAY.toByte()
                                    }
                                }
                            }
                        }
                    }
                } else if (t == TYPE_LOCAL_ROAD) {
                    // 一般道路:
                    // Zoom 13-15 では膨張しない！（画面が埋まるのを防止）
                    // Zoom 17-18（拡大時）のみ、細道がかすれて途切れないよう上下左右1px膨張
                    if (zoom >= 17) {
                        for (dy in -1..1) {
                            val ny = y + dy
                            for (dx in -1..1) {
                                if (Math.abs(dy) + Math.abs(dx) == 1) {
                                    val nIdx = ny * width + (x + dx)
                                    if (dilatedTypes[nIdx].toInt() == TYPE_BG) {
                                        dilatedTypes[nIdx] = TYPE_LOCAL_ROAD.toByte()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 第4パス: Pebble用カラー/モノクロ値へのマッピング
        val result = ByteArray(totalPixels)
        for (i in 0 until totalPixels) {
            val t = dilatedTypes[i].toInt()
            if (isMonochrome) {
                // モノクロ機: 道路（幹線・一般道ともに黒）、計画ルート、実績、アローは黒、背景と水域は白
                result[i] = when (t) {
                    TYPE_ROUTE, TYPE_PLANNED, TYPE_ARROW, TYPE_HIGHWAY, TYPE_LOCAL_ROAD -> 0b11000000.toByte() // Black
                    else -> 0b11111111.toByte() // White (Background)
                }
            } else {
                // カラー機 (Garmin スタンダード配色):
                // 幹線道路は太い黒、一般道路は濃いグレー、水域は水色、計画ルートはマゼンタ、実績は赤、現在地はエレクトリックシアン
                result[i] = when (t) {
                    TYPE_ROUTE -> settings.mapRouteColor.pebbleColorByte
                    TYPE_PLANNED -> settings.mapPlannedColor.pebbleColorByte
                    TYPE_ARROW -> settings.mapLocationColor.pebbleColorByte
                    TYPE_HIGHWAY -> 0b11000000.toByte() // Black (Major Road - Thick)
                    TYPE_LOCAL_ROAD -> 0b11010101.toByte() // Dark Gray (Local Road - Thin)
                    TYPE_WATER -> 0b11011111.toByte() // Baby Blue Eyes (Water)
                    else -> 0b11111111.toByte() // Pure White (Land Background)
                }
            }
        }
        return result
    }

    private fun encodeRLE(pixels: ByteArray): ByteArray {
        val result = mutableListOf<Byte>()
        if (pixels.isEmpty()) return byteArrayOf()
        
        var currentColor = pixels[0]
        var count = 0
        
        for (pixel in pixels) {
            if (pixel == currentColor && count < 255) {
                count++
            } else {
                result.add(count.toByte())
                result.add(currentColor)
                currentColor = pixel
                count = 1
            }
        }
        result.add(count.toByte())
        result.add(currentColor)
        
        return result.toByteArray()
    }

    override fun setCurrentMidId(id: Int) {
        currentMidDataId = id
        settings.lastMidId = id
        settings.save()
        Log.d("PebbleMessenger", "Current Mid ID synced and saved: $id")
    }

    override fun setCurrentLowerId(id: Int) {
        currentLowerDataId = id
        settings.lastLowerId = id
        settings.save()
        if (id >= 100) {
            val graphType = id - 100
            currentGraphTypeId = graphType
            settings.lastGraphTypeId = graphType
            KmpDependencies.trackerEngine.statistics.value.let { stats ->
                sendGraphData(stats)
            }
        }
        Log.d("PebbleMessenger", "Current Lower ID synced and saved: $id")
    }

    override fun openMap() {
        isMapActive = true
        panOffsetPixelsX = 0.0
        panOffsetPixelsY = 0.0
        val currentActivity = KmpDependencies.trackerEngine.statistics.value.activityType
        val defaultZoom = settings.getMapZoomForActivity(currentActivity)
        currentMapZoom = defaultZoom
        Log.i("PebbleMessenger", "openMap: Triggering map screen for $currentActivity, default zoom=$defaultZoom")
        resetAutoCloseTimer()
        scheduleMapRefresh(0L)
    }

    override fun setMapState(isActive: Boolean) {
        isMapActive = isActive
        if (!isActive) {
            mapAutoCloseJob?.cancel()
            mapAutoCloseJob = null
            mapDebounceJob?.cancel()
            mapDebounceJob = null
            mapSendJob?.cancel()
            mapSendJob = null
            isTransmittingChunks = false
            hasPendingMapRefresh = false
            panOffsetPixelsX = 0.0
            panOffsetPixelsY = 0.0
        } else {
            resetAutoCloseTimer()
        }
        Log.d("PebbleMessenger", "Map state synced from watch: $isActive")
    }

    override fun zoomInMap() {
        if (currentMapZoom < 18) {
            currentMapZoom++
            val currentActivity = KmpDependencies.trackerEngine.statistics.value.activityType
            settings.setMapZoomForActivity(currentActivity, currentMapZoom)
            resetAutoCloseTimer()
            Log.i("PebbleMessenger", "Map Zoom In: level $currentMapZoom for $currentActivity (instant refresh)")
            scheduleMapRefresh(0L)
        } else {
            Log.d("PebbleMessenger", "Map Zoom In: already at max zoom (18)")
        }
    }

    override fun zoomOutMap() {
        if (currentMapZoom > 11) {
            currentMapZoom--
            val currentActivity = KmpDependencies.trackerEngine.statistics.value.activityType
            settings.setMapZoomForActivity(currentActivity, currentMapZoom)
            resetAutoCloseTimer()
            Log.i("PebbleMessenger", "Map Zoom Out: level $currentMapZoom for $currentActivity (instant refresh)")
            scheduleMapRefresh(0L)
        } else {
            Log.d("PebbleMessenger", "Map Zoom Out: already at min zoom (11)")
        }
    }

    override fun setMapZoom(zoom: Int) {
        val clamped = zoom.coerceIn(11, 18)
        currentMapZoom = clamped
        val currentActivity = KmpDependencies.trackerEngine.statistics.value.activityType
        settings.setMapZoomForActivity(currentActivity, currentMapZoom)
        Log.i("PebbleMessenger", "Map Zoom set to $currentMapZoom for $currentActivity")
    }

    override fun panMap(dx: Int, dy: Int) {
        // スワイプ移動量 (dx, dy) に合わせてマップ中心をシフト
        panOffsetPixelsX -= dx.toDouble()
        panOffsetPixelsY -= dy.toDouble()
        Log.i("PebbleMessenger", "Map Pan: dx=$dx, dy=$dy -> current offset=($panOffsetPixelsX, $panOffsetPixelsY) (instant refresh)")
        resetAutoCloseTimer()
        scheduleMapRefresh(0L)
    }

    override fun recenterMap() {
        Log.i("PebbleMessenger", "Map Re-center requested (resetting pan offset)")
        panOffsetPixelsX = 0.0
        panOffsetPixelsY = 0.0
        resetAutoCloseTimer()
        scheduleMapRefresh(0L) // リセンターは即時実行
    }

    override fun resetAndToggleMapOrientation() {
        panOffsetPixelsX = 0.0
        panOffsetPixelsY = 0.0
        val currentActivity = KmpDependencies.trackerEngine.statistics.value.activityType
        val defaultZoom = settings.getMapZoomForActivity(currentActivity)
        currentMapZoom = defaultZoom
        isHeadingUp = !isHeadingUp
        Log.i("PebbleMessenger", "resetAndToggleMapOrientation: Re-centered for $currentActivity, zoom=$defaultZoom, isHeadingUp=$isHeadingUp")
        resetAutoCloseTimer()
        scheduleMapRefresh(0L)
    }

    override fun resetMapAutoCloseTimer() {
        Log.d("PebbleMessenger", "resetMapAutoCloseTimer: Operation detected, extending auto-close timer.")
        resetAutoCloseTimer()
    }

    private fun resetAutoCloseTimer() {
        mapAutoCloseJob?.cancel()
        val timeoutSec = settings.mapAutoCloseTimeoutSeconds
        // マップが有効 かつ 画像転送中でない場合のみカウントダウン開始
        // （転送中にタイムアウト時間が消費されて勝手に閉じるのを防止）
        if (timeoutSec > 0 && isMapActive && !isMapTransferring) {
            mapAutoCloseJob = scope.launch {
                delay(timeoutSec * 1000L)
                if (isMapActive && !isMapTransferring) {
                    Log.i("PebbleMessenger", "Map auto-close timeout reached (${timeoutSec}s). Returning to cockpit screen.")
                    sendMapState(false)
                }
            }
        }
    }

    private fun scheduleMapRefresh(delayMs: Long = 0L) {
        if (!isMapActive) return

        if (isTransmittingChunks) {
            hasPendingMapRefresh = true
            Log.d("PebbleMessenger", "scheduleMapRefresh: Chunks transmitting. Refresh marked as pending.")
            return
        }

        mapDebounceJob?.cancel()
        if (delayMs <= 0L) {
            executeMapRefresh()
        } else {
            mapDebounceJob = scope.launch {
                delay(delayMs)
                executeMapRefresh()
            }
        }
    }

    override fun clearMapCache() {
        lastMapPoints = null
        panOffsetPixelsX = 0.0
        panOffsetPixelsY = 0.0
        try {
            synchronized(tileCache) { tileCache.evictAll() }
        } catch (_: Exception) {}
        Log.i("PebbleMessenger", "clearMapCache: Cleared lastMapPoints, pan offsets and tile cache.")
    }

    private fun executeMapRefresh() {
        val stats = KmpDependencies.trackerEngine.statistics.value
        val curLoc = stats.currentLocation
        val points = if (stats.route.isNotEmpty()) {
            stats.route
        } else if (curLoc != null) {
            listOf(curLoc)
        } else if (!lastMapPoints.isNullOrEmpty()) {
            lastMapPoints!!
        } else {
            emptyList()
        }
        if (points.isNotEmpty()) {
            sendMap(points, lastMapWidth, lastMapHeight)
        } else {
            Log.w("PebbleMessenger", "refreshMap: No points available to render map")
        }
    }

    override fun launchWatchApp() {
        scope.launch {
            val targets = PebbleCommandService.lastConnectedWatch?.let { listOf(it) } ?: emptyList()
            try { getSender().startAppOnTheWatch(WATCHAPP_UUID, targets) } catch (e: Exception) {}
        }
    }

    override fun requestWatchInfo() {
        Log.d("PebbleMessenger", "Requesting watch info...")
        scope.launch {
            // 1. PebbleInfoRetriever を使用して接続済みウォッチを取得 (PebbleKit 2 推奨方法)
            // ログによると、ここで platform="emery" 等の情報が取れている
            try {
                val infoRetriever = DefaultPebbleInfoRetriever(context)
                val watchesList = infoRetriever.getConnectedWatches().firstOrNull()
                
                if (!watchesList.isNullOrEmpty()) {
                    val watch = watchesList.first()
                    Log.d("PebbleMessenger", "Found connected watch: $watch")
                    
                    // リフレクションを使用して内部フィールドから情報を抽出
                    val platformStr = try {
                        val field = watch.javaClass.getDeclaredField("platform")
                        field.isAccessible = true
                        field.get(watch)?.toString()
                    } catch (_: Exception) { null }

                    val watchName = try {
                        val field = watch.javaClass.getDeclaredField("name")
                        field.isAccessible = true
                        field.get(watch)?.toString()
                    } catch (_: Exception) { "" }

                    // 送信先 (WatchIdentifier) も自動復旧を試みる
                    if (PebbleCommandService.lastConnectedWatch == null) {
                        try {
                            val idField = watch.javaClass.getDeclaredField("id")
                            idField.isAccessible = true
                            (idField.get(watch) as? WatchIdentifier)?.let {
                                PebbleCommandService.lastConnectedWatch = it
                                Log.i("PebbleMessenger", "Recovered lastConnectedWatch from retriever: $it")
                            }
                        } catch (_: Exception) {
                            try {
                                val idMethod = watch.javaClass.getMethod("getId")
                                (idMethod.invoke(watch) as? WatchIdentifier)?.let {
                                    PebbleCommandService.lastConnectedWatch = it
                                    Log.i("PebbleMessenger", "Recovered lastConnectedWatch via method: $it")
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    if (platformStr != null || !watchName.isNullOrEmpty()) {
                        val identifiedModel = when {
                            platformStr?.contains("emery", ignoreCase = true) == true -> "Pebble Time 2"
                            platformStr?.contains("chalk", ignoreCase = true) == true -> "Pebble Time Round"
                            platformStr?.contains("diorite", ignoreCase = true) == true -> "Pebble 2"
                            platformStr?.contains("basalt", ignoreCase = true) == true -> "Pebble Time / Time Steel"
                            platformStr?.contains("aplite", ignoreCase = true) == true -> "Pebble Classic / Steel"
                            watchName?.contains("Round 2", ignoreCase = true) == true -> "Pebble Round 2"
                            else -> {
                                when {
                                    watchName?.contains("Time 2", ignoreCase = true) == true -> "Pebble Time 2"
                                    watchName?.contains("Round", ignoreCase = true) == true -> "Pebble Time Round"
                                    watchName?.contains("Time", ignoreCase = true) == true -> "Pebble Time"
                                    watchName?.contains("Pebble 2", ignoreCase = true) == true -> "Pebble 2"
                                    else -> "Pebble Watch (${platformStr ?: watchName})"
                                }
                            }
                        }
                        
                        Log.i("PebbleMessenger", "Identified Model: $identifiedModel")
                        settings.pebblePlatform = identifiedModel
                        settings.save()
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w("PebbleMessenger", "InfoRetriever extraction failed: ${e.message}")
            }

            // 2. Content Provider から詳細情報を取得 (フォールバック)
            val pebblePackages = listOf("io.rebble.cobble", "com.getpebble.android", "coredevices.coreapp")
            for (pkg in pebblePackages) {
                val uris = listOf(
                    Uri.parse("content://$pkg/connected_watch"),
                    Uri.parse("content://$pkg.provider/connected_watch"),
                    Uri.parse("content://$pkg.pebble/connected_watch")
                )
                
                for (uri in uris) {
                    try {
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        cursor?.use { c ->
                            if (c.moveToFirst()) {
                                // デバッグ用に全てのカラム名をログに出力
                                val cols = c.columnNames.joinToString(", ")
                                Log.i("PebbleMessenger", "Provider found at $uri. Columns: $cols")

                                val nameIdx = c.getColumnIndex("name")
                                val modelIdx = c.getColumnIndex("model")
                                val platformIdx = c.getColumnIndex("platform")
                                val hwIdx = c.getColumnIndex("hardware")
                                
                                val watchName = if (nameIdx != -1) c.getString(nameIdx) else ""
                                val modelId = when {
                                    modelIdx != -1 -> c.getInt(modelIdx)
                                    platformIdx != -1 -> c.getInt(platformIdx)
                                    hwIdx != -1 -> c.getInt(hwIdx)
                                    else -> -1
                                }
                                
                                Log.i("PebbleMessenger", "Watch Data from Provider: name='$watchName', modelId=$modelId")

                                if (modelId != -1 || watchName.isNotEmpty()) {
                                    val platformName = when (modelId) {
                                        1 -> "Pebble Classic / Steel"
                                        2 -> "Pebble Time / Time Steel"
                                        3 -> "Pebble Time Round"
                                        4 -> "Pebble 2"
                                        5 -> "Pebble Time 2"
                                        6 -> "Pebble Round 2"
                                        else -> {
                                            when {
                                                watchName.contains("Round 2", ignoreCase = true) -> "Pebble Round 2"
                                                watchName.contains("Time Round", ignoreCase = true) || watchName.contains("Chalk", ignoreCase = true) -> "Pebble Time Round"
                                                watchName.contains("Time 2", ignoreCase = true) || watchName.contains("Emery", ignoreCase = true) -> "Pebble Time 2"
                                                watchName.contains("Time", ignoreCase = true) || watchName.contains("Basalt", ignoreCase = true) -> "Pebble Time / Time Steel"
                                                watchName.contains("Pebble 2", ignoreCase = true) || watchName.contains("Diorite", ignoreCase = true) -> "Pebble 2"
                                                watchName.contains("Classic", ignoreCase = true) || watchName.contains("Aplite", ignoreCase = true) -> "Pebble Classic / Steel"
                                                else -> if (watchName.isNotEmpty()) "Pebble Watch ($watchName)" else "Unknown Pebble"
                                            }
                                        }
                                    }
                                    
                                    Log.i("PebbleMessenger", "Identified Platform via Provider: $platformName")
                                    settings.pebblePlatform = platformName
                                    settings.save()
                                    return@launch
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 3. 最終手段: レガシーな Broadcast (SEND_FW_VERSION)
            try {
                val intent = Intent("com.getpebble.action.app.SEND_FW_VERSION")
                for (p in pebblePackages) {
                    intent.setPackage(p)
                    context.sendBroadcast(intent)
                }
                
                val filter = IntentFilter("com.getpebble.action.app.RECEIVE_FW_VERSION")
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        if (intent == null) return
                        val platform = intent.getIntExtra("platform", -1)
                        if (platform != -1) {
                            val platformName = when (platform) {
                                1 -> "Pebble Classic / Steel"
                                2 -> "Pebble Time / Time Steel"
                                3 -> "Pebble Time Round"
                                4 -> "Pebble 2"
                                5 -> "Pebble Time 2"
                                6 -> "Pebble Round 2"
                                else -> "Pebble Watch (ID: $platform)"
                            }
                            Log.i("PebbleMessenger", "Broadcast info received: $platformName")
                            settings.pebblePlatform = platformName
                            settings.save()
                            try { context.unregisterReceiver(this) } catch (_: Exception) {}
                        }
                    }
                }
                
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
                delay(8000)
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                
            } catch (e: Exception) {
                Log.e("PebbleMessenger", "Broadcast request failed", e)
            }
        }
    }

    private fun mapToPebbleState(status: RunStatus): Int = when (status) {
        RunStatus.PREPARING -> 1
        RunStatus.READY -> 2
        RunStatus.ACTIVE -> 3
        RunStatus.PAUSED -> 4
        RunStatus.FINISHED -> 5
        RunStatus.RESULT -> 6
        else -> 0
    }

    private data class PebbleMessageRequest(val tag: String, val dictionary: Map<UInt, PebbleDictionaryItem>, val retryCount: Int = 0)
}
