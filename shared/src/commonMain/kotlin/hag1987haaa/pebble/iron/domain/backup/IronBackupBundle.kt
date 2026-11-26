package hag1987haaa.pebble.iron.domain.backup

import hag1987haaa.pebble.iron.domain.model.BiologicalSex
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.settings.LongPressMode
import hag1987haaa.pebble.iron.domain.settings.MapColorPreset
import hag1987haaa.pebble.iron.util.GpxCourse
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class IronBackupBundle(
    val backupVersion: Int = CURRENT_VERSION,
    val exportedAt: String = "",
    val runs: List<RunActivity> = emptyList(),
    val settings: SettingsBackup? = null,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}

@Serializable
data class SettingsBackup(
    // User profile & units
    val isMetric: Boolean? = null,
    val userWeightKg: Float? = null,
    val userHeightCm: Float? = null,
    val userBodyFatPct: Float? = null,
    val userRestingHr: Int? = null,
    val userBmrKcalPerDay: Float? = null,
    val userSex: String? = null,
    val userBirthDate: String? = null,

    // Workout list & per-activity watch profiles
    val enabledActivityTypes: List<String>? = null,
    val lastActivityType: String? = null,
    val activityMidTypes: Map<String, List<Int>>? = null,
    val activityLowerTypes: Map<String, List<Int>>? = null,
    val activityHrZoneEnabled: Map<String, Boolean>? = null,
    val activityHrZoneMins: Map<String, Int>? = null,
    val activityHrZoneMaxs: Map<String, Int>? = null,
    val activityMapZooms: Map<String, Int>? = null,

    // Watch common & notifications
    val enabledGraphTypes: List<Int>? = null,
    val notificationDistanceStep: Float? = null,
    val notificationTimeSeconds: Int? = null,
    val isDistNotificationVibrationEnabled: Boolean? = null,
    val autoShowMapAfterDistNotificationSeconds: Int? = null,
    val isTimeNotificationVibrationEnabled: Boolean? = null,
    val autoShowMapAfterTimeNotificationSeconds: Int? = null,
    val isAutoLaunchOnDistanceNotificationEnabled: Boolean? = null,
    val isAutoLaunchOnTimeNotificationEnabled: Boolean? = null,
    val hrZoneOutDurationSeconds: Int? = null,
    val hrZoneRepeatIntervalSeconds: Int? = null,
    val hrSamplingInterval: Int? = null,

    // Map, display & controls
    val isMapSwipePanEnabled: Boolean? = null,
    val isMapHeadingUp: Boolean? = null,
    val isAutoShowMapOnReadyEnabled: Boolean? = null,
    val mapAutoCloseTimeoutSeconds: Int? = null,
    val mapRouteColor: String? = null,
    val mapPlannedColor: String? = null,
    val mapLocationColor: String? = null,
    val isPrivacyMapModeEnabled: Boolean? = null,
    val isDetailMapPinned: Boolean? = null,
    val isMusicControlEnabled: Boolean? = null,
    val isTouchControlEnabled: Boolean? = null,
    val isLongPressEnabled: Boolean? = null,
    val upLongPressMode: String? = null,
    val selectLongPressMode: String? = null,
    val downLongPressMode: String? = null,
    val isAutomationEnabled: Boolean? = null,
    val isCommand50Enabled: Boolean? = null,
    val isCommand51Enabled: Boolean? = null,
    val isCommand52Enabled: Boolean? = null,
    val isAssistantIntegrationEnabled: Boolean? = null,

    // Portable sensor preferences (no device MAC addresses) & GPX courses
    val preferBleHeartRate: Boolean? = null,
    val wheelCircumferenceMm: Int? = null,
    val savedGpxCourses: List<GpxCourse>? = null,
)

object IronBackupCodec {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(
        runs: List<RunActivity>,
        settings: AppSettings,
        exportedAt: String = Clock.System.now().toString(),
    ): String {
        val bundle = IronBackupBundle(
            backupVersion = IronBackupBundle.CURRENT_VERSION,
            exportedAt = exportedAt,
            runs = runs,
            settings = settings.toSettingsBackup(),
        )
        return json.encodeToString(bundle)
    }

    fun decode(rawJson: String): IronBackupBundle {
        val trimmed = rawJson.trimStart('\uFEFF').trim()
        return when {
            trimmed.startsWith("[") -> {
                val legacyRuns = json.decodeFromString<List<RunActivity>>(trimmed)
                IronBackupBundle(
                    backupVersion = 1,
                    runs = legacyRuns,
                    settings = null,
                )
            }
            trimmed.startsWith("{") -> {
                json.decodeFromString<IronBackupBundle>(trimmed)
            }
            else -> throw IllegalArgumentException("Unsupported backup JSON format")
        }
    }
}

fun AppSettings.toSettingsBackup(): SettingsBackup = SettingsBackup(
    isMetric = isMetric,
    userWeightKg = userWeightKg,
    userHeightCm = userHeightCm,
    userBodyFatPct = userBodyFatPct,
    userRestingHr = userRestingHr,
    userBmrKcalPerDay = userBmrKcalPerDay,
    userSex = userSex.name,
    userBirthDate = userBirthDate?.toString(),
    enabledActivityTypes = enabledActivityTypes,
    lastActivityType = lastActivityType,
    activityMidTypes = activityMidTypes,
    activityLowerTypes = activityLowerTypes,
    activityHrZoneEnabled = activityHrZoneEnabled,
    activityHrZoneMins = activityHrZoneMins,
    activityHrZoneMaxs = activityHrZoneMaxs,
    activityMapZooms = activityMapZooms,
    enabledGraphTypes = enabledGraphTypes,
    notificationDistanceStep = notificationDistanceStep,
    notificationTimeSeconds = notificationTimeSeconds,
    isDistNotificationVibrationEnabled = isDistNotificationVibrationEnabled,
    autoShowMapAfterDistNotificationSeconds = autoShowMapAfterDistNotificationSeconds,
    isTimeNotificationVibrationEnabled = isTimeNotificationVibrationEnabled,
    autoShowMapAfterTimeNotificationSeconds = autoShowMapAfterTimeNotificationSeconds,
    isAutoLaunchOnDistanceNotificationEnabled = isAutoLaunchOnDistanceNotificationEnabled,
    isAutoLaunchOnTimeNotificationEnabled = isAutoLaunchOnTimeNotificationEnabled,
    hrZoneOutDurationSeconds = hrZoneOutDurationSeconds,
    hrZoneRepeatIntervalSeconds = hrZoneRepeatIntervalSeconds,
    hrSamplingInterval = hrSamplingInterval,
    isMapSwipePanEnabled = isMapSwipePanEnabled,
    isMapHeadingUp = isMapHeadingUp,
    isAutoShowMapOnReadyEnabled = isAutoShowMapOnReadyEnabled,
    mapAutoCloseTimeoutSeconds = mapAutoCloseTimeoutSeconds,
    mapRouteColor = mapRouteColor.name,
    mapPlannedColor = mapPlannedColor.name,
    mapLocationColor = mapLocationColor.name,
    isPrivacyMapModeEnabled = isPrivacyMapModeEnabled,
    isDetailMapPinned = isDetailMapPinned,
    isMusicControlEnabled = isMusicControlEnabled,
    isTouchControlEnabled = isTouchControlEnabled,
    isLongPressEnabled = isLongPressEnabled,
    upLongPressMode = upLongPressMode.name,
    selectLongPressMode = selectLongPressMode.name,
    downLongPressMode = downLongPressMode.name,
    isAutomationEnabled = isAutomationEnabled,
    isCommand50Enabled = isCommand50Enabled,
    isCommand51Enabled = isCommand51Enabled,
    isCommand52Enabled = isCommand52Enabled,
    isAssistantIntegrationEnabled = isAssistantIntegrationEnabled,
    preferBleHeartRate = preferBleHeartRate,
    wheelCircumferenceMm = wheelCircumferenceMm,
    savedGpxCourses = savedGpxCourses,
)

fun AppSettings.applySettingsBackup(backup: SettingsBackup) {
    val savedCallback = onSettingsChanged
    onSettingsChanged = null
    try {
        backup.isMetric?.let { isMetric = it }
        backup.userWeightKg?.let { userWeightKg = it }
        userHeightCm = backup.userHeightCm
        userBodyFatPct = backup.userBodyFatPct
        userRestingHr = backup.userRestingHr
        userBmrKcalPerDay = backup.userBmrKcalPerDay
        backup.userSex?.let { name ->
            BiologicalSex.entries.find { it.name == name }?.let { userSex = it }
        }
        userBirthDate = backup.userBirthDate?.let {
            try { LocalDate.parse(it) } catch (_: Exception) { null }
        }

        backup.enabledActivityTypes?.let { enabledActivityTypes = it }
        backup.lastActivityType?.let { lastActivityType = it }
        backup.activityMidTypes?.let { activityMidTypes = activityMidTypes + it }
        backup.activityLowerTypes?.let { activityLowerTypes = activityLowerTypes + it }
        backup.activityHrZoneEnabled?.let { activityHrZoneEnabled = activityHrZoneEnabled + it }
        backup.activityHrZoneMins?.let { activityHrZoneMins = activityHrZoneMins + it }
        backup.activityHrZoneMaxs?.let { activityHrZoneMaxs = activityHrZoneMaxs + it }
        backup.activityMapZooms?.let { activityMapZooms = activityMapZooms + it }

        backup.enabledGraphTypes?.let { enabledGraphTypes = it }
        backup.notificationDistanceStep?.let { notificationDistanceStep = it }
        backup.notificationTimeSeconds?.let { notificationTimeSeconds = it }
        backup.isDistNotificationVibrationEnabled?.let { isDistNotificationVibrationEnabled = it }
        backup.autoShowMapAfterDistNotificationSeconds?.let { autoShowMapAfterDistNotificationSeconds = it }
        backup.isTimeNotificationVibrationEnabled?.let { isTimeNotificationVibrationEnabled = it }
        backup.autoShowMapAfterTimeNotificationSeconds?.let { autoShowMapAfterTimeNotificationSeconds = it }
        backup.isAutoLaunchOnDistanceNotificationEnabled?.let { isAutoLaunchOnDistanceNotificationEnabled = it }
        backup.isAutoLaunchOnTimeNotificationEnabled?.let { isAutoLaunchOnTimeNotificationEnabled = it }
        backup.hrZoneOutDurationSeconds?.let { hrZoneOutDurationSeconds = it }
        backup.hrZoneRepeatIntervalSeconds?.let { hrZoneRepeatIntervalSeconds = it }
        backup.hrSamplingInterval?.let { hrSamplingInterval = it }

        backup.isMapSwipePanEnabled?.let { isMapSwipePanEnabled = it }
        backup.isMapHeadingUp?.let { isMapHeadingUp = it }
        backup.isAutoShowMapOnReadyEnabled?.let { isAutoShowMapOnReadyEnabled = it }
        backup.mapAutoCloseTimeoutSeconds?.let { mapAutoCloseTimeoutSeconds = it }
        backup.mapRouteColor?.let { name ->
            MapColorPreset.entries.find { it.name == name }?.let { mapRouteColor = it }
        }
        backup.mapPlannedColor?.let { name ->
            MapColorPreset.entries.find { it.name == name }?.let { mapPlannedColor = it }
        }
        backup.mapLocationColor?.let { name ->
            MapColorPreset.entries.find { it.name == name }?.let { mapLocationColor = it }
        }
        backup.isPrivacyMapModeEnabled?.let { isPrivacyMapModeEnabled = it }
        backup.isDetailMapPinned?.let { isDetailMapPinned = it }
        backup.isMusicControlEnabled?.let { isMusicControlEnabled = it }
        backup.isTouchControlEnabled?.let { isTouchControlEnabled = it }
        backup.isLongPressEnabled?.let { isLongPressEnabled = it }
        backup.upLongPressMode?.let { name ->
            LongPressMode.entries.find { it.name == name }?.let { upLongPressMode = it }
        }
        backup.selectLongPressMode?.let { name ->
            LongPressMode.entries.find { it.name == name }?.let { selectLongPressMode = it }
        }
        backup.downLongPressMode?.let { name ->
            LongPressMode.entries.find { it.name == name }?.let { downLongPressMode = it }
        }
        backup.isAutomationEnabled?.let { isAutomationEnabled = it }
        backup.isCommand50Enabled?.let { isCommand50Enabled = it }
        backup.isCommand51Enabled?.let { isCommand51Enabled = it }
        backup.isCommand52Enabled?.let { isCommand52Enabled = it }
        backup.isAssistantIntegrationEnabled?.let { isAssistantIntegrationEnabled = it }

        backup.preferBleHeartRate?.let { preferBleHeartRate = it }
        backup.wheelCircumferenceMm?.let { wheelCircumferenceMm = it }
        backup.savedGpxCourses?.let { savedGpxCourses = it }
    } finally {
        onSettingsChanged = savedCallback
    }
    save()
}
