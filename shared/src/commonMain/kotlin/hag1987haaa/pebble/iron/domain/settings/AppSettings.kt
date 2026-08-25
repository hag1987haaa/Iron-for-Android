package hag1987haaa.pebble.iron.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import hag1987haaa.pebble.iron.domain.model.ActivityType

class AppSettings {
    var isMusicControlEnabled: Boolean = false
    var isTouchControlEnabled: Boolean = false
    
    // ボタン長押しアクション設定
    var isLongPressEnabled: Boolean = false
    var upLongPressMode: LongPressMode = LongPressMode.MUSIC
    var selectLongPressMode: LongPressMode = LongPressMode.MUSIC
    var downLongPressMode: LongPressMode = LongPressMode.MUSIC

    // 自動化・外部アプリ連携設定 (INTENTモード時に使用)
    var isAutomationEnabled: Boolean = false
    var isCommand50Enabled: Boolean = true
    var isCommand51Enabled: Boolean = true
    var isCommand52Enabled: Boolean = true
    
    private val _isPrivacyMapModeEnabled = MutableStateFlow(value = false)
    val isPrivacyMapModeEnabledFlow: StateFlow<Boolean> = _isPrivacyMapModeEnabled.asStateFlow()
    var isPrivacyMapModeEnabled: Boolean
        get() = _isPrivacyMapModeEnabled.value
        set(value) { _isPrivacyMapModeEnabled.value = value }

    /**
     * 中段表示（Mid Data）の設定
     * 0: Pace, 1: Distance, 2: Steps, 3: Altitude, 4: HR, 5: Calories, 7: Avg Pace, 8: Speed, 9: Clock, 10: Gain, 11: Cadence, 99: Cockpit
     */
    var enabledMidTypes: List<Int> = listOf(0, 4, 1, 5, 10)
    var isMetric: Boolean = true
    var userWeightKg: Float = 70.0f
    var hasAskedHealthConnectOnboarding: Boolean = false
    var appVersion: String = "2.0.9"

    /**
     * ウォッチ側に表示するグラフの順序と有効無効の設定
     * 0: Pace/Speed (Dist based)
     * 1: Distance
     * 2: Steps
     * 3: Altitude
     * 4: Heart Rate
     * 5: Calories
     */
    var enabledGraphTypes: List<Int> = listOf(0, 1, 2, 3, 4, 5)

    /**
     * 自動通知設定 (0.0 は無効, 1.0 は 1km または 1mi)
     */
    var notificationDistanceStep: Float = 1.0f 
    var notificationTimeSeconds: Int = 0
    var isAutoLaunchOnDistanceNotificationEnabled: Boolean = false // 距離通知時にIronを強制起動
    var isAutoLaunchOnTimeNotificationEnabled: Boolean = false // 時間通知時にIronを強制起動

    // 自動エクスポート設定
    var isAutoExportTcxEnabled: Boolean = false
    var isAutoExportGpxEnabled: Boolean = false
    var autoExportTcxUri: String? = null
    var autoExportGpxUri: String? = null

    // 心拍サンプリング間隔 (0: システムデフォルト, 1, 10, 30, etc.)
    var hrSamplingInterval: Int = 0

    // 最後に使用したアクティビティ種別
    var lastActivityType: String = ActivityType.RUNNING.name

    // 最後に使用した表示インデックス
    var lastGraphTypeId: Int = -1

    // 最後に開いていたタブの状態
    var lastMainTab: Int = 0
    var lastSettingsTabName: String = "PHONE"
    var lastHistoryViewModeName: String = "SCROLL"

    // BLE センサー設定
    var bleHeartRateDeviceAddress: String? = null // 下位互換用（現在選択中または最後に繋がったもの）
    var bleHeartRateDeviceName: String? = null
    var registeredBleHrDevices: List<String> = emptyList() // "address|name" のリスト
    var preferredBleHrAddress: String? = null // 明示的に固定されたデバイスのアドレス
    var isBleHeartRateEnabled: Boolean = false
    var preferBleHeartRate: Boolean = true // BLEセンサーがある場合はPebbleより優先する

    // プラットフォーム固有の保存処理用コールバック
    var onSettingsChanged: (() -> Unit)? = null

    fun save() {
        onSettingsChanged?.invoke()
    }
}
