package hag1987haaa.pebble.iron

import android.app.Application
import org.osmdroid.config.Configuration
import java.io.File
import android.preference.PreferenceManager

class PebbleTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // osmdroid お作法: 設定のロード
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = "hag1987haaa.pebble.iron/1.0"

        // Android 10+ 向けのタイルキャッシュ設定
        val osmBasePath = File(cacheDir, "osmdroid")
        osmBasePath.mkdirs()
        Configuration.getInstance().osmdroidBasePath = osmBasePath
        val osmTileCache = File(osmBasePath, "tiles")
        osmTileCache.mkdirs()
        Configuration.getInstance().osmdroidTileCache = osmTileCache

        // 初期化を AndroidDependencies に統一
        AndroidDependencies.initialize(this)
    }
}
