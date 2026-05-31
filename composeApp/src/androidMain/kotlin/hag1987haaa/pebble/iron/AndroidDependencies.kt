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
        settings.isMusicControlEnabled = prefs.getBoolean("music_enabled", true)
        settings.isTouchControlEnabled = prefs.getBoolean("touch_enabled", false)
        settings.isAutomationEnabled = prefs.getBoolean("auto_enabled", false)
        settings.isCommand50Enabled = prefs.getBoolean("cmd50_enabled", true)
        settings.isCommand51Enabled = prefs.getBoolean("cmd51_enabled", true)
        settings.isCommand52Enabled = prefs.getBoolean("cmd52_enabled", true)
        settings.isPrivacyMapModeEnabled = prefs.getBoolean("privacy_map_enabled", false)
        settings.userWeightKg = prefs.getFloat("user_weight", 70.0f)
        settings.hasAskedHealthConnectOnboarding = prefs.getBoolean("hc_onboarding_asked", false)
        
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
                putBoolean("auto_enabled", settings.isAutomationEnabled)
                putBoolean("cmd50_enabled", settings.isCommand50Enabled)
                putBoolean("cmd51_enabled", settings.isCommand51Enabled)
                putBoolean("cmd52_enabled", settings.isCommand52Enabled)
                putBoolean("privacy_map_enabled", settings.isPrivacyMapModeEnabled)
                putFloat("user_weight", settings.userWeightKg)
                putBoolean("hc_onboarding_asked", settings.hasAskedHealthConnectOnboarding)
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
