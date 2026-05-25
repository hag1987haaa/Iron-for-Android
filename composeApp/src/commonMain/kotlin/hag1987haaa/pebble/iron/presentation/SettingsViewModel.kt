package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsViewModel(private val appSettings: AppSettings) : ViewModel() {

    private val _uuid = MutableStateFlow(appSettings.pebbleUuid)
    val uuid: StateFlow<String> = _uuid.asStateFlow()

    private val _useSportsApi = MutableStateFlow(appSettings.useSportsApi)
    val useSportsApi: StateFlow<Boolean> = _useSportsApi.asStateFlow()

    private val _isMusicControlEnabled = MutableStateFlow(appSettings.isMusicControlEnabled)
    val isMusicControlEnabled: StateFlow<Boolean> = _isMusicControlEnabled.asStateFlow()

    private val _isTouchControlEnabled = MutableStateFlow(appSettings.isTouchControlEnabled)
    val isTouchControlEnabled: StateFlow<Boolean> = _isTouchControlEnabled.asStateFlow()

    private val _isAutomationEnabled = MutableStateFlow(appSettings.isAutomationEnabled)
    val isAutomationEnabled: StateFlow<Boolean> = _isAutomationEnabled.asStateFlow()

    private val _isCommand50Enabled = MutableStateFlow(appSettings.isCommand50Enabled)
    val isCommand50Enabled: StateFlow<Boolean> = _isCommand50Enabled.asStateFlow()

    private val _isCommand51Enabled = MutableStateFlow(appSettings.isCommand51Enabled)
    val isCommand51Enabled: StateFlow<Boolean> = _isCommand51Enabled.asStateFlow()

    private val _isCommand52Enabled = MutableStateFlow(appSettings.isCommand52Enabled)
    val isCommand52Enabled: StateFlow<Boolean> = _isCommand52Enabled.asStateFlow()

    private val _isPrivacyMapModeEnabled = MutableStateFlow(appSettings.isPrivacyMapModeEnabled)
    val isPrivacyMapModeEnabled: StateFlow<Boolean> = _isPrivacyMapModeEnabled.asStateFlow()

    private val _userWeight = MutableStateFlow(appSettings.userWeightKg)
    val userWeight: StateFlow<Float> = _userWeight.asStateFlow()

    fun updateUuid(newUuid: String) {
        _uuid.value = newUuid
        appSettings.pebbleUuid = newUuid
        appSettings.save()
    }

    fun updateUseSportsApi(enabled: Boolean) {
        _useSportsApi.value = enabled
        appSettings.useSportsApi = enabled
        appSettings.save()
    }

    fun updateMusicControlEnabled(enabled: Boolean) {
        _isMusicControlEnabled.value = enabled
        appSettings.isMusicControlEnabled = enabled
        
        // タッチ操作設定も同時に更新し、ウォッチへ送信
        _isTouchControlEnabled.value = enabled
        appSettings.isTouchControlEnabled = enabled
        
        appSettings.save()
        
        // 設定を即座にウォッチへ同期
        KmpDependencies.trackerEngine.sendTouchConfig(enabled)
    }

    fun updatePrivacyMapModeEnabled(enabled: Boolean) {
        _isPrivacyMapModeEnabled.value = enabled
        appSettings.isPrivacyMapModeEnabled = enabled
        appSettings.save()
    }

    fun updateTouchControlEnabled(enabled: Boolean) {
        _isTouchControlEnabled.value = enabled
        appSettings.isTouchControlEnabled = enabled
        appSettings.save()
        // 設定変更時に即座にウォッチへ送信を試みる
        KmpDependencies.trackerEngine.sendTouchConfig(enabled)
    }

    fun updateAutomationEnabled(enabled: Boolean) {
        _isAutomationEnabled.value = enabled
        appSettings.isAutomationEnabled = enabled
        appSettings.save()
    }

    fun updateCommand50Enabled(enabled: Boolean) {
        _isCommand50Enabled.value = enabled
        appSettings.isCommand50Enabled = enabled
        appSettings.save()
    }

    fun updateCommand51Enabled(enabled: Boolean) {
        _isCommand51Enabled.value = enabled
        appSettings.isCommand51Enabled = enabled
        appSettings.save()
    }

    fun updateCommand52Enabled(enabled: Boolean) {
        _isCommand52Enabled.value = enabled
        appSettings.isCommand52Enabled = enabled
        appSettings.save()
    }

    fun updateUserWeight(weight: Float) {
        _userWeight.value = weight
        appSettings.userWeightKg = weight
        appSettings.save()
    }
}
