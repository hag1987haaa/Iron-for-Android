package hag1987haaa.pebble.iron.presentation

import androidx.compose.runtime.Composable

@Composable
fun BackPressHandler(enabled: Boolean, onBack: () -> Unit) {
    PlatformBackPressHandler(enabled, onBack)
}

@Composable
expect fun PlatformBackPressHandler(enabled: Boolean, onBack: () -> Unit)
