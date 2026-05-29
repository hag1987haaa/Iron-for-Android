package hag1987haaa.pebble.iron.presentation

import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import hag1987haaa.pebble.iron.*
import org.jetbrains.compose.resources.stringResource

class AndroidPebblePermissionDialogProvider : PebblePermissionDialogProvider {
    @Composable
    override fun Show(show: Boolean, onDismiss: () -> Unit) {
        if (!show) return
        
        val context = LocalContext.current
        val packageManager = context.packageManager
        
        // 管理アプリのパッケージ名リスト
        val providers = listOf(
            "io.rebble.cobble" to "Cobble",
            "com.getpebble.android" to "Pebble (Official)",
            "coredevices.coreapp" to "Coreapp"
        )
        
        // インストール済みのアプリを探す
        val installedProvider = providers.firstOrNull { (pkg, _) ->
            try {
                packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { 
                Text(if (installedProvider != null) "管理アプリを確認しました" else "管理アプリが見つかりません")
            },
            text = { 
                if (installedProvider != null) {
                    Text("${installedProvider.second} がインストールされています。Pebble との通信準備は整っています。")
                } else {
                    Text("Pebble と通信するには、管理アプリ（Cobble 推奨）が必要です。Play ストアからインストールしますか？")
                }
            },
            confirmButton = {
                if (installedProvider != null) {
                    Button(onClick = onDismiss) { Text("OK") }
                } else {
                    Button(onClick = {
                        onDismiss()
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = "market://details?id=io.rebble.cobble".toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                data = "https://play.google.com/store/apps/details?id=io.rebble.cobble".toUri()
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }) {
                        Text("Play ストアへ")
                    }
                }
            },
            dismissButton = {
                if (installedProvider == null) {
                    TextButton(onClick = onDismiss) { Text("キャンセル") }
                }
            }
        )
    }
}
