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
        // ネイティブライブラリを明示的にロード
        try {
            System.loadLibrary("sqlcipher")
        } catch (t: Throwable) {
            android.util.Log.e("DatabaseDriverFactory", "Failed to load sqlcipher library", t)
        }

        cleanupOldDatabases()

        val dbName = "iron.db"
        val passphrase = getOrCreatePassphrase()
        
        // データベースが既に存在する場合、現在のパスフレーズで開けるかテストする
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            try {
                // ドライバと同じ方式でデータベース接続をテスト
                val factory = SupportOpenHelperFactory(passphrase.toByteArray())
                val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(dbName)
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(PebbleTrackerDatabase.Schema.version.toInt()) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                        override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    })
                    .build()
                
                factory.create(config).readableDatabase.close()
            } catch (e: Exception) {
                android.util.Log.e("DatabaseDriverFactory", "Database validation failed. Resetting...", e)
                dbFile.delete()
                File(dbFile.path + "-journal").delete()
                File(dbFile.path + "-shm").delete()
                File(dbFile.path + "-wal").delete()
            }
        }

        val factory = SupportOpenHelperFactory(passphrase.toByteArray())

        return AndroidSqliteDriver(
            schema = PebbleTrackerDatabase.Schema,
            context = context,
            name = dbName,
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
        val dbName = "iron.db"
        
        val prefs = try {
            EncryptedSharedPreferences.create(
                context,
                fileName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("DatabaseDriverFactory", "EncryptedSharedPreferences corruption detected. Resetting database and prefs...", e)
            // キーの不整合や破損が起きた場合、暗号化キーを復旧できないため、
            // 整合性を保つためにデータベースファイルもろとも削除して作り直す
            context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().apply()
            context.getDatabasePath(dbName).delete()
            
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
            // キーが新規作成されるタイミングでは、古い（開けなくなった）データベースが残っている可能性があるため削除
            context.getDatabasePath(dbName).delete()
            key = UUID.randomUUID().toString()
            prefs.edit().putString("db_passphrase", key).apply()
        }
        return key
    }
}
