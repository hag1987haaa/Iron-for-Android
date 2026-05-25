package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.rebble.pebblekit2.client.DefaultPebbleAndroidAppPicker
import io.rebble.pebblekit2.client.ui.PebbleAppPermissionDialog
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.permission_rationale
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun PlatformPebblePermissionDialog(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (show) {
        val context = LocalContext.current
        val picker = DefaultPebbleAndroidAppPicker.getInstance(context)
        
        PebbleAppPermissionDialog(
            pebbleAndroidAppPicker = picker,
            onDismiss = onDismiss,
            rationaleText = {
                Text(
                    text = stringResource(Res.string.permission_rationale),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        )
    }
}
