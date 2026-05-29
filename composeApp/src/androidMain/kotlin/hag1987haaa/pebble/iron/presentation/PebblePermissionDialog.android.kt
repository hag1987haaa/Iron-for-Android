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
        
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.pebble_permission_title)) },
            text = { Text(stringResource(Res.string.pebble_permission_text)) },
            confirmButton = {
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
                    Text(stringResource(Res.string.pebble_permission_install))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(Res.string.history_delete_cancel))
                }
            }
        )
    }
}
