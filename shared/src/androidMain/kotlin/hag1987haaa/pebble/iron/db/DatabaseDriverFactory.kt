package hag1987haaa.pebble.iron.db

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.util.UUID

class DatabaseDriverFactory(private val context: Context) {
    fun createDriver(): SqlDriver {
        // ネイティブライブラリを明示的にロード（UnsatisfiedLinkError 対策）
        try {
            System.loadLibrary("sqlcipher")
        } catch (t: Throwable) {
            android.util.Log.e("DatabaseDriverFactory", "Failed to load sqlcipher library", t)
        }

        // 古いデータベースファイルのお掃除
        cleanupOldDatabases()

        val passphrase = getOrCreatePassphrase()
        val factory = SupportOpenHelperFactory(passphrase.toByteArray())

        return AndroidSqliteDriver(
            schema = PebbleTrackerDatabase.Schema,
            context = context,
            name = "iron.db", // 決定版の名称
            factory = factory
        )
    }

    /**
     * これまで開発中に使用した古いデータベースファイルを削除し、ストレージをクリーンに保つ
     */
    private fun cleanupOldDatabases() {
        val oldDbNames = listOf(
            "iron_v4.db", "iron_v5.db", "iron_v6.db", 
            "iron_final_secure.db", "iron_final_v1.db", "iron_final_v2.db"
        )
        val dbDir = context.getDatabasePath("dummy").parentFile ?: return
        
        oldDbNames.forEach { name ->
            val dbFile = File(dbDir, name)
            if (dbFile.exists()) {
                dbFile.delete()
                // 関連するジャーナルファイル等もあれば削除を試みる
                File(dbDir, "$name-journal").delete()
                File(dbDir, "$name-shm").delete()
                File(dbDir, "$name-wal").delete()
                android.util.Log.i("DatabaseDriverFactory", "Old database file deleted: $name")
            }
        }
    }

    private fun getOrCreatePassphrase(): String {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val fileName = "iron_secure_prefs"
        
        val prefs = try {
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("DatabaseDriverFactory", "EncryptedSharedPreferences corruption detected. Resetting...", e)
            // キーの不整合や破損が起きた場合、ファイルを削除して作り直す
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply()
            
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        var key = prefs.getString("db_passphrase", null)
        if (key == null) {
            key = UUID.randomUUID().toString()
            prefs.edit().putString("db_passphrase", key).apply()
        }
        return key
    }
}
