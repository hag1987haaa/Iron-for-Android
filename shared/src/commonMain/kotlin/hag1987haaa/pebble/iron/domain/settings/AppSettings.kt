package hag1987haaa.pebble.iron.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import hag1987haaa.pebble.iron.domain.model.ActivityType

class AppSettings {
    var isMusicControlEnabled: Boolean = false
    var isTouchControlEnabled: Boolean = false
    private val _isMapSwipePanEnabled = MutableStateFlow(value = true)
    val isMapSwipePanEnabledFlow: StateFlow<Boolean> = _isMapSwipePanEnabled.asStateFlow()
    var isMapSwipePanEnabled: Boolean
        get() = _isMapSwipePanEnabled.value
        set(value) { _isMapSwipePanEnabled.value = value }

    // 距離通知（オートラップ）用
    private val _isDistNotificationVibrationEnabled = MutableStateFlow(value = true)
    val isDistNotificationVibrationEnabledFlow: StateFlow<Boolean> = _isDistNotificationVibrationEnabled.asStateFlow()
    var isDistNotificationVibrationEnabled: Boolean
        get() = _isDistNotificationVibrationEnabled.value
        set(value) {
            _isDistNotificationVibrationEnabled.value = value
            save()
        }

    private val _autoShowMapAfterDistNotificationSeconds = MutableStateFlow(value = 0)
    val autoShowMapAfterDistNotificationSecondsFlow: StateFlow<Int> = _autoShowMapAfterDistNotificationSeconds.asStateFlow()
    var autoShowMapAfterDistNotificationSeconds: Int
        get() = _autoShowMapAfterDistNotificationSeconds.value
        set(value) {
            _autoShowMapAfterDistNotificationSeconds.value = value
            save()
        }

    // 時間通知（インターバル）用
    private val _isTimeNotificationVibrationEnabled = MutableStateFlow(value = true)
    val isTimeNotificationVibrationEnabledFlow: StateFlow<Boolean> = _isTimeNotificationVibrationEnabled.asStateFlow()
    var isTimeNotificationVibrationEnabled: Boolean
        get() = _isTimeNotificationVibrationEnabled.value
        set(value) {
            _isTimeNotificationVibrationEnabled.value = value
            save()
        }

    private val _autoShowMapAfterTimeNotificationSeconds = MutableStateFlow(value = 0)
    val autoShowMapAfterTimeNotificationSecondsFlow: StateFlow<Int> = _autoShowMapAfterTimeNotificationSeconds.asStateFlow()
    var autoShowMapAfterTimeNotificationSeconds: Int
        get() = _autoShowMapAfterTimeNotificationSeconds.value
        set(value) {
            _autoShowMapAfterTimeNotificationSeconds.value = value
            save()
        }

    // 下位互換用
    var isNotificationVibrationEnabled: Boolean
        get() = _isDistNotificationVibrationEnabled.value
        set(value) {
            _isDistNotificationVibrationEnabled.value = value
            _isTimeNotificationVibrationEnabled.value = value
            save()
        }

    var autoShowMapAfterNotificationSeconds: Int
        get() = _autoShowMapAfterDistNotificationSeconds.value
        set(value) {
            _autoShowMapAfterDistNotificationSeconds.value = value
            _autoShowMapAfterTimeNotificationSeconds.value = value
            save()
        }
    
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

    private val _isAutoShowMapOnReadyEnabled = MutableStateFlow(value = false)
    val isAutoShowMapOnReadyEnabledFlow: StateFlow<Boolean> = _isAutoShowMapOnReadyEnabled.asStateFlow()
    var isAutoShowMapOnReadyEnabled: Boolean
        get() = _isAutoShowMapOnReadyEnabled.value
        set(value) { _isAutoShowMapOnReadyEnabled.value = value }

    /**
     * アクティビティ種別ごとのマップ拡大率（ズームレベル 11..18）
     * 描画・通信速度優先のため、全ワークアウトでウォーキングと同じく Zoom 16 をデフォルトとし、サイクリングのみ 15 とします。
     */
    private val _activityMapZooms = MutableStateFlow<Map<String, Int>>(
        mapOf(
            ActivityType.RUNNING.name to 16,
            ActivityType.WALKING.name to 16,
            ActivityType.CYCLING.name to 16,
            ActivityType.HIKING.name to 16,
            ActivityType.OTHER.name to 16
        )
    )
    val activityMapZoomsFlow: StateFlow<Map<String, Int>> = _activityMapZooms.asStateFlow()
    var activityMapZooms: Map<String, Int>
        get() = _activityMapZooms.value
        set(value) {
            _activityMapZooms.value = value
            save()
        }

    fun getMapZoomForActivity(type: ActivityType): Int {
        return activityMapZooms[type.name] ?: 16
    }

    fun setMapZoomForActivity(type: ActivityType, zoom: Int) {
        val clamped = zoom.coerceIn(11, 18)
        val current = activityMapZooms[type.name] ?: 16
        if (current != clamped) {
            val newMap = activityMapZooms.toMutableMap()
            newMap[type.name] = clamped
            activityMapZooms = newMap
        }
    }

    private val _mapRouteColor = MutableStateFlow(MapColorPreset.RED)
    val mapRouteColorFlow: StateFlow<MapColorPreset> = _mapRouteColor.asStateFlow()
    var mapRouteColor: MapColorPreset
        get() = _mapRouteColor.value
        set(value) {
            _mapRouteColor.value = value
            save()
        }

    private val _mapPlannedColor = MutableStateFlow(MapColorPreset.YELLOW)
    val mapPlannedColorFlow: StateFlow<MapColorPreset> = _mapPlannedColor.asStateFlow()
    var mapPlannedColor: MapColorPreset
        get() = _mapPlannedColor.value
        set(value) {
            _mapPlannedColor.value = value
            save()
        }

    private val _mapLocationColor = MutableStateFlow(MapColorPreset.GREEN)
    val mapLocationColorFlow: StateFlow<MapColorPreset> = _mapLocationColor.asStateFlow()
    var mapLocationColor: MapColorPreset
        get() = _mapLocationColor.value
        set(value) {
            _mapLocationColor.value = value
            save()
        }

    private val _mapAutoCloseTimeoutSeconds = MutableStateFlow(value = 10)
    val mapAutoCloseTimeoutSecondsFlow: StateFlow<Int> = _mapAutoCloseTimeoutSeconds.asStateFlow()
    var mapAutoCloseTimeoutSeconds: Int
        get() = _mapAutoCloseTimeoutSeconds.value
        set(value) { _mapAutoCloseTimeoutSeconds.value = value }

    /**
     * 中段表示（Mid Data）の設定
     * 0: Pace, 1: Distance, 2: Steps, 3: Altitude, 4: HR, 5: Calories, 7: Avg Pace, 8: Speed, 9: Clock, 10: Gain, 11: Cadence, 99: Cockpit
     */
    var enabledMidTypes: List<Int> = listOf(0, 4, 1, 5, 10)
    var enabledLowerTypes: List<Int> = listOf(100, 101, 102, 103, 104, 105)
    var lastMidId: Int = -1
    var lastLowerId: Int = -1
    var isMetric: Boolean = true
    var userWeightKg: Float = 70.0f
    var hasAskedHealthConnectOnboarding: Boolean = false
    var appVersion: String = "2.1.3"
    
    private val _pebblePlatform = MutableStateFlow<String?>(value = null)
    val pebblePlatformFlow: StateFlow<String?> = _pebblePlatform.asStateFlow()
    var pebblePlatform: String?
        get() = _pebblePlatform.value
        set(value) { _pebblePlatform.value = value }

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

    // 保存済みGPXコース一覧（最大20個）
    private val _savedGpxCourses = MutableStateFlow<List<hag1987haaa.pebble.iron.util.GpxCourse>>(emptyList())
    val savedGpxCoursesFlow: StateFlow<List<hag1987haaa.pebble.iron.util.GpxCourse>> = _savedGpxCourses.asStateFlow()
    var savedGpxCourses: List<hag1987haaa.pebble.iron.util.GpxCourse>
        get() = _savedGpxCourses.value
        set(value) {
            _savedGpxCourses.value = value
            save()
        }

    // プラットフォーム固有の保存処理用コールバック
    var onSettingsChanged: (() -> Unit)? = null

    fun save() {
        onSettingsChanged?.invoke()
    }
}
