package hag1987haaa.pebble.iron

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.presentation.AndroidPebblePermissionDialogProvider
import hag1987haaa.pebble.iron.presentation.AppActions
import hag1987haaa.pebble.iron.presentation.LocalPebblePermissionDialog
import hag1987haaa.pebble.iron.presentation.PebblePermissionDialogProvider
import hag1987haaa.pebble.iron.service.TrackingService
import hag1987haaa.pebble.iron.util.AutoExporter
import hag1987haaa.pebble.iron.util.GpxExporter
import hag1987haaa.pebble.iron.util.HealthUtils
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import java.io.File
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController

class MainActivity : ComponentActivity() {

    private var showLocationDisclosure by mutableStateOf(false)
    private var showBackgroundLocationRationale by mutableStateOf(false)
    private var showBatteryOptimizationDialog by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult<Array<String>, Map<String, Boolean>>(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 権限リクエスト後に、次のステップへ進む
        startPermissionChain()
    }

    private val healthPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d("MainActivity", "Health Connect granted: $granted")
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                try {
                    val content = contentResolver.openInputStream(it)?.bufferedReader()?.use { br -> br.readText() }
                    if (content != null) {
                        val runs = Json.decodeFromString<List<RunActivity>>(content)
                        KmpDependencies.runRepository.importRuns(runs)
                        Toast.makeText(this@MainActivity, getString(R.string.imported_success, runs.size), Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val tcxFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            KmpDependencies.appSettings.autoExportTcxUri = it.toString()
            KmpDependencies.appSettings.save()
        }
    }

    private val gpxFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            KmpDependencies.appSettings.autoExportGpxUri = it.toString()
            KmpDependencies.appSettings.save()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AndroidDependencies.initialize(this)

        val actions = object : AppActions {
            override fun setActivityType(type: ActivityType) {
                KmpDependencies.trackerEngine.setActivityType(type)
            }

            private fun sendCommand(action: String) {
                Log.d("MainActivity", "Sending command: $action")
                try {
                    val intent = Intent(this@MainActivity, TrackingService::class.java).apply { 
                        this.action = action 
                    }
                    startForegroundService(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to send command: ${e.message}")
                }
            }

            override fun prepareTracking() = sendCommand("PREPARE")
            override fun startTracking() = sendCommand("START")
            override fun pauseTracking() = sendCommand("PAUSE")
            override fun resumeTracking() = sendCommand("RESUME")
            override fun finishTracking() = sendCommand("FINISH")
            
            override fun saveTracking() {
                // UI側での保存処理を簡略化。
                // 実際の保存処理（DB/Health Connect）は TrackingService 側で行うことで二重保存を防止する。
                sendCommand("SAVE_TO_RESULT")
            }

            override fun discardTracking() = sendCommand("STOP")
            override fun resetTracking() = sendCommand("RESET")

            override fun syncWithHealthConnect(run: RunActivity, onComplete: (Boolean) -> Unit) {
                lifecycleScope.launch {
                    try {
                        val manager = AndroidDependencies.healthConnectManager
                        run.healthConnectId?.let {
                            try {
                                manager.deleteRunActivity(run)
                                Log.d("MainActivity", "Old HC record deleted for run: ${run.id}")
                            } catch (_: Exception) {}
                        }

                        val hcId = manager.writeRunActivity(run)
                        if (hcId != null) {
                            val runId = run.id
                            if (runId != 0L) {
                                KmpDependencies.runRepository.updateHealthConnectId(runId, hcId)
                            }
                            onComplete(true)
                        } else {
                            onComplete(false)
                        }
                    } catch (_: Exception) {
                        Log.e("MainActivity", "Manual HC sync failed")
                        onComplete(false)
                    }
                }
            }

            override fun deleteRunRecord(id: Long) {
                lifecycleScope.launch {
                    val run = KmpDependencies.runRepository.getRunDetails(id)
                    if (run != null) {
                        try {
                            AndroidDependencies.healthConnectManager.deleteRunActivity(run)
                        } catch (_: Exception) {}
                    }
                    KmpDependencies.runRepository.deleteRun(id)
                }
            }

            override fun requestHealthPermissions() {
                val manager = AndroidDependencies.healthConnectManager
                val sdkStatus = HealthConnectClient.getSdkStatus(this@MainActivity)
                
                when (sdkStatus) {
                    HealthConnectClient.SDK_AVAILABLE -> {
                        lifecycleScope.launch {
                            if (manager.hasAllPermissions()) {
                                openHealthConnectSettings()
                            } else {
                                try {
                                    healthPermissionLauncher.launch(manager.permissions)
                                } catch (_: Exception) {
                                    openHealthConnectSettings()
                                }
                            }
                        }
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        val providerPackageName = "com.google.android.apps.healthdata"
                        val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setPackage("com.android.vending")
                                data = uriString.toUri()
                                putExtra("overlay", true)
                                putExtra("callerId", packageName)
                            })
                        } catch (_: Exception) {
                            startActivity(Intent(Intent.ACTION_VIEW, uriString.toUri()))
                        }
                    }
                    else -> {
                        Log.w("MainActivity", "Health Connect SDK not available")
                    }
                }
            }

            private fun openHealthConnectSettings() {
                try {
                    val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to open Health Connect settings: ${e.message}")
                }
            }

            override fun shareRunData(run: RunActivity, format: String) {
                lifecycleScope.launch {
                    try {
                        val content = if (format == "tcx") {
                            hag1987haaa.pebble.iron.util.TcxExporter.export(run)
                        } else {
                            hag1987haaa.pebble.iron.util.GpxExporter.export(run)
                        }

                        val localTime = run.startTime.toLocalDateTime(TimeZone.currentSystemDefault())
                        val dateStr = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}"
                        val timeStr = "${localTime.hour.toString().padStart(2, '0')}${localTime.minute.toString().padStart(2, '0')}${localTime.second.toString().padStart(2, '0')}"
                        val rawName = run.name ?: run.type.name
                        val extension = if (format == "tcx") "tcx" else "gpx"
                        val fileName = "${rawName.replace(" ", "_")}_${dateStr}_${timeStr}.$extension"
                        
                        val cacheFile = File(cacheDir, fileName)
                        cacheFile.writeText(content)
                        
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${applicationContext.packageName}.fileprovider",
                            cacheFile
                        )
                        
                        val mimeType = if (format == "tcx") "application/vnd.garmin.tcx+xml" else "application/gpx+xml"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_gpx_chooser_title)))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, getString(R.string.file_export_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun exportData() {
                lifecycleScope.launch {
                    try {
                        val allRuns = KmpDependencies.runRepository.getAllRunsWithDetails()
                        val jsonContent = Json.encodeToString(allRuns)
                        val fileName = "iron_backup_${System.currentTimeMillis()}.json"
                        val cacheFile = File(cacheDir, fileName)
                        cacheFile.writeText(jsonContent)
                        
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${applicationContext.packageName}.fileprovider",
                            cacheFile
                        )
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.history_export_chooser_title)))
                    } catch (_: Exception) {
                        Toast.makeText(this@MainActivity, getString(R.string.file_export_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun importData() {
                importLauncher.launch("application/json")
            }

            override fun requestOverlayPermission() {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }

            override fun selectAutoExportFolder(format: String) {
                if (format == "tcx") {
                    tcxFolderLauncher.launch(null)
                } else if (format == "gpx") {
                    gpxFolderLauncher.launch(null)
                }
            }

            override fun openAutoExportFolder(format: String) {
                val uriString = if (format == "tcx") KmpDependencies.appSettings.autoExportTcxUri else KmpDependencies.appSettings.autoExportGpxUri
                uriString?.let {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(it), "resource/folder")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        // 万が一フォルダとして開けない場合は、汎用的なVIEWを試みる
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                        } else {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it)))
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Failed to open folder", e)
                        Toast.makeText(this@MainActivity, "Could not open folder", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun triggerAutoExport(run: RunActivity) {
                lifecycleScope.launch {
                    AutoExporter.execute(applicationContext, run)
                }
            }
        }

        setContent {
            CompositionLocalProvider(
                LocalPebblePermissionDialog provides (AndroidPebblePermissionDialogProvider() as PebblePermissionDialogProvider)
            ) {
                App(actions)

                if (showLocationDisclosure) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text(stringResource(Res.string.location_disclosure_title)) },
                        text = { Text(stringResource(Res.string.location_disclosure_text)) },
                        confirmButton = {
                            Button(onClick = {
                                showLocationDisclosure = false
                                val missing = getRequiredPermissions().filter {
                                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                                }
                                if (missing.isNotEmpty()) {
                                    requestPermissionLauncher.launch(missing.toTypedArray())
                                } else {
                                    // 位置情報が既にあった場合は次のステップへ
                                    startPermissionChain()
                                }
                            }) {
                                Text(stringResource(Res.string.ok_button))
                            }
                        }
                    )
                }

                if (showBackgroundLocationRationale) {
                    AlertDialog(
                        onDismissRequest = { 
                            showBackgroundLocationRationale = false
                            checkBatteryOptimizationStep()
                        },
                        title = { Text(stringResource(Res.string.location_background_rationale_title)) },
                        text = { Text(stringResource(Res.string.location_background_rationale_text)) },
                        confirmButton = {
                            Button(onClick = {
                                showBackgroundLocationRationale = false
                                openAppDetailSettings()
                                // 設定画面から戻った時にチェックされるようにする（onResume等でのチェックは別途検討）
                            }) {
                                Text(stringResource(Res.string.location_background_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                showBackgroundLocationRationale = false
                                checkBatteryOptimizationStep() 
                            }) {
                                Text(stringResource(Res.string.history_delete_cancel))
                            }
                        }
                    )
                }

                if (showBatteryOptimizationDialog) {
                    AlertDialog(
                        onDismissRequest = { 
                            showBatteryOptimizationDialog = false
                        },
                        title = { Text(stringResource(Res.string.battery_optimization_title)) },
                        text = { Text(stringResource(Res.string.battery_optimization_text)) },
                        confirmButton = {
                            Button(onClick = {
                                showBatteryOptimizationDialog = false
                                openBatteryOptimizationSettings()
                            }) {
                                Text(stringResource(Res.string.battery_optimization_button))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { 
                                showBatteryOptimizationDialog = false
                            }) {
                                Text(stringResource(Res.string.history_delete_cancel))
                            }
                        }
                    )
                }
            }
        }
        
        // アプリ起動時の初期権限チェック（シーケンス開始）
        startPermissionChain()
    }

    override fun onResume() {
        super.onResume()
        // 設定画面から戻ってきた時などに、権限チェックを再開する
        // ただし、既にダイアログが表示されている場合は二重に実行しない
        if (!showLocationDisclosure && !showBackgroundLocationRationale && !showBatteryOptimizationDialog) {
            startPermissionChain()
        }
    }

    private fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            permissions.add(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE)
        }
        return permissions
    }

    private fun startPermissionChain() {
        val missingPermissions = getRequiredPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            if (missingPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
                missingPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showLocationDisclosure = true
            } else {
                requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            }
        } else {
            // 前景権限が揃ったら次へ
            checkBackgroundLocationStep()
        }
    }

    private fun checkBackgroundLocationStep() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                showBackgroundLocationRationale = true
            } else {
                showBackgroundLocationRationale = false
                checkBatteryOptimizationStep()
            }
        } else {
            checkBatteryOptimizationStep()
        }
    }

    private fun openAppDetailSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun checkBatteryOptimizationStep() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            showBatteryOptimizationDialog = true
        } else {
            showBatteryOptimizationDialog = false
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatteryOptimizationSettings() {
        // 1. まずは自アプリを直接指定して除外を求めるダイアログを試みる
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        
        try {
            Log.d("MainActivity", "Attempting to open direct battery optimization request")
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Direct battery settings failed: ${e.message}")
            // 2. 失敗した場合は、ユーザーに手動でアプリを探してもらう設定一覧画面を開く
            try {
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Log.e("MainActivity", "Total failure to open battery settings: ${e2.message}")
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val actions = object : AppActions {
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
    }
    App(actions)
}
