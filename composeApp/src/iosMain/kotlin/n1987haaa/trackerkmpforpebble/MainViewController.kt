package n1987haaa.trackerkmpforpebble

import androidx.compose.ui.window.ComposeUIViewController
import n1987haaa.trackerkmpforpebble.presentation.AppActions

fun MainViewController() = ComposeUIViewController {
    App(actions = object : AppActions {
        override fun startTracking() {
            println("iOS Start Tracking (Not implemented)")
        }
        override fun stopTracking() {
            println("iOS Stop Tracking (Not implemented)")
        }
    })
}