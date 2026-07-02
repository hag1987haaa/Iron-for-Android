package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.settings.LongPressMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

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

    private val _isMetric = MutableStateFlow(settings.isMetric)
    val isMetric: StateFlow<Boolean> = _isMetric.asStateFlow()

    private val _notifDistance = MutableStateFlow(settings.notificationDistanceMeters)
    val notifDistance: StateFlow<Int> = _notifDistance.asStateFlow()

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

    val isPrivacyMapModeEnabled: StateFlow<Boolean> = settings.isPrivacyMapModeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settings.isPrivacyMapModeEnabled)

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
        settings.enabledMidTypes = newTypes
        _enabledMidTypes.value = newTypes
        settings.save()
    }

    fun updateMetric(isMetric: Boolean) {
        settings.isMetric = isMetric
        _isMetric.value = isMetric
        settings.save()
    }

    fun updateNotifDistance(meters: Int) {
        settings.notificationDistanceMeters = meters
        _notifDistance.value = meters
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
        // ウォッチへ即座に同期（もし計測中なら反映させる）
        hag1987haaa.pebble.iron.KmpDependencies.trackerEngine.triggerStatisticsUpdate()
    }

    fun refreshUris() {
        _autoExportTcxUri.value = settings.autoExportTcxUri
        _autoExportGpxUri.value = settings.autoExportGpxUri
    }
}
