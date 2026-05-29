package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settings: AppSettings) : ViewModel() {

    private val _isMusicControlEnabled = MutableStateFlow(settings.isMusicControlEnabled)
    val isMusicControlEnabled: StateFlow<Boolean> = _isMusicControlEnabled.asStateFlow()

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

    val isPrivacyMapModeEnabled: StateFlow<Boolean> = settings.isPrivacyMapModeEnabledFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), settings.isPrivacyMapModeEnabled)

    fun updateMusicControlEnabled(enabled: Boolean) {
        settings.isMusicControlEnabled = enabled
        _isMusicControlEnabled.value = enabled
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
}
