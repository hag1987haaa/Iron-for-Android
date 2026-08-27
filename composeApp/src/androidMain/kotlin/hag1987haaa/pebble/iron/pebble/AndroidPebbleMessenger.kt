package hag1987haaa.pebble.iron.pebble

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
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
    private var nextGraphRequest: PebbleMessageRequest? = null

    private var cachedSender: DefaultPebbleSender? = null
    
    // そのワークアウト中に使う固定のリスト順序（計測中は一切変えない）
    private var sessionMidList: List<Int> = emptyList()
    // 今実際に Pebble の画面に表示されている ID
    private var currentMidDataId: Int = -1
    private var currentGraphTypeId: Int = settings.lastGraphTypeId

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
            KEY_TYPE to PebbleDictionaryItem.Int32(stats.activityType.ordinal)
        )
        nextStatsRequest = PebbleMessageRequest("STATS", dict)
        sendMidData(stats)
    }

    private fun ensureSessionInitialized(status: RunStatus, settings: hag1987haaa.pebble.iron.domain.settings.AppSettings) {
        val enabled = settings.enabledMidTypes
        if (enabled.isEmpty()) return

        // 1. セッションリストの構築
        // 常に設定リストの順序通りに初期化する（前回の状態は引き継がない）
        if (sessionMidList.isEmpty() || sessionMidList.size != enabled.size || !sessionMidList.containsAll(enabled)) {
            sessionMidList = enabled
            currentMidDataId = enabled.first()
            Log.d("PebbleMessenger", "Session initialized. List=$sessionMidList")
        }

        // 計測中に一時停止から復帰した際などは、リストの先頭に同期
        if (status == RunStatus.ACTIVE && currentMidDataId == 99) {
            currentMidDataId = enabled.first()
        }

        if (currentGraphTypeId == -1) {
            currentGraphTypeId = settings.lastGraphTypeId
            if (currentGraphTypeId !in settings.enabledGraphTypes) {
                currentGraphTypeId = settings.enabledGraphTypes.firstOrNull() ?: -1
            }
        }
    }

    private fun sendMidData(stats: RunStatistics) {
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

    private fun generateMidPageString(typeId: Int, stats: RunStatistics, settings: hag1987haaa.pebble.iron.domain.settings.AppSettings): String? {
        val unitStr = if (settings.isMetric) "/km" else "/mi"
        return when (typeId) {
            0 -> "30s PACE,${calculateWindowedPace(stats, 30, settings.isMetric)},$unitStr,0"
            1 -> "DIST,${formatDistance(stats.totalDistanceMeters, settings.isMetric)},${if (settings.isMetric) "km" else "mi"},0"
            2 -> "STEPS,${stats.steps},steps,0"
            3 -> {
                val alt = if (settings.isMetric) (stats.route.lastOrNull()?.altitude ?: 0.0).toInt() 
                          else ((stats.route.lastOrNull()?.altitude ?: 0.0) * 3.28084).toInt()
                "ALT,$alt,${if (settings.isMetric) "m" else "ft"},0"
            }
            4 -> {
                val hr = stats.currentHeartRate?.toString() ?: "--"
                val label = if (stats.hrSource == "BLE") "HR (Ext)" else "HR (Int)"
                "$label,$hr,bpm,0"
            }
            5 -> "CAL,${stats.calories.toInt()},kcal,0"
            7 -> "AVG PACE,${formatPace(stats.totalDistanceMeters, stats.totalSeconds, settings.isMetric)},$unitStr,0"
            8 -> "SPEED,${formatSpeed(stats.totalDistanceMeters, stats.totalSeconds, settings.isMetric)},${if (settings.isMetric) "km/h" else "mph"},0"
            9 -> {
                val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                "CLOCK,${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')},,0"
            }
            10 -> {
                val gain = if (settings.isMetric) stats.totalElevationGain.toInt() else (stats.totalElevationGain * 3.28084).toInt()
                "GAIN,$gain,${if (settings.isMetric) "m" else "ft"},0"
            }
            11 -> "CADENCE,${calculateCurrentCadence(stats)},spm,0"
            12 -> "BLE HR,${stats.latestBleHeartRate ?: "--"},bpm,0"
            13 -> "WATCH HR,${stats.latestPebbleHeartRate ?: "--"},bpm,0"
            14 -> "1m PACE,${calculateWindowedPace(stats, 60, settings.isMetric)},$unitStr,0"
            15 -> "2m PACE,${calculateWindowedPace(stats, 120, settings.isMetric)},$unitStr,0"
            16 -> "5m PACE,${calculateWindowedPace(stats, 300, settings.isMetric)},$unitStr,0"
            17 -> "10m PACE,${calculateWindowedPace(stats, 600, settings.isMetric)},$unitStr,0"
            99 -> ",DETAIL,,0"
            else -> null
        }
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
