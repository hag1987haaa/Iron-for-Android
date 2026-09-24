package hag1987haaa.pebble.iron

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import hag1987haaa.pebble.iron.data.repository.SqlRunRepository
import hag1987haaa.pebble.iron.db.DatabaseDriverFactory
import hag1987haaa.pebble.iron.db.PebbleTrackerDatabase
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.settings.MapColorPreset
import hag1987haaa.pebble.iron.util.toActivePlannedPoints
import kotlinx.serialization.encodeToString
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.tracker.RunTrackerEngine
import hag1987haaa.pebble.iron.health.HealthConnectManager
import hag1987haaa.pebble.iron.location.AndroidLocationTracker
import hag1987haaa.pebble.iron.pebble.AndroidPebbleMessenger
import hag1987haaa.pebble.iron.ble.AndroidBleScanner
import hag1987haaa.pebble.iron.ble.AndroidBleHeartRateManager
import kotlinx.coroutines.MainScope
import androidx.compose.ui.graphics.asImageBitmap

@SuppressLint("StaticFieldLeak")
object AndroidDependencies {
    private var isInitialized = false
    private var _healthConnectManager: HealthConnectManager? = null

    val healthConnectManager: HealthConnectManager
        get() = _healthConnectManager ?: throw IllegalStateException("AndroidDependencies not initialized")

    fun initialize(context: Context) {
        if (isInitialized) return
        Log.d("AndroidDependencies", "Initializing dependencies...")
        
        // Ensure we use application context to avoid leaks
        val appContext = context.applicationContext

        hag1987haaa.pebble.iron.presentation.platformImageBitmapConverter = { width, height, rgba ->
            try {
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.setPixels(rgba, 0, width, 0, 0, width, height)
                bitmap.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
        
        val settings = AppSettings()
        
        // --- 設定の読み込み (SharedPreferences) ---
        val prefs = appContext.getSharedPreferences("iron_settings", Context.MODE_PRIVATE)
        settings.isMusicControlEnabled = prefs.getBoolean("music_enabled", false)
        settings.isTouchControlEnabled = prefs.getBoolean("touch_enabled", false)
        settings.isMapSwipePanEnabled = prefs.getBoolean("map_swipe_pan_enabled", true)
        settings.cartoApiKey = prefs.getString("carto_api_key", "") ?: ""
        settings.isLongPressEnabled = prefs.getBoolean("longpress_enabled", false)
        settings.upLongPressMode = hag1987haaa.pebble.iron.domain.settings.LongPressMode.valueOf(
            prefs.getString("longpress_up_mode", hag1987haaa.pebble.iron.domain.settings.LongPressMode.MUSIC.name) ?: hag1987haaa.pebble.iron.domain.settings.LongPressMode.MUSIC.name
        )
        settings.selectLongPressMode = hag1987haaa.pebble.iron.domain.settings.LongPressMode.valueOf(
            prefs.getString("longpress_select_mode", hag1987haaa.pebble.iron.domain.settings.LongPressMode.MUSIC.name) ?: hag1987haaa.pebble.iron.domain.settings.LongPressMode.MUSIC.name
        )
        settings.downLongPressMode = hag1987haaa.pebble.iron.domain.settings.LongPressMode.valueOf(
            prefs.getString("longpress_down_mode", hag1987haaa.pebble.iron.domain.settings.LongPressMode.MUSIC.name) ?: hag1987haaa.pebble.iron.domain.settings.LongPressMode.MUSIC.name
        )
        settings.isAutomationEnabled = prefs.getBoolean("auto_enabled", false)
        settings.isCommand50Enabled = prefs.getBoolean("cmd50_enabled", true)
        settings.isCommand51Enabled = prefs.getBoolean("cmd51_enabled", true)
        settings.isCommand52Enabled = prefs.getBoolean("cmd52_enabled", true)
        settings.isPrivacyMapModeEnabled = prefs.getBoolean("privacy_map_enabled", false)
        settings.isAutoShowMapOnReadyEnabled = prefs.getBoolean("auto_show_map_on_ready", false)
        val savedTimeout = prefs.getInt("map_auto_close_timeout", 10)
        settings.mapAutoCloseTimeoutSeconds = if (savedTimeout <= 0 || savedTimeout > 30) 10 else savedTimeout
        settings.mapRouteColor = MapColorPreset.fromId(prefs.getString("map_route_color", null), MapColorPreset.RED)
        settings.mapPlannedColor = MapColorPreset.fromId(prefs.getString("map_planned_color", null), MapColorPreset.YELLOW)
        settings.mapLocationColor = MapColorPreset.fromId(prefs.getString("map_location_color", null), MapColorPreset.GREEN)

        val zoomsStr = prefs.getString("activity_map_zooms", null)
        if (!zoomsStr.isNullOrEmpty()) {
            val map = zoomsStr.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) {
                    val actName = parts[0].trim()
                    val z = parts[1].trim().toIntOrNull()
                    if (z != null) actName to z else null
                } else null
            }.toMap()
            if (map.isNotEmpty()) {
                val current = settings.activityMapZooms.toMutableMap()
                current.putAll(map)
                settings.activityMapZooms = current
            }
        }
        settings.userWeightKg = prefs.getFloat("user_weight", 70.0f)
        settings.hasAskedHealthConnectOnboarding = prefs.getBoolean("hc_onboarding_asked", false)
        
        // グラフ・通知設定の読み込み
        val graphTypesStr = prefs.getString("graph_types", "0,1,2,3,4,5") ?: "0,1,2,3,4,5"
        settings.enabledGraphTypes = graphTypesStr.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
        settings.notificationDistanceStep = prefs.getFloat("notif_dist_step", 1.0f)
        settings.notificationTimeSeconds = prefs.getInt("notif_time", 0)
        settings.isAutoLaunchOnDistanceNotificationEnabled = prefs.getBoolean("auto_launch_dist", false)
        settings.isAutoLaunchOnTimeNotificationEnabled = prefs.getBoolean("auto_launch_time", false)
        val oldVib = prefs.getBoolean("notif_vibration", true)
        settings.isDistNotificationVibrationEnabled = prefs.getBoolean("dist_notif_vibration", oldVib)
        settings.isTimeNotificationVibrationEnabled = prefs.getBoolean("time_notif_vibration", oldVib)

        val oldAutoShowMap = prefs.getInt("auto_show_map_after_notif_sec", 0)
        settings.autoShowMapAfterDistNotificationSeconds = prefs.getInt("dist_auto_show_map_sec", oldAutoShowMap)
        settings.autoShowMapAfterTimeNotificationSeconds = prefs.getInt("time_auto_show_map_sec", oldAutoShowMap)
        
        // Mid Data 設定の読み込み
        val midTypesStr = prefs.getString("mid_types", "0,4,1,5,10") ?: "0,4,1,5,10"
        settings.enabledMidTypes = midTypesStr.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
        val lowerTypesStr = prefs.getString("lower_types", "100,101,102,103,104,105") ?: "100,101,102,103,104,105"
        settings.enabledLowerTypes = lowerTypesStr.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
        settings.isMetric = prefs.getBoolean("is_metric", true)
        
        // 自動エクスポート設定
        settings.isAutoExportTcxEnabled = prefs.getBoolean("auto_export_tcx", false)
        settings.isAutoExportGpxEnabled = prefs.getBoolean("auto_export_gpx", false)
        settings.autoExportTcxUri = prefs.getString("auto_export_tcx_uri", null)
        settings.autoExportGpxUri = prefs.getString("auto_export_gpx_uri", null)
        
        settings.hrSamplingInterval = prefs.getInt("hr_interval", 0)
        settings.lastActivityType = prefs.getString("last_activity_type", ActivityType.RUNNING.name) ?: ActivityType.RUNNING.name
        
        // 中段表示・グラフのIDを読み込み、未設定ならリストの先頭をデフォルトにする
        // 中段・下段表示のIDを読み込み、未設定ならリストの先頭をデフォルトにする
        val lastMidId = prefs.getInt("last_mid_id", -1)
        val validatedMidId = if (lastMidId != -1 && lastMidId in settings.enabledMidTypes) {
            lastMidId
        } else {
            settings.enabledMidTypes.firstOrNull() ?: -1
        }
        settings.lastMidId = validatedMidId

        val lastLowerId = prefs.getInt("last_lower_id", -1)
        val validatedLowerId = if (lastLowerId != -1 && lastLowerId in settings.enabledLowerTypes) {
            lastLowerId
        } else {
            settings.enabledLowerTypes.firstOrNull() ?: -1
        }
        settings.lastLowerId = validatedLowerId

        val lastGraphId = prefs.getInt("last_graph_id", -1)
        val validatedGraphId = if (lastGraphId != -1 && lastGraphId in settings.enabledGraphTypes) {
            lastGraphId
        } else {
            settings.enabledGraphTypes.firstOrNull() ?: -1
        }
        settings.lastGraphTypeId = validatedGraphId

        prefs.edit().apply {
            putInt("last_mid_id", settings.lastMidId)
            putInt("last_lower_id", settings.lastLowerId)
            putInt("last_graph_id", settings.lastGraphTypeId)
            apply()
        }

        settings.lastMainTab = prefs.getInt("last_main_tab", 0)
        settings.lastSettingsTabName = prefs.getString("last_settings_tab", "PHONE") ?: "PHONE"
        settings.lastHistoryViewModeName = prefs.getString("last_history_view", "SCROLL") ?: "SCROLL"

        // BLE センサー設定
        settings.bleHeartRateDeviceAddress = prefs.getString("ble_hr_address", null)
        settings.bleHeartRateDeviceName = prefs.getString("ble_hr_name", null)
        settings.registeredBleHrDevices = prefs.getString("ble_hr_registered", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        settings.preferredBleHrAddress = prefs.getString("ble_hr_preferred", null)
        settings.isBleHeartRateEnabled = prefs.getBoolean("ble_hr_enabled", false)
        settings.preferBleHeartRate = prefs.getBoolean("ble_hr_prefer", true)

        // GPXコース設定の読み込み (JSONファイル)
        val coursesFile = java.io.File(appContext.filesDir, "saved_gpx_courses.json")
        if (coursesFile.exists()) {
            try {
                val coursesJson = coursesFile.readText()
                settings.savedGpxCourses = hag1987haaa.pebble.iron.util.parseGpxCoursesJson(coursesJson)
                Log.d("AndroidDependencies", "Loaded ${settings.savedGpxCourses.size} saved GPX courses from disk")
            } catch (e: Exception) {
                Log.e("AndroidDependencies", "Failed to load saved GPX courses", e)
            }
        }

        // アプリバージョンの取得
        try {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            settings.appVersion = packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            Log.e("AndroidDependencies", "Failed to get version name", e)
        }

        // 保存用コールバックの登録
        settings.onSettingsChanged = {
            // GPXコースの保存 (JSONファイル)
            try {
                val coursesJson = hag1987haaa.pebble.iron.util.gpxJson.encodeToString(settings.savedGpxCourses)
                java.io.File(appContext.filesDir, "saved_gpx_courses.json").writeText(coursesJson)
            } catch (e: Exception) {
                Log.e("AndroidDependencies", "Failed to save GPX courses to disk", e)
            }
            prefs.edit().apply {
                putBoolean("music_enabled", settings.isMusicControlEnabled)
                putBoolean("touch_enabled", settings.isTouchControlEnabled)
                putBoolean("map_swipe_pan_enabled", settings.isMapSwipePanEnabled)
                putString("carto_api_key", settings.cartoApiKey)
                putBoolean("longpress_enabled", settings.isLongPressEnabled)
                putString("longpress_up_mode", settings.upLongPressMode.name)
                putString("longpress_select_mode", settings.selectLongPressMode.name)
                putString("longpress_down_mode", settings.downLongPressMode.name)
                putBoolean("auto_enabled", settings.isAutomationEnabled)
                putBoolean("cmd50_enabled", settings.isCommand50Enabled)
                putBoolean("cmd51_enabled", settings.isCommand51Enabled)
                putBoolean("cmd52_enabled", settings.isCommand52Enabled)
                putBoolean("privacy_map_enabled", settings.isPrivacyMapModeEnabled)
                putBoolean("auto_show_map_on_ready", settings.isAutoShowMapOnReadyEnabled)
                putInt("map_auto_close_timeout", settings.mapAutoCloseTimeoutSeconds)
                putString("map_route_color", settings.mapRouteColor.id)
                putString("map_planned_color", settings.mapPlannedColor.id)
                putString("map_location_color", settings.mapLocationColor.id)
                putString("activity_map_zooms", settings.activityMapZooms.entries.joinToString(",") { "${it.key}:${it.value}" })
                putFloat("user_weight", settings.userWeightKg)
                putBoolean("hc_onboarding_asked", settings.hasAskedHealthConnectOnboarding)
                
                // グラフ・通知設定の保存
                putString("graph_types", settings.enabledGraphTypes.joinToString(","))
                putFloat("notif_dist_step", settings.notificationDistanceStep)
                putInt("notif_time", settings.notificationTimeSeconds)
                putBoolean("auto_launch_dist", settings.isAutoLaunchOnDistanceNotificationEnabled)
                putBoolean("auto_launch_time", settings.isAutoLaunchOnTimeNotificationEnabled)
        putBoolean("notif_vibration", settings.isDistNotificationVibrationEnabled)
        putInt("auto_show_map_after_notif_sec", settings.autoShowMapAfterDistNotificationSeconds)
        putBoolean("dist_notif_vibration", settings.isDistNotificationVibrationEnabled)
        putBoolean("time_notif_vibration", settings.isTimeNotificationVibrationEnabled)
        putInt("dist_auto_show_map_sec", settings.autoShowMapAfterDistNotificationSeconds)
        putInt("time_auto_show_map_sec", settings.autoShowMapAfterTimeNotificationSeconds)
                
                // Mid / Lower Data 設定の保存
                putString("mid_types", settings.enabledMidTypes.joinToString(","))
                putString("lower_types", settings.enabledLowerTypes.joinToString(","))
                putBoolean("is_metric", settings.isMetric)
                
                // 自動エクスポート設定の保存
                putBoolean("auto_export_tcx", settings.isAutoExportTcxEnabled)
                putBoolean("auto_export_gpx", settings.isAutoExportGpxEnabled)
                putString("auto_export_tcx_uri", settings.autoExportTcxUri)
                putString("auto_export_gpx_uri", settings.autoExportGpxUri)
                
                // 心拍サンプリング間隔
                putInt("hr_interval", settings.hrSamplingInterval)
                putString("last_activity_type", settings.lastActivityType)
                putInt("last_mid_id", settings.lastMidId)
                putInt("last_lower_id", settings.lastLowerId)
                putInt("last_graph_id", settings.lastGraphTypeId)
                
                putInt("last_main_tab", settings.lastMainTab)
                putString("last_settings_tab", settings.lastSettingsTabName)
                putString("last_history_view", settings.lastHistoryViewModeName)

                // BLE センサー設定
                putString("ble_hr_address", settings.bleHeartRateDeviceAddress)
                putString("ble_hr_name", settings.bleHeartRateDeviceName)
                putString("ble_hr_registered", settings.registeredBleHrDevices.joinToString(","))
                putString("ble_hr_preferred", settings.preferredBleHrAddress)
                putBoolean("ble_hr_enabled", settings.isBleHeartRateEnabled)
                putBoolean("ble_hr_prefer", settings.preferBleHeartRate)

                apply()
            }
            Log.d("AndroidDependencies", "Settings saved to SharedPreferences")
        }
        // ------------------------------------------

        _healthConnectManager = HealthConnectManager(appContext)
        
        // DatabaseDriverFactory を使用して暗号化対応のドライバーを作成
        val driver = DatabaseDriverFactory(appContext).createDriver()
        val database = PebbleTrackerDatabase(driver)
        val repository = SqlRunRepository(database)

        val bleScanner = AndroidBleScanner(appContext)
        val bleHeartRateManager = AndroidBleHeartRateManager(appContext)

        val engine = RunTrackerEngine(
            locationTracker = AndroidLocationTracker(appContext),
            runRepository = repository,
            pebbleMessenger = AndroidPebbleMessenger(appContext, settings),
            appSettings = settings,
            bleHrManager = bleHeartRateManager,
            scope = MainScope()
        )
        
        KmpDependencies.setup(repository, engine, settings, bleScanner, bleHeartRateManager)
        
        // 起動時に保存済みコースの有効ルートを復元
        val activePlanned = settings.savedGpxCourses.toActivePlannedPoints()
        if (activePlanned.isNotEmpty()) {
            engine.pebbleMessenger?.setPlannedCourse(activePlanned)
        }

        isInitialized = true
    }
}
