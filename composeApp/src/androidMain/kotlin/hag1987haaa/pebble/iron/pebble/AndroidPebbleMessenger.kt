package hag1987haaa.pebble.iron.pebble

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
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
    private var isMapActive: Boolean = false
    private var isMapTransferring: Boolean = false

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

    override fun sendMap(points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>, width: Int, height: Int) {
        scope.launch {
            if (isMapTransferring) {
                Log.w("PebbleMessenger", "sendMap: Already transferring. Ignored.")
                return@launch
            }
            isMapTransferring = true
            
            try {
                Log.i("PebbleMessenger", "sendMap: Starting fast transmission... (w=$width, h=$height, points=${points.size})")
                
                val activePoints = if (points.isEmpty()) {
                    val now = kotlinx.datetime.Clock.System.now()
                    listOf(
                        hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6812, 139.7671, timestamp = now), 
                        hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6812, 139.7701, timestamp = now),
                        hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6782, 139.7701, timestamp = now),
                        hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6782, 139.7671, timestamp = now),
                        hag1987haaa.pebble.iron.domain.model.LocationPoint(35.6812, 139.7671, timestamp = now)
                    )
                } else {
                    points
                }

                // 1. マップ表示コマンドを送信
                sendMapState(true)
                delay(200) // 画面遷移待機（短縮）

                // 2. ビットマップ生成 (IOスレッドで並列タイル取得)
                val bitmap = renderMapBitmapWithTiles(activePoints, width, height)
                
                // 3. Pebble 8-bit カラー変換 & RLE エンコード
                val isMonochrome = settings.pebblePlatform?.let { 
                    it.contains("Classic") || it.contains("Pebble 2") 
                } ?: false

                val pebblePixels = convertToPebblePixels(bitmap, isMonochrome)
                bitmap.recycle()
                val rleData = encodeRLE(pebblePixels)
                
                val totalSize = rleData.size
                Log.i("PebbleMessenger", "sendMap: RLE encoded size = $totalSize bytes")

                // 4. チャンク分割送信 (500バイトチャンク + 180msディレイ)
                val chunkSize = 500 
                val totalChunks = (totalSize + chunkSize - 1) / chunkSize
                
                val fixedDelayMs = 180L 
                val estimatedTimeSec = ((totalChunks * fixedDelayMs) / 1000.0)
                Log.i("PebbleMessenger", "sendMap: Total chunks = $totalChunks. Estimated time = ${estimatedTimeSec}s")

                for (i in 0 until totalChunks) {
                    val start = i * chunkSize
                    val end = minOf(start + chunkSize, totalSize)
                    val chunk = rleData.sliceArray(start until end)
                    
                    Log.d("PebbleMessenger", "sendMap: Sending chunk ${i + 1}/$totalChunks (${chunk.size} bytes)")
                    sendMapChunk(chunk, i, totalChunks)
                    
                    delay(fixedDelayMs) 
                }
                Log.i("PebbleMessenger", "sendMap: Fully transmitted $totalSize bytes in $totalChunks chunks.")
            } catch (e: Exception) {
                Log.e("PebbleMessenger", "sendMap: Error during transmission: ${e.message}")
            } finally {
                isMapTransferring = false
                Log.d("PebbleMessenger", "sendMap: Transmission lock released. Syncing latest stats.")
                // 転送完了直後に最新のSTATS/MID_DATAをウォッチへ即座に送信
                nextStatsRequest?.let { commandQueue.trySend(it) }
                nextMidDataRequest?.let { commandQueue.trySend(it) }
                nextLowerDataRequest?.let { commandQueue.trySend(it) }
            }
        }
    }

    private suspend fun renderMapBitmapWithTiles(points: List<hag1987haaa.pebble.iron.domain.model.LocationPoint>, width: Int, height: Int): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.LTGRAY)

        if (points.isEmpty()) return@withContext bitmap

        // 1. 中心の決定 (最新の地点を画像の中央にする)
        val currentPoint = points.last()
        val centerLat = currentPoint.latitude
        val centerLon = currentPoint.longitude
        
        // 2. ズームレベルの設定 (半径約500m表示のため 14 に設定)
        val zoom = 14
        val n = Math.pow(2.0, zoom.toDouble())

        // メルカトル投影での世界座標ピクセル (256pxタイル基準)
        val xCenterWorld = (centerLon + 180.0) / 360.0 * n * 256.0
        val yCenterWorld = (1.0 - Math.log(Math.tan(Math.toRadians(centerLat)) + (1.0 / Math.cos(Math.toRadians(centerLat)))) / Math.PI) / 2.0 * n * 256.0

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
                        val connection = java.net.URL(tileUrl).openConnection() as java.net.HttpURLConnection
                        connection.setRequestProperty("User-Agent", "TrackerIronAndroid/1.0")
                        connection.connectTimeout = 2500
                        connection.readTimeout = 2500
                        val tileBitmap = android.graphics.BitmapFactory.decodeStream(connection.inputStream)
                        if (tileBitmap != null) {
                            val tileLeftWorld = curX * 256.0
                            val tileTopWorld = curY * 256.0
                            val dx = (tileLeftWorld - xCenterWorld + (width / 2.0)).toFloat()
                            val dy = (tileTopWorld - yCenterWorld + (height / 2.0)).toFloat()
                            Triple(tileBitmap, dx, dy)
                        } else null
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
            tileBitmap.recycle()
        }

        // 4. ルート (Polyline) の描画
        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = false
        }

        val path = android.graphics.Path()
        points.forEachIndexed { index, point ->
            val px = (point.longitude + 180.0) / 360.0 * n * 256.0
            val py = (1.0 - Math.log(Math.tan(Math.toRadians(point.latitude)) + (1.0 / Math.cos(Math.toRadians(point.latitude)))) / Math.PI) / 2.0 * n * 256.0
            
            val dx = (px - xCenterWorld + (width / 2.0)).toFloat()
            val dy = (py - yCenterWorld + (height / 2.0)).toFloat()
            
            if (index == 0) path.moveTo(dx, dy) else path.lineTo(dx, dy)
        }
        canvas.drawPath(path, paint)

        // 5. 現在地（中心点）を強調
        paint.style = Paint.Style.FILL
        paint.color = Color.BLUE
        canvas.drawCircle(width / 2f, height / 2f, 6f, paint)
        
        // 中心点に白枠をつけて視認性向上
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 2f
        canvas.drawCircle(width / 2f, height / 2f, 6f, paint)

        bitmap
    }

    private fun convertToPebblePixels(bitmap: Bitmap, isMonochrome: Boolean): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val result = ByteArray(width * height)
        for (i in pixels.indices) {
            val color = pixels[i]
            if (isMonochrome) {
                // 輝度計算 (Y = 0.299R + 0.587G + 0.114B)
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                // しきい値 140 で白黒判定（少し明るめにして地図の白地を強調）
                result[i] = if (luminance > 140) 0b11111111.toByte() else 0b11000000.toByte()
            } else {
                val r = (Color.red(color) shr 6) and 0x03
                val g = (Color.green(color) shr 6) and 0x03
                val b = (Color.blue(color) shr 6) and 0x03
                result[i] = (0b11000000 or (r shl 4) or (g shl 2) or b).toByte()
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

    override fun setMapState(isActive: Boolean) {
        isMapActive = isActive
        Log.d("PebbleMessenger", "Map state synced from watch: $isActive")
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
