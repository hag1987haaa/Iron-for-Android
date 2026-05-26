package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable

/**
 * 各プラットフォームで実装されるダイアログの合言葉
 */
@Composable
expect fun PlatformPebblePermissionDialog(
    show: Boolean,
    onDismiss: () -> Unit
)
