package n1987haaa.trackerkmpforpebble

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TrackerKMPforPebble",
    ) {
        App()
    }
}