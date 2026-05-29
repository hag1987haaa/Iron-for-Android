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
    private var showBatteryOptimizationDialog by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult<Array<String>, Map<String, Boolean>>(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> 
        checkAndRequestHealthPermissionsOnboarding()
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
                        Log.i("MainActivity", "Imported ${runs.size} workouts successfully.")
                    }
                } catch (_: Exception) {
                    Log.e("MainActivity", "Import failed")
                }
            }
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
                val stats = RunState.currentStats.value
                lifecycleScope.launch {
                    try {
                        val settings = KmpDependencies.appSettings
                        val avgHr = if (stats.heartRates.isNotEmpty()) stats.heartRates.average() else null
                        val maxHr = if (stats.heartRates.isNotEmpty()) stats.heartRates.maxOrNull()?.toDouble() else null

                        val calories = HealthUtils.calculateCalories(
                            type = stats.activityType,
                            weightKg = settings.userWeightKg,
                            durationSeconds = stats.totalSeconds,
                            distanceMeters = stats.totalDistanceMeters,
                            elevationGainMeters = stats.totalElevationGain,
                            avgHeartRate = avgHr
                        )

                        val run = RunActivity(
                            startTime = stats.startTime ?: Clock.System.now(),
                            name = stats.name,
                            type = stats.activityType,
                            endTime = Clock.System.now(),
                            distanceMeters = stats.totalDistanceMeters,
                            durationSeconds = stats.totalSeconds,
                            calories = calories,
                            steps = stats.steps,
                            avgHeartRate = avgHr,
                            maxHeartRate = maxHr,
                            elevationGain = stats.totalElevationGain,
                            route = stats.route
                        )

                        var hcId: String? = null
                        try {
                            hcId = AndroidDependencies.healthConnectManager.writeRunActivity(run)
                            if (hcId != null) {
                                Log.d("MainActivity", "Health Connect synced ID: $hcId")
                            }
                        } catch (_: Exception) {
                            Log.e("MainActivity", "Health Connect sync failed")
                        }

                        KmpDependencies.runRepository.saveRun(run.copy(healthConnectId = hcId))
                        Log.i("MainActivity", "Workout saved successfully.")
                    } catch (e: Exception) {
                        Log.e("MainActivity", "FAILED TO SAVE WORKOUT: ${e.message}")
                    } finally {
                        sendCommand("SAVE_TO_RESULT")
                    }
                }
            }

            override fun discardTracking() = sendCommand("STOP")
            override fun resetTracking() = sendCommand("RESET")

            override fun syncWithHealthConnect(run: RunActivity, onComplete: (Boolean) -> Unit) {
                lifecycleScope.launch {
                    try {
                        val manager = AndroidDependencies.healthConnectManager
                        run.healthConnectId?.let { oldId ->
                            try {
                                manager.deleteRunActivity(oldId)
                                Log.d("MainActivity", "Old HC record deleted: $oldId")
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
                    run?.healthConnectId?.let { hcId ->
                        try {
                            AndroidDependencies.healthConnectManager.deleteRunActivity(hcId)
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

            override fun shareGpx(run: RunActivity) {
                lifecycleScope.launch {
                    try {
                        val gpxContent = GpxExporter.export(run)
                        val localTime = run.startTime.toLocalDateTime(TimeZone.currentSystemDefault())
                        val dateStr = "${localTime.year}${localTime.monthNumber.toString().padStart(2, '0')}${localTime.dayOfMonth.toString().padStart(2, '0')}"
                        val timeStr = "${localTime.hour.toString().padStart(2, '0')}${localTime.minute.toString().padStart(2, '0')}${localTime.second.toString().padStart(2, '0')}"
                        val rawName = run.name ?: run.type.name
                        val fileName = "${rawName.replace(" ", "_")}_${dateStr}_${timeStr}.gpx"
                        
                        val cacheFile = File(cacheDir, fileName)
                        cacheFile.writeText(gpxContent)
                        
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity,
                            "${applicationContext.packageName}.fileprovider",
                            cacheFile
                        )
                        
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/gpx+xml"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(intent, getString(R.string.share_gpx_chooser_title)))
                    } catch (_: Exception) {
                        Log.e("MainActivity", "GPX export failed")
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
                        startActivity(Intent.createChooser(intent, "ワークアウトデータをエクスポート"))
                    } catch (_: Exception) {
                        Log.e("MainActivity", "Export failed")
                    }
                }
            }

            override fun importData() {
                importLauncher.launch("application/json")
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
                                    checkAndRequestHealthPermissionsOnboarding()
                                }
                                checkBatteryOptimization()
                            }) {
                                Text("OK")
                            }
                        }
                    )
                }

                if (showBatteryOptimizationDialog) {
                    AlertDialog(
                        onDismissRequest = { showBatteryOptimizationDialog = false },
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
                            TextButton(onClick = { showBatteryOptimizationDialog = false }) {
                                Text(stringResource(Res.string.history_delete_cancel))
                            }
                        }
                    )
                }
            }
        }
        
        // アプリ起動時の初期権限チェック
        checkAndRequestPermissions()
        checkBatteryOptimization()
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

    private fun checkAndRequestPermissions() {
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
            // 全権限がある場合はヘルスコネクトのオンボーディングを確認
            checkAndRequestHealthPermissionsOnboarding()
        }
    }

    private fun checkAndRequestHealthPermissionsOnboarding() {
        val settings = KmpDependencies.appSettings
        if (settings.hasAskedHealthConnectOnboarding) return

        val manager = AndroidDependencies.healthConnectManager
        if (!manager.isSdkAvailable()) return

        lifecycleScope.launch {
            try {
                if (!manager.hasAllPermissions()) {
                    healthPermissionLauncher.launch(manager.permissions)
                }
                settings.hasAskedHealthConnectOnboarding = true
                settings.save()
            } catch (e: Exception) {
                Log.e("MainActivity", "Health onboarding failed: ${e.message}")
            }
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            if (!showLocationDisclosure) {
                showBatteryOptimizationDialog = true
            }
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
        override fun shareGpx(run: RunActivity) {}
        override fun exportData() {}
        override fun importData() {}
    }
    App(actions)
}
