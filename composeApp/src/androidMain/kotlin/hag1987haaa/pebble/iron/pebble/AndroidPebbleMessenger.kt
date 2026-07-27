package hag1987haaa.pebble.iron.pebble

import android.content.Context
import android.util.Log
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import hag1987haaa.pebble.iron.domain.tracker.RunStatistics
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import hag1987haaa.pebble.iron.domain.tracker.PebbleMessenger
import hag1987haaa.pebble.iron.KmpDependencies
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

class AndroidPebbleMessenger(private val context: Context) : PebbleMessenger {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandQueue = Channel<PebbleMessageRequest>(Channel.UNLIMITED)
    
    @Volatile
    private var nextStatsRequest: PebbleMessageRequest? = null
    @Volatile
    private var nextMidDataRequest: PebbleMessageRequest? = null

    private var cachedSender: DefaultPebbleSender? = null

    // 現在表示中の項目ID
    private var currentMidDataId: Int = -1
    private var currentGraphTypeId: Int = -1

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
                    val cmd = commandQueue.tryReceive().getOrNull()
                    if (cmd != null) {
                        processRequest(cmd)
                        delay(200) 
                        continue
                    }
                    val stats = nextStatsRequest
                    if (stats != null) {
                        nextStatsRequest = null 
                        processRequest(stats)
                        delay(100)
                    }
                    val mid = nextMidDataRequest
                    if (mid != null) {
                        nextMidDataRequest = null
                        processRequest(mid)
                        delay(100)
                    }
                    if (stats == null && mid == null) delay(50)
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
                success
            }
        } catch (e: Exception) {
            cachedSender = null
            false
        }
    }

    override fun sendStatistics(stats: RunStatistics) {
        val settings = KmpDependencies.appSettings
        syncIdsFromSettings(settings)

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

    private fun syncIdsFromSettings(settings: hag1987haaa.pebble.iron.domain.settings.AppSettings) {
        // メモリ上にない場合は設定からロード
        if (currentMidDataId == -1) currentMidDataId = settings.lastMidDataId
        if (currentGraphTypeId == -1) currentGraphTypeId = settings.lastGraphTypeId

        val enabledMid = settings.enabledMidTypes
        // ★重要: 現在表示しようとしているIDが、ユーザーが有効にしているリストにあるかチェック
        // なければ（一時停止中のコックピットID 99など）、リストの先頭を採用する
        if (currentMidDataId == -1 || currentMidDataId !in enabledMid) {
            currentMidDataId = enabledMid.firstOrNull() ?: -1
        }

        val enabledGraphs = settings.enabledGraphTypes
        if (currentGraphTypeId == -1 || currentGraphTypeId !in enabledGraphs) {
            currentGraphTypeId = enabledGraphs.firstOrNull() ?: -1
        }
    }

    private fun sendMidData(stats: RunStatistics) {
        val settings = KmpDependencies.appSettings
        val enabledTypes = settings.enabledMidTypes
        if (enabledTypes.isEmpty()) return

        // 全ページの文字列を生成
        val allPages = enabledTypes.mapNotNull { typeId -> generateMidPageString(typeId, stats, settings) }
        if (allPages.isEmpty()) return

        // 表示すべきIDのインデックスを探す
        val targetIdx = enabledTypes.indexOf(currentMidDataId).coerceAtLeast(0)

        val finalPages = if (stats.status == RunStatus.PAUSED) {
            // 一時停止中はコックピット(99)を先頭にするが、currentMidDataIdは変更しない！
            val cockpitPage = generateMidPageString(99, stats, settings)
            if (cockpitPage != null) {
                listOf(cockpitPage) + allPages.filter { !it.contains(",DETAIL,") }
            } else allPages
        } else {
            // 通常時はお気に入りIDが先頭に来るようにリストを回転
            allPages.drop(targetIdx) + allPages.take(targetIdx)
        }

        val midDataString = finalPages.joinToString("|")
        val dict = mapOf(KEY_MID_DATA to PebbleDictionaryItem.Text(midDataString))
        nextMidDataRequest = PebbleMessageRequest("MID_DATA", dict)
    }

    private fun generateMidPageString(typeId: Int, stats: RunStatistics, settings: hag1987haaa.pebble.iron.domain.settings.AppSettings): String? {
        return when (typeId) {
            0 -> "PACE,${formatPace(stats.totalDistanceMeters, stats.totalSeconds, settings.isMetric)},${if (settings.isMetric) "/km" else "/mi"},0"
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
            7 -> "AVG PACE,${formatPace(stats.totalDistanceMeters, stats.totalSeconds, settings.isMetric)},${if (settings.isMetric) "/km" else "/mi"},0"
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
            99 -> ",DETAIL,,0"
            else -> null
        }
    }

    override fun rotateMidData(stats: RunStatistics) {
        val settings = KmpDependencies.appSettings
        val enabled = settings.enabledMidTypes
        if (enabled.isEmpty()) return

        // リストの中から現在のIDの次を探す
        val currentIdx = enabled.indexOf(currentMidDataId)
        val nextIdx = (currentIdx + 1) % enabled.size
        val nextId = enabled[nextIdx]

        currentMidDataId = nextId
        
        // ★重要: リストにある正規のIDなら永続化。99（コックピット）などは保存しない。
        if (nextId in enabled && nextId != 99) {
            settings.lastMidDataId = nextId
            settings.save()
        }
        sendMidData(stats)
    }

    override fun rotateGraphType(stats: RunStatistics) {
        val settings = KmpDependencies.appSettings
        val enabled = settings.enabledGraphTypes
        if (enabled.isEmpty()) return

        val currentIdx = enabled.indexOf(currentGraphTypeId)
        val nextIdx = (currentIdx + 1) % enabled.size
        val nextId = enabled[nextIdx]

        currentGraphTypeId = nextId
        if (nextId in enabled) {
            settings.lastGraphTypeId = nextId
            settings.save()
        }
        sendGraphData(stats)
    }

    override fun sendGraphData(stats: RunStatistics) {
        scope.launch {
            val settings = KmpDependencies.appSettings
            syncIdsFromSettings(settings)
            val enabled = settings.enabledGraphTypes
            if (enabled.isEmpty()) return@launch

            val typeToSend = if (currentGraphTypeId in enabled) currentGraphTypeId else enabled[0]
            val unifiedGraph = GraphDataGenerator.generateUnifiedGraph(stats, typeToSend)
            commandQueue.trySend(PebbleMessageRequest("GRAPH", mapOf(KEY_GRAPH_DATA to PebbleDictionaryItem.Text(unifiedGraph))))
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
            val ff = if (fractionalPart < 10) "0$fractionalPart" else "$fractionalPart"
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

    override fun sendState(status: RunStatus) {
        val dict = mapOf(KEY_CMD to PebbleDictionaryItem.Int32(1), KEY_STATE to PebbleDictionaryItem.Int32(mapToPebbleState(status)))
        commandQueue.trySend(PebbleMessageRequest("STATE", dict, retryCount = 3))
    }

    override fun sendFullSync(stats: RunStatistics) {
        val settings = KmpDependencies.appSettings
        syncIdsFromSettings(settings)
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
