package hag1987haaa.pebble.iron.domain.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings {
    var pebbleUuid: String = "0ec71971-1191-4e05-87f5-27a3c749023c"
    var useSportsApi: Boolean = true
    var isMusicControlEnabled: Boolean = true
    var isTouchControlEnabled: Boolean = false
    
    // 自動化・外部アプリ連携設定
    var isAutomationEnabled: Boolean = false
    var isCommand50Enabled: Boolean = true
    var isCommand51Enabled: Boolean = true
    var isCommand52Enabled: Boolean = true
    
    private val _isPrivacyMapModeEnabled = MutableStateFlow(false)
    val isPrivacyMapModeEnabledFlow: StateFlow<Boolean> = _isPrivacyMapModeEnabled.asStateFlow()
    var isPrivacyMapModeEnabled: Boolean
        get() = _isPrivacyMapModeEnabled.value
        set(value) { _isPrivacyMapModeEnabled.value = value }

    var userWeightKg: Float = 70.0f

    // プラットフォーム固有の保存処理用コールバック
    var onSettingsChanged: (() -> Unit)? = null

    fun save() {
        onSettingsChanged?.invoke()
    }
}
