package hag1987haaa.pebble.iron.pebble

import android.util.Log
import android.content.Intent
import android.media.AudioManager
import android.os.PowerManager
import android.view.KeyEvent
import io.rebble.pebblekit2.client.BasePebbleListenerService
import io.rebble.pebblekit2.common.model.PebbleDictionaryItem
import io.rebble.pebblekit2.common.model.WatchIdentifier
import io.rebble.pebblekit2.common.model.ReceiveResult
import hag1987haaa.pebble.iron.AndroidDependencies
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.settings.LongPressMode
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import java.util.UUID

class PebbleCommandService : BasePebbleListenerService() {

    companion object {
        var lastConnectedWatch: WatchIdentifier? = null
        private var lastCommandTime = 0L
        private var lastCommandVal = -1
        private const val DEBOUNCE_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        try {
            AndroidDependencies.initialize(applicationContext)
        } catch (e: Exception) {
            Log.e("PebbleCommand", "Init failed", e)
        }
    }

    override suspend fun onMessageReceived(
        watchappUUID: UUID,
        data: Map<UInt, PebbleDictionaryItem>,
        watch: WatchIdentifier
    ): ReceiveResult {
        Log.d("PebbleCommand", "--- MESSAGE RECEIVED --- From: $watch")
        
        lastConnectedWatch = watch

        // 1. ワークアウト種別の変更 (Activity Type Key = 10012)
        val typeIdx = data[10012u]?.let { parsePebbleItemToInt(it) }
        if (typeIdx != null) {
            try {
                val types = ActivityType.entries
                if (typeIdx in types.indices) {
                    val newType = types[typeIdx]
                    Log.i("PebbleCommand", "Changing Activity Type to: $newType (Index: $typeIdx)")
                    KmpDependencies.trackerEngine.setActivityType(newType)
                    // 変更を即座にウォッチに同期
                    KmpDependencies.trackerEngine.triggerStatisticsUpdate()
                }
            } catch (e: Exception) {
                Log.e("PebbleCommand", "Failed to change Activity Type", e)
            }
        }

        // 2. 心拍数・歩数データ処理 (デバウンス対象外 - 常時受け付ける)
        val hrItem = data[10007u] ?: data[100u]
        val hr = hrItem?.let { parsePebbleItemToInt(it) }
        if (hr != null) {
            Log.i("PebbleCommand", "Data: Heart Rate ($hr)")
            KmpDependencies.trackerEngine.addHeartRate(hr)
        }

        val stepsItem = data[10010u]
        val steps = stepsItem?.let { parsePebbleItemToInt(it) }
        if (steps != null) {
            Log.i("PebbleCommand", "Data: Steps ($steps)")
            KmpDependencies.trackerEngine.updateSteps(steps)
        }

        // 2. メディアコマンドの処理 (KEY_MEDIA_CMD = 10008 - デバウンス対象外)
        val mediaCmdItem = data[10008u]
        val mediaCmd = mediaCmdItem?.let { parsePebbleItemToInt(it) }
        if (mediaCmd != null) {
            if (KmpDependencies.appSettings.isMusicControlEnabled) {
                Log.i("PebbleCommand", "Media Command Received: $mediaCmd")
                sendMediaKey(mediaCmd)
            } else {
                Log.w("PebbleCommand", "Media Command ignored (Feature Disabled in settings)")
            }
        }

        // 3. ボタンコマンドの処理 (KEY_CMD = 10000 または 0)
        val cmdItem = data[10000u] ?: data[0u]
        val cmd = cmdItem?.let { parsePebbleItemToInt(it) }

        if (cmd != null) {
            val currentTime = System.currentTimeMillis()
            // 同じコマンドが短時間に連続した場合は無視する (長押しによる連打対策)
            if (cmd == lastCommandVal && ((currentTime - lastCommandTime) < DEBOUNCE_MS)) {
                Log.w("PebbleCommand", "Ignoring repeated command: $cmd (debounce)")
                return ReceiveResult.Ack
            }
            
            lastCommandTime = currentTime
            lastCommandVal = cmd

            Log.i("PebbleCommand", "Execute Command: $cmd")
            val engine = KmpDependencies.trackerEngine
            val currentStatus = engine.statistics.value.status

            when (cmd) {
                1 -> { // UP ボタン: アクションの進行 (開始 / 一時停止 / 再開)
                    when (currentStatus) {
                        RunStatus.IDLE -> sendCommandToService("PREPARE")
                        RunStatus.PREPARING, RunStatus.READY -> sendCommandToService("START")
                        RunStatus.ACTIVE -> sendCommandToService("PAUSE")
                        RunStatus.PAUSED -> sendCommandToService("RESUME")
                        else -> Log.w("PebbleCommand", "UP ignored in $currentStatus")
                    }
                }
                2 -> { // SELECT ボタン (一時停止中): ワークアウトの終了
                    if (currentStatus == RunStatus.PAUSED) {
                        Log.i("PebbleCommand", "FINISH command received via SELECT (Cmd 2)")
                        sendCommandToService("FINISH")
                    }
                }
                0 -> { // SELECT ボタン (待機中): 設定モード
                    Log.i("PebbleCommand", "Settings mode requested (Cmd 0)")
                    // 将来的な設定画面実装用
                }
                7 -> { // UP ボタン (状態5): ワークアウトの保存
                    if (currentStatus == RunStatus.FINISHED) {
                        Log.i("PebbleCommand", "SAVE command received (Cmd 7)")
                        sendCommandToService("SAVE_TO_RESULT")
                    }
                }
                8 -> { // DOWN ボタン (状態5): ワークアウトの破棄
                    if (currentStatus == RunStatus.FINISHED) {
                        Log.i("PebbleCommand", "DISCARD command received (Cmd 8)")
                        sendCommandToService("RESET")
                    }
                }
                9 -> { // SELECT ボタン (状態6): リザルト画面終了 -> IDLE
                    if (currentStatus == RunStatus.RESULT) {
                        Log.i("PebbleCommand", "RESET FROM RESULT received (Cmd 9)")
                        sendCommandToService("RESET")
                    }
                }
                6 -> { // DOWN ボタン: グラフ切り替え
                    engine.rotateGraphType()
                }
                5 -> { // 同期リクエスト
                    engine.triggerStatisticsUpdate()
                }
                50, 51, 52 -> {
                    // ボタン長押しアクションの処理
                    val settings = KmpDependencies.appSettings
                    if (settings.isLongPressEnabled) {
                        val mode = when (cmd) {
                            50 -> settings.upLongPressMode
                            51 -> settings.selectLongPressMode
                            52 -> settings.downLongPressMode
                            else -> LongPressMode.MUSIC
                        }

                        when (mode) {
                            LongPressMode.MUSIC -> {
                                // ① ミュージックコントロール
                                val mediaCmd = when (cmd) {
                                    50 -> 3 // Up Long -> Previous
                                    51 -> 1 // Select Long -> Play/Pause
                                    52 -> 2 // Down Long -> Next
                                    else -> -1
                                }
                                if (mediaCmd != -1) {
                                    Log.i("PebbleCommand", "Long Press Media: $mediaCmd")
                                    sendMediaKey(mediaCmd)
                                }
                            }
                            LongPressMode.ASSISTANT -> {
                                // ② アシスタントを呼び出す (Google Assistant / Gemini)
                                Log.i("PebbleCommand", "Long Press Assistant: Triggering Voice Command via Trampoline")
                                try {
                                    // 画面を強制的に点灯させてバックグラウンド制限を回避しやすくする
                                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                                    val wakeLock = pm.newWakeLock(
                                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                                        "Iron:AssistantWakeup"
                                    )
                                    wakeLock.acquire(3000) // 3秒間点灯

                                    // 直接アシスタントを呼ぶのではなく、Ironの中継画面を立ち上げる
                                    val trampolineIntent = Intent(this, AssistantTrampolineActivity::class.java).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    }
                                    startActivity(trampolineIntent)
                                } catch (e: Exception) {
                                    Log.e("PebbleCommand", "Failed to launch assistant", e)
                                }
                            }
                            LongPressMode.INTENT -> {
                                // ③ カスタムIntentの送信
                                if (settings.isAutomationEnabled) {
                                    val isEnabled = when(cmd) {
                                        50 -> settings.isCommand50Enabled
                                        51 -> settings.isCommand51Enabled
                                        52 -> settings.isCommand52Enabled
                                        else -> false
                                    }
                                    
                                    if (isEnabled) {
                                        val action = when(cmd) {
                                            50 -> "hag1987haaa.pebble.iron.ACTION_LONGPRESS_UP"
                                            51 -> "hag1987haaa.pebble.iron.ACTION_LONGPRESS_SELECT"
                                            52 -> "hag1987haaa.pebble.iron.ACTION_LONGPRESS_DOWN"
                                            else -> ""
                                        }
                                        Log.i("PebbleCommand", "Automation: Broadcasting intent $action")
                                        val intent = Intent(action).apply {
                                            setPackage(null) // システム全体に放送
                                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                                        }
                                        sendBroadcast(intent)
                                    }
                                }
                            }
                            LongPressMode.NONE -> {
                                Log.d("PebbleCommand", "Long Press ignored (Mode: NONE)")
                            }
                        }
                    }
                }
            }
            // 重要: コマンド処理後は、現在の最新状態(STATE)と統計を即座にウォッチに返信して同期させる
            engine.triggerStatisticsUpdate()
        }

        return ReceiveResult.Ack
    }

    private fun parsePebbleItemToInt(item: PebbleDictionaryItem): Int? {
        return when (item) {
            is PebbleDictionaryItem.UInt32 -> item.value.toInt()
            is PebbleDictionaryItem.Int32 -> item.value
            is PebbleDictionaryItem.UInt16 -> item.value.toInt()
            is PebbleDictionaryItem.Int16 -> item.value.toInt()
            is PebbleDictionaryItem.UInt8 -> item.value.toInt()
            is PebbleDictionaryItem.Int8 -> item.value.toInt()
            is PebbleDictionaryItem.Text -> item.value.toIntOrNull()
            else -> null
        }
    }

    private fun sendCommandToService(action: String) {
        Log.i("PebbleCommand", "Sending action to service: $action")
        try {
            val intent = Intent(this, hag1987haaa.pebble.iron.service.TrackingService::class.java).apply {
                this.action = action
            }
            startForegroundService(intent)
        } catch (e: Exception) {
            Log.e("PebbleCommand", "Failed to start service for action $action", e)
        }
    }

    private fun sendMediaKey(cmd: Int) {
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        
        // 音量操作 (コマンド 4, 5) の場合は adjustStreamVolume を使用
        if (cmd == 4) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            return
        } else if (cmd == 5) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            return
        }

        val keyCode = when (cmd) {
            1 -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            2 -> KeyEvent.KEYCODE_MEDIA_NEXT
            3 -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return
        }

        Log.d("PebbleCommand", "Dispatching Media Key: $keyCode")
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    override fun onAppOpened(watchappUUID: UUID, watch: WatchIdentifier) {
        Log.d("PebbleCommand", "App opened detected: $watch. Triggering forced full sync.")
        lastConnectedWatch = watch
        // ウォッチアプリが開かれたのを検知したら、強制的に現在の全ステータスを同期させる
        KmpDependencies.trackerEngine.triggerStatisticsUpdate()
    }

    override fun onAppClosed(watchappUUID: UUID, watch: WatchIdentifier) {
        Log.d("PebbleCommand", "App closed: $watch")
    }
}
