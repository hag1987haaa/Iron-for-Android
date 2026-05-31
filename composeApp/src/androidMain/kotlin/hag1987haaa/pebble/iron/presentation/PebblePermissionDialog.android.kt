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
            "coredevices.coreapp" to "Coreapp",
        )

        // インストール済みのアプリを探す
        val installedProvider = providers.firstOrNull { (pkg, _) ->
            try {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                val titleRes = if (installedProvider != null) {
                    Res.string.pebble_provider_detected_title
                } else {
                    Res.string.pebble_provider_missing_title
                }
                Text(stringResource(titleRes))
            },
            text = {
                if (installedProvider != null) {
                    Text(
                        stringResource(Res.string.pebble_provider_detected_text)
                            .replace("%s", installedProvider.second),
                    )
                } else {
                    Text(stringResource(Res.string.pebble_provider_missing_text))
                }
            },
            confirmButton = {
                if (installedProvider != null) {
                    Button(onClick = onDismiss) {
                        Text(stringResource(Res.string.pebble_provider_confirm_ok))
                    }
                } else {
                    Button(
                        onClick = {
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
                        },
                    ) {
                        Text(stringResource(Res.string.pebble_permission_install))
                    }
                }
            },
            dismissButton = {
                if (installedProvider == null) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.pebble_provider_confirm_cancel))
                    }
                }
            },
        )
    }
}
