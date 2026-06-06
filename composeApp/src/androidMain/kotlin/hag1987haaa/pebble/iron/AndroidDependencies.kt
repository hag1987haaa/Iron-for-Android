package hag1987haaa.pebble.iron

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import hag1987haaa.pebble.iron.data.repository.SqlRunRepository
import hag1987haaa.pebble.iron.db.DatabaseDriverFactory
import hag1987haaa.pebble.iron.db.PebbleTrackerDatabase
import hag1987haaa.pebble.iron.domain.settings.AppSettings
import hag1987haaa.pebble.iron.domain.tracker.RunTrackerEngine
import hag1987haaa.pebble.iron.health.HealthConnectManager
import hag1987haaa.pebble.iron.location.AndroidLocationTracker
import hag1987haaa.pebble.iron.pebble.AndroidPebbleMessenger
import kotlinx.coroutines.MainScope

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
        
        val settings = AppSettings()
        
        // --- 設定の読み込み (SharedPreferences) ---
        val prefs = appContext.getSharedPreferences("iron_settings", Context.MODE_PRIVATE)
        settings.isMusicControlEnabled = prefs.getBoolean("music_enabled", false)
        settings.isTouchControlEnabled = prefs.getBoolean("touch_enabled", false)
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
        settings.userWeightKg = prefs.getFloat("user_weight", 70.0f)
        settings.hasAskedHealthConnectOnboarding = prefs.getBoolean("hc_onboarding_asked", false)
        
        // グラフ・通知設定の読み込み
        val graphTypesStr = prefs.getString("graph_types", "0,1,2,3,4,5") ?: "0,1,2,3,4,5"
        settings.enabledGraphTypes = graphTypesStr.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
        settings.notificationDistanceMeters = prefs.getInt("notif_dist", 1000)
        settings.notificationTimeSeconds = prefs.getInt("notif_time", 0)
        settings.isAutoLaunchOnDistanceNotificationEnabled = prefs.getBoolean("auto_launch_dist", false)
        settings.isAutoLaunchOnTimeNotificationEnabled = prefs.getBoolean("auto_launch_time", false)
        
        // Mid Data 設定の読み込み
        val midTypesStr = prefs.getString("mid_types", "0,4,1,5,6") ?: "0,4,1,5,6"
        settings.enabledMidTypes = midTypesStr.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
        settings.isMetric = prefs.getBoolean("is_metric", true)

        // アプリバージョンの取得
        try {
            val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
            settings.appVersion = packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            Log.e("AndroidDependencies", "Failed to get version name", e)
        }

        // 保存用コールバックの登録
        settings.onSettingsChanged = {
            prefs.edit().apply {
                putBoolean("music_enabled", settings.isMusicControlEnabled)
                putBoolean("touch_enabled", settings.isTouchControlEnabled)
                putBoolean("longpress_enabled", settings.isLongPressEnabled)
                putString("longpress_up_mode", settings.upLongPressMode.name)
                putString("longpress_select_mode", settings.selectLongPressMode.name)
                putString("longpress_down_mode", settings.downLongPressMode.name)
                putBoolean("auto_enabled", settings.isAutomationEnabled)
                putBoolean("cmd50_enabled", settings.isCommand50Enabled)
                putBoolean("cmd51_enabled", settings.isCommand51Enabled)
                putBoolean("cmd52_enabled", settings.isCommand52Enabled)
                putBoolean("privacy_map_enabled", settings.isPrivacyMapModeEnabled)
                putFloat("user_weight", settings.userWeightKg)
                putBoolean("hc_onboarding_asked", settings.hasAskedHealthConnectOnboarding)
                
                // グラフ・通知設定の保存
                putString("graph_types", settings.enabledGraphTypes.joinToString(","))
                putInt("notif_dist", settings.notificationDistanceMeters)
                putInt("notif_time", settings.notificationTimeSeconds)
                putBoolean("auto_launch_dist", settings.isAutoLaunchOnDistanceNotificationEnabled)
                putBoolean("auto_launch_time", settings.isAutoLaunchOnTimeNotificationEnabled)
                
                // Mid Data 設定の保存
                putString("mid_types", settings.enabledMidTypes.joinToString(","))
                putBoolean("is_metric", settings.isMetric)

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

        val engine = RunTrackerEngine(
            locationTracker = AndroidLocationTracker(appContext),
            runRepository = repository,
            pebbleMessenger = AndroidPebbleMessenger(appContext),
            appSettings = settings,
            scope = MainScope()
        )
        
        KmpDependencies.setup(repository, engine, settings)
        isInitialized = true
    }
}
