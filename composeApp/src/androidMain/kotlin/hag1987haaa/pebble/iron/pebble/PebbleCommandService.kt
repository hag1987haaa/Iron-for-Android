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
import hag1987haaa.pebble.iron.domain.model.AppEventID
import hag1987haaa.pebble.iron.domain.settings.LongPressMode
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import java.util.UUID

class PebbleCommandService : BasePebbleListenerService() {

    companion object {
        var lastConnectedWatch: WatchIdentifier? = null
        private var lastCommandTime = 0L
        private var lastCommandVal = -1
        private const val DEBOUNCE_MS = 200L

        private const val KEY_CMD = 10000u
        private const val KEY_HR = 10007u
        private const val KEY_MEDIA_CMD = 10008u
        private const val KEY_STEPS = 10010u
        private const val KEY_ACTIVITY_TYPE = 10012u
        private const val KEY_MID_ID = 10015u
        private const val KEY_LOWER_ID = 10016u
        private const val KEY_EVENT = 10018u
        private const val KEY_MAP_STATE = 10022u
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
        // --- 画面オフ時のレスポンス改善対策 ---
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Iron:CommandWakeLock")
        wakeLock.acquire(3000) 
        
        Log.d("PebbleCommand", "--- MESSAGE RECEIVED --- From: $watch")
        lastConnectedWatch = watch

        val engine = KmpDependencies.trackerEngine

        // 1. 同期項目の処理 (Activity Type, HR, Steps, Mid/Lower ID, Map State)
        
        // Activity Type (10012)
        data[KEY_ACTIVITY_TYPE]?.let { parsePebbleItemToInt(it) }?.let { typeIdx ->
            try {
                val types = ActivityType.entries
                if (typeIdx in types.indices) {
                    val newType = types[typeIdx]
                    Log.i("PebbleCommand", "Changing Activity Type to: $newType")
                    engine.setActivityType(newType)
                }
            } catch (e: Exception) {
                Log.e("PebbleCommand", "Failed to change Activity Type", e)
            }
        }

        // Heart Rate (10007 or 100)
        (data[KEY_HR] ?: data[100u])?.let { parsePebbleItemToInt(it) }?.let { hr ->
            Log.i("PebbleCommand", "Data: Heart Rate ($hr)")
            engine.addHeartRate(hr, source = "PEBBLE")
        }

        // Steps (10010)
        data[KEY_STEPS]?.let { parsePebbleItemToInt(it) }?.let { steps ->
            Log.i("PebbleCommand", "Data: Steps ($steps)")
            engine.updateSteps(steps)
        }

        // Mid ID (10015)
        data[KEY_MID_ID]?.let { parsePebbleItemToInt(it) }?.let { midId ->
            Log.i("PebbleCommand", "Sync: Mid ID ($midId)")
            engine.setCurrentMidId(midId)
        }

        // Lower ID (10016)
        data[KEY_LOWER_ID]?.let { parsePebbleItemToInt(it) }?.let { lowerId ->
            Log.i("PebbleCommand", "Sync: Lower ID ($lowerId)")
            engine.setCurrentLowerId(lowerId)
        }

        // Map State (10022)
        data[KEY_MAP_STATE]?.let { parsePebbleItemToInt(it) }?.let { mapState ->
            val isActive = mapState == 1
            Log.i("PebbleCommand", "Sync: Map State ($isActive)")
            engine.setMapState(isActive)
        }

        // 2. イベント・コマンドの処理
        var handledByEvent = false
        
        // KEY_EVENT (10018) を最優先で評価
        val eventId = data[KEY_EVENT]?.let { parsePebbleItemToInt(it) }?.let { AppEventID.fromId(it) }
        if (eventId != null && eventId != AppEventID.EVENT_NONE) {
            Log.i("PebbleCommand", "Handling AppEvent: $eventId")
            handleAppEvent(eventId)
            handledByEvent = true
        }

        // KEY_EVENT が処理されなかった場合のみ、従来のキーを評価する (後方互換)
        if (!handledByEvent) {
            // メディアコマンド (10008)
            data[KEY_MEDIA_CMD]?.let { parsePebbleItemToInt(it) }?.let { mediaCmd ->
                if (KmpDependencies.appSettings.isMusicControlEnabled) {
                    Log.i("PebbleCommand", "Media Command Received: $mediaCmd")
                    sendMediaKey(mediaCmd)
                }
            }

            // ボタンコマンド (10000 or 0)
            (data[KEY_CMD] ?: data[0u])?.let { parsePebbleItemToInt(it) }?.let { cmd ->
                val currentTime = System.currentTimeMillis()
                if (cmd == lastCommandVal && ((currentTime - lastCommandTime) < DEBOUNCE_MS)) {
                    Log.w("PebbleCommand", "Ignoring repeated command: $cmd (debounce)")
                } else {
                    lastCommandTime = currentTime
                    lastCommandVal = cmd
                    Log.i("PebbleCommand", "Execute Legacy Command: $cmd")
                    handleLegacyCommand(cmd)
                }
            }
        }

        return ReceiveResult.Ack
    }

    private fun handleAppEvent(event: AppEventID) {
        val engine = KmpDependencies.trackerEngine
        val status = engine.statistics.value.status
        
        when (event) {
            AppEventID.EVENT_BUTTON_UP_CLICK -> {
                if (status == RunStatus.FINISHED) handleLegacyCommand(7) // SAVE
                else handleLegacyCommand(1) // START/PAUSE/RESUME
            }
            AppEventID.EVENT_BUTTON_SELECT_CLICK -> {
                if (status == RunStatus.RESULT) handleLegacyCommand(9) // RESET FROM RESULT
                else handleLegacyCommand(2) // ROTATE MID / FINISH
            }
            AppEventID.EVENT_BUTTON_DOWN_CLICK -> {
                if (status == RunStatus.FINISHED) handleLegacyCommand(8) // DISCARD
                else handleLegacyCommand(6) // ROTATE GRAPH
            }
            AppEventID.EVENT_BUTTON_UP_LONG -> handleLegacyCommand(50)
            AppEventID.EVENT_BUTTON_SELECT_LONG -> handleLegacyCommand(51)
            AppEventID.EVENT_BUTTON_DOWN_LONG -> handleLegacyCommand(52)
            
            AppEventID.EVENT_TOUCH_DOUBLE_TAP -> sendMediaKey(1) // Play/Pause
            AppEventID.EVENT_TOUCH_SWIPE_LEFT -> sendMediaKey(2)  // Next
            AppEventID.EVENT_TOUCH_SWIPE_RIGHT -> sendMediaKey(3) // Prev
            AppEventID.EVENT_TOUCH_SWIPE_UP -> sendMediaKey(4)    // Vol Up
            AppEventID.EVENT_TOUCH_SWIPE_DOWN -> sendMediaKey(5)  // Vol Down
            else -> {}
        }
    }

    private fun handleLegacyCommand(cmd: Int) {
        val engine = KmpDependencies.trackerEngine
        val currentStatus = engine.statistics.value.status

        when (cmd) {
            1 -> { // UP ボタン
                when (currentStatus) {
                    RunStatus.IDLE -> sendCommandToService("PREPARE")
                    RunStatus.PREPARING, RunStatus.READY -> sendCommandToService("START")
                    RunStatus.ACTIVE -> sendCommandToService("PAUSE")
                    RunStatus.PAUSED -> sendCommandToService("RESUME")
                    else -> Log.w("PebbleCommand", "UP ignored in $currentStatus")
                }
            }
            2 -> { // SELECT ボタン
                if (currentStatus == RunStatus.PAUSED) {
                    Log.i("PebbleCommand", "FINISH command received via SELECT (Cmd 2)")
                    sendCommandToService("FINISH")
                } else if (currentStatus == RunStatus.ACTIVE) {
                    Log.i("PebbleCommand", "Rotate Mid Data via SELECT (Cmd 2)")
                    engine.rotateMidData()
                }
            }
            0 -> { // SELECT ボタン (待機中): 設定モード
                Log.i("PebbleCommand", "Settings mode requested (Cmd 0)")
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
                handleLongPress(cmd)
            }
        }
    }

    private fun handleLongPress(cmd: Int) {
        val settings = KmpDependencies.appSettings
        if (!settings.isLongPressEnabled) return

        val mode = when (cmd) {
            50 -> settings.upLongPressMode
            51 -> settings.selectLongPressMode
            52 -> settings.downLongPressMode
            else -> LongPressMode.MUSIC
        }

        when (mode) {
            LongPressMode.MUSIC -> {
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
                Log.i("PebbleCommand", "Long Press Assistant Triggered")
                try {
                    val pm = getSystemService(POWER_SERVICE) as PowerManager
                    val wakeLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "Iron:AssistantWakeup"
                    )
                    wakeLock.acquire(3000)

                    val trampolineIntent = Intent(this, AssistantTrampolineActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    startActivity(trampolineIntent)
                } catch (e: Exception) {
                    Log.e("PebbleCommand", "Failed to launch assistant", e)
                }
            }
            LongPressMode.INTENT -> {
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
                            setPackage(null) 
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
                // レスポンス改善：OS内のIntent配信優先度を「フォアグラウンド（最優先）」に設定
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            }
            // 終了・リセット系のコマンドは、フォアグラウンドサービスとして起動する必要はない
            if (action == "STOP" || action == "RESET" || action == "SAVE" || action == "SAVE_TO_RESULT") {
                startService(intent)
            } else {
                startForegroundService(intent)
            }
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
