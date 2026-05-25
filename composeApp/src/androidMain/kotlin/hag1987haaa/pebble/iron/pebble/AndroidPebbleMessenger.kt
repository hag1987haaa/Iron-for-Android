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
import java.util.UUID

class AndroidPebbleMessenger(private val context: Context) : PebbleMessenger {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // コマンドキュー (STATE変更, SYNC, グラフ切り替え時)
    private val commandQueue = Channel<PebbleMessageRequest>(Channel.UNLIMITED)
    
    // 統計データ（最新1件のみ保持。volatileでスレッドセーフにアクセス）
    @Volatile
    private var nextStatsRequest: PebbleMessageRequest? = null

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
        private const val KEY_TYPE = 10012u // ★ ワークアウト種別キー
        
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
                    } else {
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
        val targets = PebbleCommandService.lastConnectedWatch?.let { listOf(it) } ?: emptyList()
        
        if (request.retryCount > 0) {
            for (i in 0 until request.retryCount) {
                if (sendAttempt(request.tag, request.dictionary, targets)) return
                delay((i + 1) * 800L)
            }
        } else {
            sendAttempt(request.tag, request.dictionary, targets)
        }
    }

    private suspend fun sendAttempt(tag: String, dict: Map<UInt, PebbleDictionaryItem>, targets: List<WatchIdentifier>): Boolean {
        return try {
            withTimeout(SEND_TIMEOUT_MS) {
                val results = getSender().sendDataToPebble(WATCHAPP_UUID, dict, targets)
                val success = results?.all { it.value == TransmissionResult.Success } ?: false
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
        // IDLE状態でも種別変更などの重要な同期があるため、最低限の情報を送るように緩和
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

    override fun sendGraphData(stats: RunStatistics) {
        scope.launch {
            val typeToSend = currentGraphType
            // 送信スレッドの外（ここ）で重いCSV生成処理を終わらせる
            val unifiedGraph = GraphDataGenerator.generateUnifiedGraph(stats, typeToSend)
            val dict = mapOf(KEY_GRAPH_DATA to PebbleDictionaryItem.Text(unifiedGraph))
            
            // グラフ送信はデータ量が多いが、ボタン操作に伴うものは確実に送る
            commandQueue.trySend(PebbleMessageRequest("GRAPH", dict))
        }
    }

    private var currentGraphType = 0 
    override fun rotateGraphType(stats: RunStatistics) {
        currentGraphType = (currentGraphType + 1) % 5
        sendGraphData(stats)
    }

    override fun sendTouchConfig(enabled: Boolean) {
        val dict = mapOf(
            KEY_TOUCH_ENABLE to PebbleDictionaryItem.Int32(if (enabled) 1 else 0)
        )
        // 設定は重要なのでコマンドキュー経由で確実に送る
        commandQueue.trySend(PebbleMessageRequest("TOUCH_CONFIG", dict, retryCount = 3))
    }

    override fun launchWatchApp() {
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
