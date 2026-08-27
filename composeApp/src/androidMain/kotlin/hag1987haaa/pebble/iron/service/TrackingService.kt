package hag1987haaa.pebble.iron.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import hag1987haaa.pebble.iron.AndroidDependencies
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.MainActivity
import hag1987haaa.pebble.iron.R
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.domain.tracker.RunStatus
import hag1987haaa.pebble.iron.util.AutoExporter
import hag1987haaa.pebble.iron.util.HealthUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class TrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifContent: String? = null

    override fun onCreate() {
        super.onCreate()
        AndroidDependencies.initialize(this)

        // 長時間・画面オフ時でも計測を維持するため WakeLock を取得
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Iron:TrackingWakeLock")
        wakeLock?.acquire()
        Log.d("TrackingService", "WakeLock acquired")

        // ステータスと統計の変化を監視して通知を自動更新
        serviceScope.launch {
            // 状態の変化を監視
            launch {
                RunState.status.collect { status ->
                    updateNotificationByCurrentState()
                    
                    // 自動化アプリ向けインテントの送出
                    if (KmpDependencies.appSettings.isAutomationEnabled) {
                        val intent = Intent("hag1987haaa.pebble.iron.ACTION_STATE_CHANGED").apply {
                            putExtra("state_name", status.name)
                            putExtra("state_code", status.ordinal)
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                        }
                        sendBroadcast(intent)
                    }
                }
            }

            // 統計データ（時間など）の変化を監視して、通知の内容を更新
            launch {
                RunState.currentStats.collect { stats ->
                    // ACTIVE中、またはPAUSED中なら、経過時間を含めて通知を更新
                    val status = RunState.status.value
                    if (status == RunStatus.ACTIVE || status == RunStatus.PAUSED) {
                        updateNotificationByCurrentState(stats.formattedTime)
                    }
                }
            }
        }
    }

    private fun updateNotificationByCurrentState(timeStr: String? = null) {
        val status = RunState.status.value
        if (status == RunStatus.IDLE || status == RunStatus.RESULT) return

        val statusName = when (status) {
            RunStatus.PREPARING -> getString(R.string.status_preparing)
            RunStatus.READY -> getString(R.string.status_ready)
            RunStatus.ACTIVE -> getString(R.string.status_active)
            RunStatus.PAUSED -> getString(R.string.status_paused)
            else -> getString(R.string.app_name)
        }

        val contentText = if (timeStr != null) {
            getString(R.string.notif_content_current_state, statusName) + " [$timeStr]"
        } else {
            getString(R.string.notif_content_current_state, statusName)
        }

        // 表示内容が変わっていない場合は更新をスキップ（秒間数回の冗長な更新を防ぐ）
        if (contentText == lastNotifContent) return
        lastNotifContent = contentText
        
        updateNotification(contentText, forceOngoing = (timeStr == null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d("TrackingService", "onStartCommand: action=$action, flags=$flags")

        // OSによるメモリ不足からの再起動 (intent == null) 時の対策
        if (action == null) {
            val status = RunState.status.value
            if (status != RunStatus.IDLE && status != RunStatus.FINISHED && status != RunStatus.RESULT) {
                // 計測中なら通知を再構築してフォアグラウンドを維持
                val statusName = when (status) {
                    RunStatus.PREPARING -> getString(R.string.status_preparing)
                    RunStatus.READY -> getString(R.string.status_ready)
                    RunStatus.ACTIVE -> getString(R.string.status_active)
                    RunStatus.PAUSED -> getString(R.string.status_paused)
                    else -> getString(R.string.app_name)
                }
                updateNotification(getString(R.string.notif_content_current_state, statusName), forceOngoing = true)
            } else {
                // アイドル状態ならサービスを終了
                stopSelf()
            }
            return START_STICKY
        }

        // バイブレーションなどは必要なアクションの時のみ実行
        if (action != "SAVE" && action != "SAVE_TO_RESULT" && action != "STOP" && action != "RESET") {
            vibrateDevice()
        }

        val engine = KmpDependencies.trackerEngine
        val currentStatus = RunState.status.value

        when (action) {
            "PREPARE" -> {
                if (currentStatus == RunStatus.IDLE || currentStatus == RunStatus.RESULT) engine.prepare()
            }
            "START" -> {
                if (currentStatus == RunStatus.IDLE || currentStatus == RunStatus.RESULT) {
                    engine.prepare()
                    engine.start()
                } else if (currentStatus == RunStatus.READY || currentStatus == RunStatus.PREPARING) {
                    engine.start()
                }
            }
            "PAUSE" -> {
                if (currentStatus == RunStatus.ACTIVE) engine.pause()
            }
            "RESUME" -> {
                if (currentStatus == RunStatus.PAUSED) engine.resume()
            }
            "FINISH" -> {
                if (currentStatus == RunStatus.ACTIVE || currentStatus == RunStatus.PAUSED) engine.finish()
            }
            "SAVE_TO_RESULT" -> {
                saveWorkoutToResult()
            }
            "SAVE" -> {
                saveWorkoutAndStop()
            }
            "STOP" -> {
                engine.discard()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            "RESET" -> {
                Log.i("TrackingService", "Action RESET: Resetting engine and stopping service")
                engine.resetToIdle()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun vibrateDevice() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(100)
                }
            }
        } catch (e: Exception) {
            Log.e("TrackingService", "Vibration failed", e)
        }
    }

    private fun saveWorkoutToResult() {
        // 重複保存の防止
        if (RunState.status.value == RunStatus.RESULT) {
            Log.w("TrackingService", "saveWorkoutToResult: Already in RESULT state, skipping.")
            return
        }

        serviceScope.launch {
            try {
                val stats = RunState.currentStats.value
                val settings = KmpDependencies.appSettings
                
                // 保存直前に最新の設定（体重・アクティビティ種別）でカロリーを再計算
                val calories = HealthUtils.calculateCalories(
                    type = stats.activityType,
                    weightKg = settings.userWeightKg,
                    durationSeconds = stats.totalSeconds,
                    distanceMeters = stats.totalDistanceMeters,
                    elevationGainMeters = stats.totalElevationGain,
                    avgHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null
                )

                val run = RunActivity(
                    startTime = stats.startTime ?: Clock.System.now(),
                    name = stats.name,
                    type = stats.activityType,
                    endTime = Clock.System.now(),
                    distanceMeters = stats.totalDistanceMeters,
                    durationSeconds = stats.totalSeconds,
                    calories = calories,
                    steps = stats.steps,
                    avgHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null,
                    maxHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.maxOrNull()?.toDouble() else null,
                    elevationGain = stats.totalElevationGain,
                    route = stats.route
                )

                // Health Connect 同期を試行
                var hcId: String? = null
                try {
                    hcId = AndroidDependencies.healthConnectManager.writeRunActivity(run)
                } catch (e: Exception) {
                    Log.e("TrackingService", "Health Connect sync failed", e)
                }
                
                // データベース保存
                val savedId = KmpDependencies.runRepository.saveRun(run.copy(healthConnectId = hcId))
                
                // データベースから「保存完了した完全なデータ（route/ID含む）」を読み直して自動エクスポートを実行
                val savedRun = KmpDependencies.runRepository.getRunDetails(savedId)
                if (savedRun != null) {
                    AutoExporter.execute(applicationContext, savedRun)
                }

                // エンジンをリザルト表示モードへ移行
                KmpDependencies.trackerEngine.saveToResult()
                
                Log.i("TrackingService", "Workout saved to RESULT state.")
                
                // ワークアウト終了に伴い、通知を消去してサービスを停止
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            } catch (e: Exception) {
                Log.e("TrackingService", "Failed to save workout", e)
            }
        }
    }

    private fun saveWorkoutAndStop() {
        // 重複保存の防止
        if (RunState.status.value == RunStatus.IDLE) {
            Log.w("TrackingService", "saveWorkoutAndStop: Already in IDLE state, skipping.")
            return
        }

        serviceScope.launch {
            try {
                val stats = RunState.currentStats.value
                val settings = KmpDependencies.appSettings
                
                // 保存直前に最新の設定（体重・アクティビティ種別）でカロリーを再計算
                val calories = HealthUtils.calculateCalories(
                    type = stats.activityType,
                    weightKg = settings.userWeightKg,
                    durationSeconds = stats.totalSeconds,
                    distanceMeters = stats.totalDistanceMeters,
                    elevationGainMeters = stats.totalElevationGain,
                    avgHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null
                )

                val run = RunActivity(
                    startTime = stats.startTime ?: Clock.System.now(),
                    name = stats.name,
                    type = stats.activityType,
                    endTime = Clock.System.now(),
                    distanceMeters = stats.totalDistanceMeters,
                    durationSeconds = stats.totalSeconds,
                    calories = calories,
                    steps = stats.steps,
                    avgHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null,
                    maxHeartRate = if (stats.heartRates.isNotEmpty()) stats.heartRates.maxOrNull()?.toDouble() else null,
                    elevationGain = stats.totalElevationGain,
                    route = stats.route
                )
                
                // Health Connect 同期を試行
                var hcId: String? = null
                try {
                    hcId = AndroidDependencies.healthConnectManager.writeRunActivity(run)
                } catch (e: Exception) {
                    Log.e("TrackingService", "Health Connect sync failed", e)
                }

                // データベース保存
                val savedId = KmpDependencies.runRepository.saveRun(run.copy(healthConnectId = hcId))
                
                // 自動エクスポートを実行
                val savedRun = KmpDependencies.runRepository.getRunDetails(savedId)
                if (savedRun != null) {
                    AutoExporter.execute(applicationContext, savedRun)
                }

                // トラッキングリセット
                KmpDependencies.trackerEngine.resetToIdle()
                
                Log.i("TrackingService", "Workout saved and service stopping.")
            } catch (e: Exception) {
                Log.e("TrackingService", "Failed to save workout", e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateNotification(content: String, forceOngoing: Boolean = false) {
        val channelId = "tracking_channel"
        val manager = getSystemService(NotificationManager::class.java)
        // 重要度を少し上げて、OSに無視されないようにする
        manager?.createNotificationChannel(NotificationChannel(channelId, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_DEFAULT))

        val status = RunState.status.value
        // アイドルや終了状態なら、通知を更新しようとせず、速やかにフォアグラウンドから抜ける準備をする
        if (status == RunStatus.IDLE || status == RunStatus.RESULT) {
            return
        }

        val title = when(status) {
            RunStatus.ACTIVE -> getString(R.string.notif_status_active)
            RunStatus.PAUSED -> getString(R.string.notif_status_paused)
            RunStatus.PREPARING -> getString(R.string.notif_status_preparing)
            RunStatus.READY -> getString(R.string.notif_status_ready)
            else -> getString(R.string.app_name)
        }

        val isOngoing = forceOngoing || (status != RunStatus.IDLE && status != RunStatus.RESULT)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true) // 重要：更新時に音やバイブを鳴らさない（Pebbleへの通知飛びを防ぐ）
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    1, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("TrackingService", "Failed to start foreground", e)
        }
    }

    override fun onDestroy() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d("TrackingService", "WakeLock released")
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
