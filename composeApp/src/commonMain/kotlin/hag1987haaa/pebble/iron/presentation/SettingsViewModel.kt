package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.settings.MapColorPreset
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.settings.LongPressMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {

    private val _isMusicControlEnabled = MutableStateFlow(settings.isMusicControlEnabled)
    val isMusicControlEnabled: StateFlow<Boolean> = _isMusicControlEnabled.asStateFlow()

    private val _isLongPressEnabled = MutableStateFlow(settings.isLongPressEnabled)
    val isLongPressEnabled: StateFlow<Boolean> = _isLongPressEnabled.asStateFlow()

    private val _upLongPressMode = MutableStateFlow(settings.upLongPressMode)
    val upLongPressMode: StateFlow<LongPressMode> = _upLongPressMode.asStateFlow()

    private val _selectLongPressMode = MutableStateFlow(settings.selectLongPressMode)
    val selectLongPressMode: StateFlow<LongPressMode> = _selectLongPressMode.asStateFlow()

    private val _downLongPressMode = MutableStateFlow(settings.downLongPressMode)
    val downLongPressMode: StateFlow<LongPressMode> = _downLongPressMode.asStateFlow()

    private val _isAutomationEnabled = MutableStateFlow(settings.isAutomationEnabled)
    val isAutomationEnabled: StateFlow<Boolean> = _isAutomationEnabled.asStateFlow()

    private val _isCommand50Enabled = MutableStateFlow(settings.isCommand50Enabled)
    val isCommand50Enabled: StateFlow<Boolean> = _isCommand50Enabled.asStateFlow()

    private val _isCommand51Enabled = MutableStateFlow(settings.isCommand51Enabled)
    val isCommand51Enabled: StateFlow<Boolean> = _isCommand51Enabled.asStateFlow()

    private val _isCommand52Enabled = MutableStateFlow(settings.isCommand52Enabled)
    val isCommand52Enabled: StateFlow<Boolean> = _isCommand52Enabled.asStateFlow()

    private val _userWeight = MutableStateFlow(settings.userWeightKg)
    val userWeight: StateFlow<Float> = _userWeight.asStateFlow()

    private val _enabledGraphTypes = MutableStateFlow(settings.enabledGraphTypes)
    val enabledGraphTypes: StateFlow<List<Int>> = _enabledGraphTypes.asStateFlow()

    private val _enabledMidTypes = MutableStateFlow(settings.enabledMidTypes)
    val enabledMidTypes: StateFlow<List<Int>> = _enabledMidTypes.asStateFlow()

    private val _enabledLowerTypes = MutableStateFlow(settings.enabledLowerTypes)
    val enabledLowerTypes: StateFlow<List<Int>> = _enabledLowerTypes.asStateFlow()

    val activityMidTypes: StateFlow<Map<String, List<Int>>> = settings.activityMidTypesFlow
    val activityLowerTypes: StateFlow<Map<String, List<Int>>> = settings.activityLowerTypesFlow

    private val _isMetric = MutableStateFlow(settings.isMetric)
    val isMetric: StateFlow<Boolean> = _isMetric.asStateFlow()

    private val _notifDistanceStep = MutableStateFlow(settings.notificationDistanceStep)
    val notifDistanceStep: StateFlow<Float> = _notifDistanceStep.asStateFlow()

    val mapRouteColor: StateFlow<MapColorPreset> = settings.mapRouteColorFlow
    val mapPlannedColor: StateFlow<MapColorPreset> = settings.mapPlannedColorFlow
    val mapLocationColor: StateFlow<MapColorPreset> = settings.mapLocationColorFlow
    val activityMapZooms: StateFlow<Map<String, Int>> = settings.activityMapZoomsFlow

    private val _mapAutoCloseTimeoutSeconds = MutableStateFlow(settings.mapAutoCloseTimeoutSeconds)
    val mapAutoCloseTimeoutSeconds: StateFlow<Int> = _mapAutoCloseTimeoutSeconds.asStateFlow()

    private val _isMapSwipePanEnabled = MutableStateFlow(settings.isMapSwipePanEnabled)
    val isMapSwipePanEnabled: StateFlow<Boolean> = _isMapSwipePanEnabled.asStateFlow()

    private val _isAutoShowMapOnReadyEnabled = MutableStateFlow(settings.isAutoShowMapOnReadyEnabled)
    val isAutoShowMapOnReadyEnabled: StateFlow<Boolean> = _isAutoShowMapOnReadyEnabled.asStateFlow()

    private val _isNotificationVibrationEnabled = MutableStateFlow(settings.isNotificationVibrationEnabled)
    val isNotificationVibrationEnabled: StateFlow<Boolean> = _isNotificationVibrationEnabled.asStateFlow()

    val isDistNotificationVibrationEnabled: StateFlow<Boolean> = settings.isDistNotificationVibrationEnabledFlow
    val autoShowMapAfterDistNotificationSeconds: StateFlow<Int> = settings.autoShowMapAfterDistNotificationSecondsFlow
    val isTimeNotificationVibrationEnabled: StateFlow<Boolean> = settings.isTimeNotificationVibrationEnabledFlow
    val autoShowMapAfterTimeNotificationSeconds: StateFlow<Int> = settings.autoShowMapAfterTimeNotificationSecondsFlow

    val isHrZoneNotificationEnabled: StateFlow<Boolean> = settings.isHrZoneNotificationEnabledFlow
    val hrZoneOutDurationSeconds: StateFlow<Int> = settings.hrZoneOutDurationSecondsFlow
    val hrZoneRepeatIntervalSeconds: StateFlow<Int> = settings.hrZoneRepeatIntervalSecondsFlow
    val activityHrZoneMins: StateFlow<Map<String, Int>> = settings.activityHrZoneMinsFlow
    val activityHrZoneMaxs: StateFlow<Map<String, Int>> = settings.activityHrZoneMaxsFlow

    private val _autoShowMapAfterNotificationSeconds = MutableStateFlow(settings.autoShowMapAfterNotificationSeconds)
    val autoShowMapAfterNotificationSeconds: StateFlow<Int> = _autoShowMapAfterNotificationSeconds.asStateFlow()

    private val _notifTime = MutableStateFlow(settings.notificationTimeSeconds)
    val notifTime: StateFlow<Int> = _notifTime.asStateFlow()

    private val _isAutoLaunchDistEnabled = MutableStateFlow(settings.isAutoLaunchOnDistanceNotificationEnabled)
    val isAutoLaunchDistEnabled: StateFlow<Boolean> = _isAutoLaunchDistEnabled.asStateFlow()

    private val _isAutoLaunchTimeEnabled = MutableStateFlow(settings.isAutoLaunchOnTimeNotificationEnabled)
    val isAutoLaunchTimeEnabled: StateFlow<Boolean> = _isAutoLaunchTimeEnabled.asStateFlow()

    private val _isAutoExportTcxEnabled = MutableStateFlow(settings.isAutoExportTcxEnabled)
    val isAutoExportTcxEnabled: StateFlow<Boolean> = _isAutoExportTcxEnabled.asStateFlow()

    private val _isAutoExportGpxEnabled = MutableStateFlow(settings.isAutoExportGpxEnabled)
    val isAutoExportGpxEnabled: StateFlow<Boolean> = _isAutoExportGpxEnabled.asStateFlow()

    private val _autoExportTcxUri = MutableStateFlow(settings.autoExportTcxUri)
    val autoExportTcxUri: StateFlow<String?> = _autoExportTcxUri.asStateFlow()

    private val _autoExportGpxUri = MutableStateFlow(settings.autoExportGpxUri)
    val autoExportGpxUri: StateFlow<String?> = _autoExportGpxUri.asStateFlow()

    private val _hrSamplingInterval = MutableStateFlow(settings.hrSamplingInterval)
    val hrSamplingInterval: StateFlow<Int> = _hrSamplingInterval.asStateFlow()

    // Heart Rate
    private val _bleHeartRateDeviceAddress = MutableStateFlow(settings.bleHeartRateDeviceAddress)
    val bleHeartRateDeviceAddress: StateFlow<String?> = _bleHeartRateDeviceAddress.asStateFlow()

    private val _bleHeartRateDeviceName = MutableStateFlow(settings.bleHeartRateDeviceName)
    val bleHeartRateDeviceName: StateFlow<String?> = _bleHeartRateDeviceName.asStateFlow()

    private val _isBleHeartRateEnabled = MutableStateFlow(settings.isBleHeartRateEnabled)
    val isBleHeartRateEnabled: StateFlow<Boolean> = _isBleHeartRateEnabled.asStateFlow()

    private val _preferBleHeartRate = MutableStateFlow(settings.preferBleHeartRate)
    val preferBleHeartRate: StateFlow<Boolean> = _preferBleHeartRate.asStateFlow()

    private val _registeredBleHrDevices = MutableStateFlow(settings.registeredBleHrDevices)
    val registeredBleHrDevices: StateFlow<List<String>> = _registeredBleHrDevices.asStateFlow()

    private val _preferredBleHrAddress = MutableStateFlow(settings.preferredBleHrAddress)
    val preferredBleHrAddress: StateFlow<String?> = _preferredBleHrAddress.asStateFlow()

    // CSC (ケイデンス & スピード)
    private val _isBleCscEnabled = MutableStateFlow(settings.isBleCscEnabled)
    val isBleCscEnabled: StateFlow<Boolean> = _isBleCscEnabled.asStateFlow()

    private val _bleCscDeviceAddress = MutableStateFlow(settings.bleCscDeviceAddress)
    val bleCscDeviceAddress: StateFlow<String?> = _bleCscDeviceAddress.asStateFlow()

    private val _registeredBleCscDevices = MutableStateFlow(settings.registeredBleCscDevices)
    val registeredBleCscDevices: StateFlow<List<String>> = _registeredBleCscDevices.asStateFlow()

    private val _preferredBleCscAddress = MutableStateFlow(settings.preferredBleCscAddress)
    val preferredBleCscAddress: StateFlow<String?> = _preferredBleCscAddress.asStateFlow()

    private val _wheelCircumferenceMm = MutableStateFlow(settings.wheelCircumferenceMm)
    val wheelCircumferenceMm: StateFlow<Int> = _wheelCircumferenceMm.asStateFlow()

    // Cycling Power
    private val _isBlePowerEnabled = MutableStateFlow(settings.isBlePowerEnabled)
    val isBlePowerEnabled: StateFlow<Boolean> = _isBlePowerEnabled.asStateFlow()

    private val _blePowerDeviceAddress = MutableStateFlow(settings.blePowerDeviceAddress)
    val blePowerDeviceAddress: StateFlow<String?> = _blePowerDeviceAddress.asStateFlow()

    private val _registeredBlePowerDevices = MutableStateFlow(settings.registeredBlePowerDevices)
    val registeredBlePowerDevices: StateFlow<List<String>> = _registeredBlePowerDevices.asStateFlow()

    private val _preferredBlePowerAddress = MutableStateFlow(settings.preferredBlePowerAddress)
    val preferredBlePowerAddress: StateFlow<String?> = _preferredBlePowerAddress.asStateFlow()

    // 最後に開いていたタブ
    private val _currentTab = MutableStateFlow(try { SettingsTab.valueOf(settings.lastSettingsTabName) } catch(e: Exception) { SettingsTab.PHONE })
    val currentTab: StateFlow<SettingsTab> = _currentTab.asStateFlow()

    fun updateCurrentTab(tab: SettingsTab) {
        _currentTab.value = tab
        settings.lastSettingsTabName = tab.name
        settings.save()
    }

    val isPrivacyMapModeEnabled: StateFlow<Boolean> = settings.isPrivacyMapModeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settings.isPrivacyMapModeEnabled)

    val pebblePlatform: StateFlow<String?> = settings.pebblePlatformFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settings.pebblePlatform)

    fun updateMusicControlEnabled(enabled: Boolean) {
        settings.isMusicControlEnabled = enabled
        _isMusicControlEnabled.value = enabled
        settings.save()
    }

    fun updateLongPressEnabled(enabled: Boolean) {
        settings.isLongPressEnabled = enabled
        _isLongPressEnabled.value = enabled
        settings.save()
    }

    fun updateUpLongPressMode(mode: LongPressMode) {
        settings.upLongPressMode = mode
        _upLongPressMode.value = mode
        settings.save()
    }

    fun updateSelectLongPressMode(mode: LongPressMode) {
        settings.selectLongPressMode = mode
        _selectLongPressMode.value = mode
        settings.save()
    }

    fun updateDownLongPressMode(mode: LongPressMode) {
        settings.downLongPressMode = mode
        _downLongPressMode.value = mode
        settings.save()
    }

    fun updateAutomationEnabled(enabled: Boolean) {
        settings.isAutomationEnabled = enabled
        _isAutomationEnabled.value = enabled
        settings.save()
    }

    fun updateCommand50Enabled(enabled: Boolean) {
        settings.isCommand50Enabled = enabled
        _isCommand50Enabled.value = enabled
        settings.save()
    }

    fun updateCommand51Enabled(enabled: Boolean) {
        settings.isCommand51Enabled = enabled
        _isCommand51Enabled.value = enabled
        settings.save()
    }

    fun updateCommand52Enabled(enabled: Boolean) {
        settings.isCommand52Enabled = enabled
        _isCommand52Enabled.value = enabled
        settings.save()
    }

    fun updatePrivacyMapModeEnabled(enabled: Boolean) {
        settings.isPrivacyMapModeEnabled = enabled
        settings.save()
    }

    fun updateUserWeight(weight: Float) {
        settings.userWeightKg = weight
        _userWeight.value = weight
        settings.save()
    }

    fun updateGraphSettings(newTypes: List<Int>) {
        settings.enabledGraphTypes = newTypes
        _enabledGraphTypes.value = newTypes
        settings.save()
    }

    fun updateMidDataSettings(newTypes: List<Int>) {
        updateMidDataSettings(ActivityType.RUNNING, newTypes)
    }

    fun updateMidDataSettings(type: ActivityType, newTypes: List<Int>) {
        settings.setMidTypes(type, newTypes)
        _enabledMidTypes.value = settings.enabledMidTypes
        settings.save()
    }

    fun resetMidDataSettings(type: ActivityType) {
        settings.resetMidTypes(type)
        _enabledMidTypes.value = settings.enabledMidTypes
        settings.save()
    }

    fun updateLowerDataSettings(newTypes: List<Int>) {
        updateLowerDataSettings(ActivityType.RUNNING, newTypes)
    }

    fun updateLowerDataSettings(type: ActivityType, newTypes: List<Int>) {
        settings.setLowerTypes(type, newTypes)
        _enabledLowerTypes.value = settings.enabledLowerTypes
        settings.save()
    }

    fun resetLowerDataSettings(type: ActivityType) {
        settings.resetLowerTypes(type)
        _enabledLowerTypes.value = settings.enabledLowerTypes
        settings.save()
    }

    fun updateMetric(isMetric: Boolean) {
        settings.isMetric = isMetric
        _isMetric.value = isMetric
        settings.save()
    }

    fun updateNotifDistanceStep(step: Double) {
        settings.notificationDistanceStep = step.toFloat()
        _notifDistanceStep.value = step.toFloat()
        settings.save()
    }

    fun updateNotifTime(seconds: Int) {
        settings.notificationTimeSeconds = seconds
        _notifTime.value = seconds
        settings.save()
    }

    fun updateAutoLaunchDistEnabled(enabled: Boolean) {
        settings.isAutoLaunchOnDistanceNotificationEnabled = enabled
        _isAutoLaunchDistEnabled.value = enabled
        settings.save()
    }

    fun updateAutoLaunchTimeEnabled(enabled: Boolean) {
        settings.isAutoLaunchOnTimeNotificationEnabled = enabled
        _isAutoLaunchTimeEnabled.value = enabled
        settings.save()
    }

    fun updateHrZoneNotificationEnabled(enabled: Boolean) {
        settings.isHrZoneNotificationEnabled = enabled
    }

    fun updateHrZoneMinBpm(minBpm: Int) {
        updateActivityHrZoneMin(ActivityType.RUNNING, minBpm)
    }

    fun updateHrZoneMaxBpm(maxBpm: Int) {
        updateActivityHrZoneMax(ActivityType.RUNNING, maxBpm)
    }

    fun updateHrZoneOutDurationSeconds(seconds: Int) {
        settings.hrZoneOutDurationSeconds = seconds.coerceIn(3, 120)
    }

    fun updateHrZoneRepeatIntervalSeconds(seconds: Int) {
        settings.hrZoneRepeatIntervalSeconds = if (seconds <= 0) 0 else seconds.coerceIn(5, 120)
    }

    fun updateActivityHrZoneMin(type: ActivityType, min: Int) {
        settings.setHrZoneMin(type, min)
    }

    fun updateActivityHrZoneMax(type: ActivityType, max: Int) {
        settings.setHrZoneMax(type, max)
    }

    fun getHrZoneMin(type: ActivityType): Int = settings.getHrZoneMin(type)
    fun getHrZoneMax(type: ActivityType): Int = settings.getHrZoneMax(type)

    fun updateAutoExportTcxEnabled(enabled: Boolean) {
        settings.isAutoExportTcxEnabled = enabled
        _isAutoExportTcxEnabled.value = enabled
        settings.save()
    }

    fun updateAutoExportGpxEnabled(enabled: Boolean) {
        settings.isAutoExportGpxEnabled = enabled
        _isAutoExportGpxEnabled.value = enabled
        settings.save()
    }

    fun updateHrSamplingInterval(interval: Int) {
        settings.hrSamplingInterval = interval
        _hrSamplingInterval.value = interval
        settings.save()
        hag1987haaa.pebble.iron.KmpDependencies.trackerEngine.triggerStatisticsUpdate()
    }

    fun updateBleHeartRateEnabled(enabled: Boolean) {
        settings.isBleHeartRateEnabled = enabled
        _isBleHeartRateEnabled.value = enabled
        settings.save()
        
        if (enabled) {
            hag1987haaa.pebble.iron.KmpDependencies.trackerEngine.triggerStatisticsUpdate()
        }

        viewModelScope.launch {
            _isBleHeartRateEnabled.emit(enabled)
        }
    }

    fun updatePreferBleHeartRate(prefer: Boolean) {
        settings.preferBleHeartRate = prefer
        _preferBleHeartRate.value = prefer
        settings.save()
    }

    fun setBleHeartRateDevice(address: String?, name: String?) {
        settings.bleHeartRateDeviceAddress = address
        settings.bleHeartRateDeviceName = name
        _bleHeartRateDeviceAddress.value = address
        _bleHeartRateDeviceName.value = name
        
        if (address != null && name != null) {
            val entry = "$address|$name"
            if (entry !in settings.registeredBleHrDevices) {
                settings.registeredBleHrDevices = settings.registeredBleHrDevices + entry
                _registeredBleHrDevices.value = settings.registeredBleHrDevices
            }
        }
        settings.save()
    }

    fun removeRegisteredBleHrDevice(address: String) {
        settings.registeredBleHrDevices = settings.registeredBleHrDevices.filter { !it.startsWith(address) }
        if (settings.preferredBleHrAddress == address) {
            settings.preferredBleHrAddress = null
            _preferredBleHrAddress.value = null
        }
        _registeredBleHrDevices.value = settings.registeredBleHrDevices
        settings.save()
    }

    fun togglePreferredBleHrDevice(address: String) {
        val next = if (settings.preferredBleHrAddress == address) null else address
        settings.preferredBleHrAddress = next
        _preferredBleHrAddress.value = next
        settings.save()
    }

    // --- CSC (ケイデンス & スピード) 設定操作 ---
    fun updateBleCscEnabled(enabled: Boolean) {
        settings.isBleCscEnabled = enabled
        settings.save()
        if (enabled) {
            hag1987haaa.pebble.iron.KmpDependencies.trackerEngine.triggerStatisticsUpdate()
        }
        viewModelScope.launch {
            _isBleCscEnabled.emit(enabled)
        }
    }

    fun setBleCscDevice(address: String?, name: String?) {
        settings.bleCscDeviceAddress = address
        settings.bleCscDeviceName = name
        _bleCscDeviceAddress.value = address
        if (address != null && name != null) {
            val entry = "$address|$name"
            if (entry !in settings.registeredBleCscDevices) {
                settings.registeredBleCscDevices = settings.registeredBleCscDevices + entry
                _registeredBleCscDevices.value = settings.registeredBleCscDevices
            }
        }
        settings.save()
    }

    fun removeRegisteredBleCscDevice(address: String) {
        settings.registeredBleCscDevices = settings.registeredBleCscDevices.filter { !it.startsWith(address) }
        if (settings.preferredBleCscAddress == address) {
            settings.preferredBleCscAddress = null
            _preferredBleCscAddress.value = null
        }
        _registeredBleCscDevices.value = settings.registeredBleCscDevices
        settings.save()
    }

    fun togglePreferredBleCscDevice(address: String) {
        val next = if (settings.preferredBleCscAddress == address) null else address
        settings.preferredBleCscAddress = next
        _preferredBleCscAddress.value = next
        settings.save()
    }

    fun updateWheelCircumference(mm: Int) {
        settings.wheelCircumferenceMm = mm
        _wheelCircumferenceMm.value = mm
        settings.save()
        hag1987haaa.pebble.iron.KmpDependencies.bleCscManager?.setWheelCircumferenceMm(mm)
    }

    // --- Cycling Power 設定操作 ---
    fun updateBlePowerEnabled(enabled: Boolean) {
        settings.isBlePowerEnabled = enabled
        settings.save()
        if (enabled) {
            hag1987haaa.pebble.iron.KmpDependencies.trackerEngine.triggerStatisticsUpdate()
        }
        viewModelScope.launch {
            _isBlePowerEnabled.emit(enabled)
        }
    }

    fun setBlePowerDevice(address: String?, name: String?) {
        settings.blePowerDeviceAddress = address
        settings.blePowerDeviceName = name
        _blePowerDeviceAddress.value = address
        if (address != null && name != null) {
            val entry = "$address|$name"
            if (entry !in settings.registeredBlePowerDevices) {
                settings.registeredBlePowerDevices = settings.registeredBlePowerDevices + entry
                _registeredBlePowerDevices.value = settings.registeredBlePowerDevices
            }
        }
        settings.save()
    }

    fun removeRegisteredBlePowerDevice(address: String) {
        settings.registeredBlePowerDevices = settings.registeredBlePowerDevices.filter { !it.startsWith(address) }
        if (settings.preferredBlePowerAddress == address) {
            settings.preferredBlePowerAddress = null
            _preferredBlePowerAddress.value = null
        }
        _registeredBlePowerDevices.value = settings.registeredBlePowerDevices
        settings.save()
    }

    fun togglePreferredBlePowerDevice(address: String) {
        val next = if (settings.preferredBlePowerAddress == address) null else address
        settings.preferredBlePowerAddress = next
        _preferredBlePowerAddress.value = next
        settings.save()
    }

    fun updateMapRouteColor(color: MapColorPreset) {
        settings.mapRouteColor = color
    }

    fun updateMapPlannedColor(color: MapColorPreset) {
        settings.mapPlannedColor = color
    }

    fun updateActivityMapZoom(type: ActivityType, zoom: Int) {
        settings.setMapZoomForActivity(type, zoom)
    }

    fun updateMapLocationColor(color: MapColorPreset) {
        settings.mapLocationColor = color
    }

    fun updateMapAutoCloseTimeoutSeconds(seconds: Int) {
        settings.mapAutoCloseTimeoutSeconds = seconds
        _mapAutoCloseTimeoutSeconds.value = seconds
        settings.save()
    }

    fun updateMapSwipePanEnabled(enabled: Boolean) {
        settings.isMapSwipePanEnabled = enabled
        _isMapSwipePanEnabled.value = enabled
        settings.save()
    }

    fun updateAutoShowMapOnReadyEnabled(enabled: Boolean) {
        settings.isAutoShowMapOnReadyEnabled = enabled
        _isAutoShowMapOnReadyEnabled.value = enabled
        settings.save()
    }

    fun updateNotificationVibrationEnabled(enabled: Boolean) {
        settings.isNotificationVibrationEnabled = enabled
        _isNotificationVibrationEnabled.value = enabled
        settings.save()
    }

    fun updateDistNotificationVibrationEnabled(enabled: Boolean) {
        settings.isDistNotificationVibrationEnabled = enabled
    }

    fun updateAutoShowMapAfterDistNotificationSeconds(seconds: Int) {
        settings.autoShowMapAfterDistNotificationSeconds = seconds
    }

    fun updateTimeNotificationVibrationEnabled(enabled: Boolean) {
        settings.isTimeNotificationVibrationEnabled = enabled
    }

    fun updateAutoShowMapAfterTimeNotificationSeconds(seconds: Int) {
        settings.autoShowMapAfterTimeNotificationSeconds = seconds
    }

    fun updateAutoShowMapAfterNotificationSeconds(seconds: Int) {
        settings.autoShowMapAfterNotificationSeconds = seconds
        _autoShowMapAfterNotificationSeconds.value = seconds
        settings.save()
    }

    fun refreshUris() {
        _autoExportTcxUri.value = settings.autoExportTcxUri
        _autoExportGpxUri.value = settings.autoExportGpxUri
    }
}
