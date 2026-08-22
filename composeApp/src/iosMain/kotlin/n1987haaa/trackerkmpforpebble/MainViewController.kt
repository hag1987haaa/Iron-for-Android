package n1987haaa.trackerkmpforpebble

import androidx.compose.ui.window.ComposeUIViewController
import hag1987haaa.pebble.iron.App
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.presentation.AppActions

fun MainViewController() = ComposeUIViewController {
    App(actions = object : AppActions {
        override fun setActivityType(type: ActivityType) {}
        override fun prepareTracking() {}
        override fun startTracking() {}
        override fun pauseTracking() {}
        override fun resumeTracking() {}
        override fun finishTracking() {}
        override fun saveTracking() {}
        override fun discardTracking() {}
        override fun resetTracking() {}
        override fun syncWithHealthConnect(run: RunActivity, onComplete: (Boolean) -> Unit) {}
        override fun deleteRunRecord(id: Long) {}
        override fun requestHealthPermissions() {}
        override fun shareRunData(run: RunActivity, format: String) {}
        override fun exportData() {}
        override fun importData() {}
        override fun requestOverlayPermission() {}
        override fun selectAutoExportFolder(format: String) {}
        override fun openAutoExportFolder(format: String) {}
        override fun triggerAutoExport(run: RunActivity) {}
        override fun requestSensorPermissions(onResult: (Boolean) -> Unit) {}
        override fun copyToClipboard(text: String, label: String) {}
    })
}
