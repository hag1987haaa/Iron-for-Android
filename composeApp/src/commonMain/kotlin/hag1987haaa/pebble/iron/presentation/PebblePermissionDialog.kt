package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Interface to show platform-specific dialog
 */
interface PebblePermissionDialogProvider {
    @Composable
    fun Show(show: Boolean, onDismiss: () -> Unit)
}

/**
 * Default provider (does nothing)
 */
private val DefaultProvider = object : PebblePermissionDialogProvider {
    @Composable
    override fun Show(show: Boolean, onDismiss: () -> Unit) {
        // No-op
    }
}

/**
 * CompositionLocal to provide the dialog implementation
 */
val LocalPebblePermissionDialog = staticCompositionLocalOf<PebblePermissionDialogProvider> {
    object : PebblePermissionDialogProvider {
        @Composable
        override fun Show(show: Boolean, onDismiss: () -> Unit) {
            // No-op
        }
    }
}
