package hag1987haaa.pebble.iron

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import org.jetbrains.compose.resources.stringResource
import hag1987haaa.pebble.iron.*
import hag1987haaa.pebble.iron.presentation.AppActions
import hag1987haaa.pebble.iron.service.TrackingService
import hag1987haaa.pebble.iron.pebble.AndroidPebbleMessenger
import hag1987haaa.pebble.iron.domain.model.ActivityType
import hag1987haaa.pebble.iron.domain.model.RunActivity
import hag1987haaa.pebble.iron.domain.tracker.RunState
import hag1987haaa.pebble.iron.util.HealthUtils
import hag1987haaa.pebble.iron.util.GpxExporter
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.io.File
import androidx.core.content.FileProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class MainActivity : ComponentActivity() {

    private var showLocationDisclosure by mutableStateOf(false)
    private var showBatteryOptimizationDialog by mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult<Array<String>, Map<String, Boolean>>(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    private val healthPermissionLauncher = registerForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        android.util.Log.d("MainActivity", "Health Connect granted: $granted")
        if (granted.isEmpty()) {
            android.util.Log.w("MainActivity", "No permissions granted.")
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                try {
                    val content = contentResolver.openInputStream(it)?.bufferedReader()?.use { it.readText() }
                    if (content != null) {
                        val runs = Json.decodeFromString<List<RunActivity>>(content)
                        KmpDependencies.runRepository.importRuns(runs)
                        android.util.Log.i("MainActivity", "Imported ${runs.size} workouts successfully.")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Import failed", e)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        AndroidDependencies.initialize(this)
        checkAndRequestPermissions()

        val actions = object : AppActions {
            override fun setActivityType(type: ActivityType) {
                KmpDependencies.trackerEngine.setActivityType(type)
            }

            private fun sendCommand(action: String) {
                android.util.Log.d("MainActivity", "Sending command: $action")
                try {
                    val intent = Intent(this@MainActivity, TrackingService::class.java).apply { 
                        this.action = action 
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to send command", e)
                }
            }

            override fun prepareTracking() = sendCommand("PREPARE")
            override fun startTracking() = sendCommand("START")
            override fun pauseTracking() = sendCommand("PAUSE")
            override fun resumeTracking() = sendCommand("RESUME")
            override fun finishTracking() = sendCommand("FINISH")
            
            override fun saveTracking() {
                val stats = RunState.currentStats.value // 非同期処理に入る前に現在のデータを確実にキャプチャ
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

                        // Health Connect 同期を常に試行 (権限がなければ HealthConnectManager 側でスキップされる)
                        var hcId: String? = null
                        try {
                            hcId = AndroidDependencies.healthConnectManager.writeRunActivity(run)
                            if (hcId != null) {
                                android.util.Log.d("MainActivity", "Health Connect synced ID: $hcId")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Health Connect sync failed", e)
                        }

                        // データベース保存
                        KmpDependencies.runRepository.saveRun(run.copy(healthConnectId = hcId))
                        android.util.Log.i("MainActivity", "Workout saved successfully.")
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "FAILED TO SAVE WORKOUT", e)
                    } finally {
                        // 保存後はリザルト表示モード（RESULT）へ移行させ、データを保持する
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
                        // 1. もしすでに同期済みIDがあるなら、古いデータを削除（上書きの準備）
                        run.healthConnectId?.let { oldId ->
                            try {
                                manager.deleteRunActivity(oldId)
                                android.util.Log.d("MainActivity", "Old HC record deleted: $oldId")
                            } catch (e: Exception) {
                                android.util.Log.w("MainActivity", "Failed to delete old HC record, continuing...")
                            }
                        }

                        // 2. 最新のデータ（再計算後のカロリー等）を書き込む
                        val hcId = manager.writeRunActivity(run)
                        if (hcId != null) {
                            // 重要：同期に成功したら、既存のレコードの ID を更新する（新規保存しない！）
                            val runId = run.id ?: 0L
                            if (runId != 0L) {
                                KmpDependencies.runRepository.updateHealthConnectId(runId, hcId)
                            }
                            onComplete(true)
                        } else {
                            onComplete(false)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Manual HC sync failed", e)
                        onComplete(false)
                    }
                }
            }

            override fun deleteRunRecord(id: Long) {
                lifecycleScope.launch {
                    val run = KmpDependencies.runRepository.getRunDetails(id)
                    val hcId = run?.healthConnectId
                    if (hcId != null) {
                        // 同期済みデータがあれば Health Connect 側も削除を試みる
                        try {
                            AndroidDependencies.healthConnectManager.deleteRunActivity(hcId)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Failed to delete from HC", e)
                        }
                    }
                    KmpDependencies.runRepository.deleteRun(id)
                }
            }

            override fun requestHealthPermissions() {
                val manager = AndroidDependencies.healthConnectManager
                val sdkStatus = androidx.health.connect.client.HealthConnectClient.getSdkStatus(this@MainActivity)
                
                when (sdkStatus) {
                    androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE -> {
                        lifecycleScope.launch {
                            if (manager.hasAllPermissions()) {
                                // すべて許可済みの場合は、管理画面を開いて確認してもらう
                                openHealthConnectSettings()
                            } else {
                                // 未許可がある場合はリクエストを試みる
                                try {
                                    healthPermissionLauncher.launch(manager.permissions)
                                } catch (e: Exception) {
                                    // リクエストがブロックされる場合は設定画面を直接開く
                                    openHealthConnectSettings()
                                }
                            }
                        }
                    }
                    androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        // (既存のアップデート処理)
                        val providerPackageName = "com.google.android.apps.healthdata"
                        val uriString = "market://details?id=$providerPackageName&url=healthconnect%3A%2F%2Fonboarding"
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setPackage("com.android.vending")
                                data = android.net.Uri.parse(uriString)
                                putExtra("overlay", true)
                                putExtra("callerId", packageName)
                            })
                        } catch (e: Exception) {
                            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uriString)))
                        }
                    }
                    else -> {
                        android.util.Log.w("MainActivity", "Health Connect SDK not available")
                    }
                }
            }

            private fun openHealthConnectSettings() {
                try {
                    val intent = Intent(androidx.health.connect.client.HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to open Health Connect settings", e)
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
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "GPX export failed", e)
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
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Export failed", e)
                    }
                }
            }

            override fun importData() {
                importLauncher.launch("application/json")
            }

            override fun buyCoffee() {
                val billing = AndroidDependencies.billingManager
                val products = billing.products.value
                if (products.isNotEmpty()) {
                    // とりあえず最初のアイテム（tip_coffeeなど）を起動
                    billing.launchPurchase(this@MainActivity, products.first())
                }
            }
        }

        setContent {
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
                            }
                            // 位置情報の説明が終わった後に、バッテリー最適化のチェックを再実行
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
            // 位置情報が含まれる場合はディスクロージャーを表示
            if (missingPermissions.contains(Manifest.permission.ACCESS_FINE_LOCATION) ||
                missingPermissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showLocationDisclosure = true
            } else {
                requestPermissionLauncher.launch(missingPermissions.toTypedArray())
            }
        } else {
            checkBackgroundLocationPermission()
        }
    }

    private fun checkBatteryOptimization() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            // 他の重要なダイアログ（位置情報の説明）が表示されていない場合のみ表示
            if (!showLocationDisclosure) {
                showBatteryOptimizationDialog = true
            }
        }
    }

    private fun openBatteryOptimizationSettings() {
        // 直接設定ダイアログを開く試み
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:$packageName")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to open direct battery settings, falling back to list.", e)
            // 失敗した場合は、ユーザーに手動でアプリを探してもらう設定一覧画面を開く
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            try {
                startActivity(fallbackIntent)
            } catch (e2: Exception) {
                android.util.Log.e("MainActivity", "Total failure to open battery settings", e2)
            }
        }
    }

    private fun checkBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                android.util.Log.i("MainActivity", "Requesting Background Location Permission")
                requestPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
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
        override fun buyCoffee() {}
    }
    App(actions)
}
