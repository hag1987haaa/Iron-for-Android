package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable

@Composable
expect fun PlatformPebblePermissionDialog(
    show: Boolean,
    onDismiss: () -> Unit
)
