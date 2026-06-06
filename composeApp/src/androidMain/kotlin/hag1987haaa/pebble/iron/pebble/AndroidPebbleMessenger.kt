package hag1987haaa.pebble.iron.pebble

import android.content.Context
import android.util.Log
import io.rebble.pebblekit2.client.DefaultPebbleAndroidAppPicker
import io.rebble.pebblekit2.client.DefaultPebbleSender
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.TransmissionResult
import io.rebble.pebblekit2.common.model.WatchIdentifier
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import hag1987haaa.pebble.iron.domain.tracker.RunStatistics
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import hag1987haaa.pebble.iron.domain.tracker.PebbleMessenger
import kotlinx.datetime.toLocalDateTime
import java.util.UUID

class AndroidPebbleMessenger(private val context: Context) : PebbleMessenger {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // コマンドキュー (STATE変更, SYNC, グラフ切り替え時)
    private val commandQueue = Channel<PebbleMessageRequest>(Channel.UNLIMITED)
    
    // 統計データ（最新1件のみ保持。volatileでスレッドセーフにアクセス）
    @Volatile
    private var nextStatsRequest: PebbleMessageRequest? = null
    @Volatile
    private var nextMidDataRequest: PebbleMessageRequest? = null

    private var cachedSender: DefaultPebbleSender? = null

    companion object {
        private val WATCHAPP_UUID = UUID.fromString("0ec71971-1191-4e05-87f5-27a3c749023c")
        
        private const val KEY_CMD = 10000u
        private const val KEY_TIME = 10001u
        private const val KEY_DISTANCE = 10002u
        private const val KEY_PACE = 10003u
        private const val KEY_STATE = 10004u
        private const val KEY_GRAPH_DATA = 10009u
        private const val KEY_HR = 10007u
        private const val KEY_TOUCH_ENABLE = 10011u
        private const val KEY_TYPE = 10012u 
        private const val KEY_MID_DATA = 10013u // ★ 中段カスタムデータ
        
        private const val PEBBLE_STATE_IDLE = 0
        private const val PEBBLE_STATE_GPS_SEARCHING = 1
        private const val PEBBLE_STATE_READY = 2
        private const val PEBBLE_STATE_RUNNING = 3
        private const val PEBBLE_STATE_PAUSED = 4
        private const val PEBBLE_STATE_FINISHED = 5
        private const val PEBBLE_STATE_RESULT = 6
        
        private const val SEND_TIMEOUT_MS = 2500L
    }

    init {
        // 通信専用ワーカー：一つのループで確実に順番を守って送信
        scope.launch {
            while (isActive) {
                try {
                    // 1. コマンド（ボタン操作など）を優先
                    val cmd = commandQueue.tryReceive().getOrNull()
                    if (cmd != null) {
                        processRequest(cmd)
                        delay(200) // パケット間の最小間隔
                        continue
                    }

                    // 2. 統計データ（1秒おき）を処理
                    val stats = nextStatsRequest
                    if (stats != null) {
                        nextStatsRequest = null // 消費
                        processRequest(stats)
                        delay(100)
                    }

                    // 3. 中段データ
                    val mid = nextMidDataRequest
                    if (mid != null) {
                        nextMidDataRequest = null
                        processRequest(mid)
                        delay(100)
                    }

                    if (stats == null && mid == null) {
                        // 送信キューが空なら待機
                        delay(50)
                    }
                } catch (e: Exception) {
                    Log.e("PebbleMessenger", "Worker loop error", e)
                    delay(500)
                }
            }
        }
        Log.i("PebbleMessenger", "AndroidPebbleMessenger: HIGH-PERFORMANCE SEQUENTIAL WORKER START.")
    }

    private fun getSender(): DefaultPebbleSender {
        // Senderを使い回すことで、毎回のバインド処理（タイムアウトの原因）を回避
        return cachedSender ?: DefaultPebbleSender(context).also { cachedSender = it }
    }

    private suspend fun processRequest(request: PebbleMessageRequest) {
        Log.i("PebbleMessenger", "Processing request: ${request.tag}")
        // もし直近で通信したウォッチがいればそれを使うが、
        // いない場合は現在接続されている全てのウォッチを宛先にする
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
                // targets が null の場合、PebbleKit2 は接続されている全てのウォッチに送信を試みる
                val results = getSender().sendDataToPebble(WATCHAPP_UUID, dict, targets)
                
                // 宛先が不明でも、この送信によって一つでも成功（または送信試行）すればOKとする
                val success = results?.all { it.value == TransmissionResult.Success } ?: false
                
                // 送信に成功したウォッチがあれば、それを次回の優先宛先として保存
                if (success && results != null && results.isNotEmpty()) {
                    PebbleCommandService.lastConnectedWatch = results.keys.first()
                }

                if (!success) Log.w("PebbleMessenger", "Send failed for $tag")
                success
            }
        } catch (e: Exception) {
            Log.e("PebbleMessenger", "Send error for $tag: ${e.message}")
            // エラー時はSenderをリセットして次回のバインドを試みる
            cachedSender = null
            false
        }
    }

    override fun sendStatistics(stats: RunStatistics) {
        // 10秒おきにログを出力して生存確認
        if (stats.totalSeconds > 0 && stats.totalSeconds % 10 == 0L) {
            val settings = hag1987haaa.pebble.iron.KmpDependencies.appSettings
            Log.i("PebbleMessenger", "sendStatistics: time=${stats.totalSeconds}s dist=${stats.totalDistanceMeters}m status=${stats.status} notifTime=${settings.notificationTimeSeconds}")
        }
        val pebbleState = mapToPebbleState(stats.status)
        val dict = mapOf(
            KEY_CMD to PebbleDictionaryItem.Int32(1), 
            KEY_TIME to PebbleDictionaryItem.Text(stats.formattedTime),
            KEY_DISTANCE to PebbleDictionaryItem.Text(stats.formattedDistance),
            KEY_PACE to PebbleDictionaryItem.Text(stats.formattedPace),
            KEY_STATE to PebbleDictionaryItem.Int32(pebbleState),
            KEY_HR to PebbleDictionaryItem.Text(if (stats.heartRates.isNotEmpty()) stats.heartRates.last().toString() else "--"),
            KEY_TYPE to PebbleDictionaryItem.Int32(stats.activityType.ordinal)
        )
        // 常に最新1件のみを保持
        nextStatsRequest = PebbleMessageRequest("STATS", dict)
        
        // ★ 中段データの送信もトリガー
        sendMidData(stats)
    }

    private fun sendMidData(stats: RunStatistics) {
        val settings = hag1987haaa.pebble.iron.KmpDependencies.appSettings
        val pages = mutableListOf<String>()

        settings.enabledMidTypes.forEach { typeId ->
            val pageStr = when (typeId) {
                0 -> { // Instant Pace
                    val unit = if (settings.isMetric) "/km" else "/mile"
                    "PACE,${stats.formattedPace},$unit,0"
                }
                1 -> { // Distance
                    val unit = if (settings.isMetric) "km" else "mile"
                    "DIST,${stats.formattedDistance},$unit,0"
                }
                2 -> { // Steps
                    "STEPS,${stats.steps},steps,0"
                }
                3 -> { // Altitude
                    val unit = if (settings.isMetric) "m" else "ft"
                    val alt = stats.route.lastOrNull()?.altitude?.toInt() ?: 0
                    "ALT,$alt,$unit,0"
                }
                4 -> { // Heart Rate
                    val hr = if (stats.heartRates.isNotEmpty()) stats.heartRates.last().toString() else "--"
                    "HEART,$hr,bpm,0"
                }
                5 -> { // Calories
                    "CAL,${stats.calories.toInt()},kcal,0"
                }
                7 -> { // Avg Pace
                    val unit = if (settings.isMetric) "/km" else "/mile"
                    "AVG PACE,${stats.formattedAvgPace ?: "--:--"},$unit,0"
                }
                8 -> { // Speed
                    val unit = if (settings.isMetric) "km/h" else "mph"
                    "SPEED,${stats.formattedSpeed ?: "0.0"},$unit,0"
                }
                9 -> { // Clock
                    val now = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
                    val timeStr = "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
                    "CLOCK,$timeStr,,0"
                }
                10 -> { // Gain (Elevation)
                    val unit = if (settings.isMetric) "m" else "ft"
                    "GAIN,${stats.totalElevationGain.toInt()},$unit,0"
                }
                11 -> { // Cadence (SPM)
                    // 簡易的に最新の歩数変化から計算するか、平均を出す
                    "CADENCE,${calculateCurrentCadence(stats)},spm,0"
                }
                99 -> { // Detail Mode (High Density)
                    // 空タイトルをフラグにする
                    ",DETAIL,,0"
                }
                else -> null
            }
            pageStr?.let { pages.add(it) }
        }

        if (pages.isEmpty()) return

        val midDataString = pages.joinToString("|")
        val dict = mapOf(KEY_MID_DATA to PebbleDictionaryItem.Text(midDataString))
        nextMidDataRequest = PebbleMessageRequest("MID_DATA", dict)
    }

    private fun calculateCurrentCadence(stats: RunStatistics): Int {
        if (stats.route.size < 2) return 0
        val last = stats.route.last()
        val prev = stats.route[stats.route.size - 2]
        val stepDiff = (last.steps ?: 0) - (prev.steps ?: 0)
        val timeDiffSec = (last.timestamp.epochSeconds - prev.timestamp.epochSeconds).coerceAtLeast(1)
        return (stepDiff.toDouble() / timeDiffSec.toDouble() * 60.0).toInt().coerceIn(0, 250)
    }

    override fun sendState(status: RunStatus) {
        val pebbleState = mapToPebbleState(status)
        val dict = mapOf(
            KEY_CMD to PebbleDictionaryItem.Int32(1),
            KEY_STATE to PebbleDictionaryItem.Int32(pebbleState)
        )
        // 状態変更は「コマンド」として確実に送る
        commandQueue.trySend(PebbleMessageRequest("STATE", dict, retryCount = 3))
    }

    override fun sendFullSync(stats: RunStatistics) {
        val pebbleState = mapToPebbleState(stats.status)
        val dict = mapOf(
            KEY_CMD to PebbleDictionaryItem.Int32(5), 
            KEY_TIME to PebbleDictionaryItem.Text(stats.formattedTime),
            KEY_DISTANCE to PebbleDictionaryItem.Text(stats.formattedDistance),
            KEY_PACE to PebbleDictionaryItem.Text(stats.formattedPace),
            KEY_STATE to PebbleDictionaryItem.Int32(pebbleState),
            KEY_HR to PebbleDictionaryItem.Text(if (stats.heartRates.isNotEmpty()) stats.heartRates.last().toString() else "--"),
            KEY_TYPE to PebbleDictionaryItem.Int32(stats.activityType.ordinal) // ★ 現在の種別インデックスを同送
        )
        commandQueue.trySend(PebbleMessageRequest("SYNC", dict, retryCount = 3))
        sendGraphData(stats)
    }

    private var currentGraphListIndex = 0 
    override fun rotateGraphType(stats: RunStatistics) {
        val enabledGraphs = hag1987haaa.pebble.iron.KmpDependencies.appSettings.enabledGraphTypes
        if (enabledGraphs.isEmpty()) return

        currentGraphListIndex = (currentGraphListIndex + 1) % enabledGraphs.size
        sendGraphData(stats)
    }

    override fun sendGraphData(stats: RunStatistics) {
        scope.launch {
            val enabledGraphs = hag1987haaa.pebble.iron.KmpDependencies.appSettings.enabledGraphTypes
            if (enabledGraphs.isEmpty()) return@launch
            
            val typeToSend = enabledGraphs[currentGraphListIndex % enabledGraphs.size]
            // 送信スレッドの外（ここ）で重いCSV生成処理を終わらせる
            val unifiedGraph = GraphDataGenerator.generateUnifiedGraph(stats, typeToSend)
            val dict = mapOf(KEY_GRAPH_DATA to PebbleDictionaryItem.Text(unifiedGraph))
            
            // グラフ送信はデータ量が多いが、ボタン操作に伴うものは確実に送る
            commandQueue.trySend(PebbleMessageRequest("GRAPH", dict))
        }
    }

    override fun sendTouchConfig(enabled: Boolean) {
        val dict = mapOf(
            KEY_TOUCH_ENABLE to PebbleDictionaryItem.Int32(if (enabled) 1 else 0)
        )
        // 設定は重要なのでコマンドキュー経由で確実に送る。
        // 未接続時のパケットロストを避けるためリトライを増やす。
        commandQueue.trySend(PebbleMessageRequest("TOUCH_CONFIG", dict, retryCount = 5))
    }

    override fun sendNotification(type: Int) {
        val cmdId = if (type == 0) 10 else 11
        Log.i("PebbleMessenger", "sendNotification called: type=$type (CMD=$cmdId)")
        val dict = mapOf(
            KEY_CMD to PebbleDictionaryItem.Int32(cmdId)
        )
        // 通知は重要なので、リトライを 3 回に設定して確実に届ける
        commandQueue.trySend(PebbleMessageRequest("NOTIFICATION", dict, retryCount = 3))
    }

    override fun launchWatchApp() {
        Log.i("PebbleMessenger", "launchWatchApp requested")
        scope.launch {
            val targets = PebbleCommandService.lastConnectedWatch?.let { listOf(it) } ?: emptyList()
            try { getSender().startAppOnTheWatch(WATCHAPP_UUID, targets) } catch (e: Exception) {}
        }
    }

    private fun mapToPebbleState(status: RunStatus): Int {
        return when (status) {
            RunStatus.PREPARING -> PEBBLE_STATE_GPS_SEARCHING
            RunStatus.READY -> PEBBLE_STATE_READY
            RunStatus.ACTIVE -> PEBBLE_STATE_RUNNING
            RunStatus.PAUSED -> PEBBLE_STATE_PAUSED
            RunStatus.FINISHED -> PEBBLE_STATE_FINISHED
            RunStatus.RESULT -> PEBBLE_STATE_RESULT
            else -> PEBBLE_STATE_IDLE
        }
    }

    private data class PebbleMessageRequest(
        val tag: String,
        val dictionary: Map<UInt, PebbleDictionaryItem>,
        val retryCount: Int = 0
    )
}
