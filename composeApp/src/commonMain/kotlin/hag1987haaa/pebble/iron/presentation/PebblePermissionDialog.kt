package hag1987haaa.pebble.iron.platform

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformPebblePermissionDialog(
    show: Boolean,
    onDismiss: () -> Unit
)
