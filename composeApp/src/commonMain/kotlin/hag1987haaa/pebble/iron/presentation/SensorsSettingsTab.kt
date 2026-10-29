package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.Res
import hag1987haaa.pebble.iron.*
import org.jetbrains.compose.resources.stringResource
import hag1987haaa.pebble.iron.domain.ble.BleDevice
import hag1987haaa.pebble.iron.domain.ble.BleSensorType
import hag1987haaa.pebble.iron.theme.IronColors

@Composable
fun SensorsSettingsTab(settingsViewModel: SettingsViewModel, actions: AppActions) {
    val sensorViewModel: SensorViewModel = viewModel { SensorViewModel() }
    val foundDevices by sensorViewModel.foundDevices.collectAsState()
    val isScanning by sensorViewModel.isScanning.collectAsState()

    // Heart Rate
    val isHrConnected by sensorViewModel.isConnected.collectAsState()
    val isHrDataActive by sensorViewModel.isDataActive.collectAsState()
    val heartRateBpm by sensorViewModel.heartRateBpm.collectAsState()
    val registeredHrDevices by settingsViewModel.registeredBleHrDevices.collectAsState()
    val preferredHrAddress by settingsViewModel.preferredBleHrAddress.collectAsState()
    val isBleHrEnabled by settingsViewModel.isBleHeartRateEnabled.collectAsState()
    val preferBleHr by settingsViewModel.preferBleHeartRate.collectAsState()

    // CSC (Speed & Cadence)
    val isCscConnected by sensorViewModel.isCscConnected.collectAsState()
    val isCscDataActive by sensorViewModel.isCscDataActive.collectAsState()
    val cadenceRpm by sensorViewModel.cadenceRpm.collectAsState()
    val wheelSpeedKmh by sensorViewModel.wheelSpeedKmh.collectAsState()
    val registeredCscDevices by settingsViewModel.registeredBleCscDevices.collectAsState()
    val preferredCscAddress by settingsViewModel.preferredBleCscAddress.collectAsState()
    val isBleCscEnabled by settingsViewModel.isBleCscEnabled.collectAsState()
    val wheelCircumferenceMm by settingsViewModel.wheelCircumferenceMm.collectAsState()

    // Cycling Power
    val isPowerConnected by sensorViewModel.isPowerConnected.collectAsState()
    val isPowerDataActive by sensorViewModel.isPowerDataActive.collectAsState()
    val powerWatts by sensorViewModel.powerWatts.collectAsState()
    val registeredPowerDevices by settingsViewModel.registeredBlePowerDevices.collectAsState()
    val preferredPowerAddress by settingsViewModel.preferredBlePowerAddress.collectAsState()
    val isBlePowerEnabled by settingsViewModel.isBlePowerEnabled.collectAsState()

    var selectedDeviceForRegistration by remember { mutableStateOf<BleDevice?>(null) }
    var showWheelCircDialog by remember { mutableStateOf(false) }

    val registerDeviceWithType: (BleDevice, BleSensorType) -> Unit = { device, type ->
        sensorViewModel.stopScan()
        when (type) {
            BleSensorType.HEART_RATE -> {
                settingsViewModel.updateBleHeartRateEnabled(true)
                settingsViewModel.setBleHeartRateDevice(device.address, device.name)
                sensorViewModel.connectDevice(device.address)
            }
            BleSensorType.CSC -> {
                settingsViewModel.updateBleCscEnabled(true)
                settingsViewModel.setBleCscDevice(device.address, device.name)
                sensorViewModel.connectCsc(device.address)
            }
            BleSensorType.CYCLING_POWER -> {
                settingsViewModel.updateBlePowerEnabled(true)
                settingsViewModel.setBlePowerDevice(device.address, device.name)
                sensorViewModel.connectPower(device.address)
            }
            else -> {}
        }
        selectedDeviceForRegistration = null
    }

    val onPairDevice: (BleDevice) -> Unit = { device ->
        val detectedTypes = buildList {
            if (device.isHeartRate) add(BleSensorType.HEART_RATE)
            if (device.isCsc) add(BleSensorType.CSC)
            if (device.isCyclingPower) add(BleSensorType.CYCLING_POWER)
        }

        if (detectedTypes.size == 1) {
            registerDeviceWithType(device, detectedTypes.first())
        } else {
            selectedDeviceForRegistration = device
        }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // --- 1. センサー有効化トグルカード ---
        item {
            Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Sensor Types", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    // 心拍計
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.MonitorHeart, null, tint = Color(IronColors.HEART_RATE_PINK), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Heart Rate Monitor", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Chest straps, optical bands", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isBleHrEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    actions.requestSensorPermissions { granted ->
                                        if (granted) settingsViewModel.updateBleHeartRateEnabled(true)
                                    }
                                } else {
                                    settingsViewModel.updateBleHeartRateEnabled(false)
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // ケイデンス & スピード (CSC)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.DirectionsBike, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cycling Speed & Cadence", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Crank cadence & wheel speed (RPM, km/h)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isBleCscEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    actions.requestSensorPermissions { granted ->
                                        if (granted) settingsViewModel.updateBleCscEnabled(true)
                                    }
                                } else {
                                    settingsViewModel.updateBleCscEnabled(false)
                                }
                            }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // パワーメーター (Cycling Power)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFB300), modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cycling Power Meter", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Pedal / crank power output (Watts)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Switch(
                            checked = isBlePowerEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    actions.requestSensorPermissions { granted ->
                                        if (granted) settingsViewModel.updateBlePowerEnabled(true)
                                    }
                                } else {
                                    settingsViewModel.updateBlePowerEnabled(false)
                                }
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // --- 2. スキャン操作バー ---
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Manage Sensors", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (isScanning) {
                    TextButton(onClick = { sensorViewModel.stopScan() }) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Scan")
                    }
                } else {
                    Button(
                        onClick = { sensorViewModel.startFitnessScan() },
                        enabled = isBleHrEnabled || isBleCscEnabled || isBlePowerEnabled
                    ) {
                        Icon(Icons.Default.BluetoothSearching, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Scan Sensors")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- 3. 登録済みデバイス一覧 (Heart Rate) ---
        item {
            SensorCategorySection(
                title = "Heart Rate Monitors",
                icon = Icons.Default.MonitorHeart,
                iconColor = Color(IronColors.HEART_RATE_PINK),
                registeredEntries = registeredHrDevices,
                preferredAddress = preferredHrAddress,
                isConnected = isHrConnected,
                isDataActive = isHrDataActive,
                activeAddress = settingsViewModel.bleHeartRateDeviceAddress.collectAsState().value,
                valueText = if (heartRateBpm > 0) "$heartRateBpm BPM" else null,
                onTogglePin = { settingsViewModel.togglePreferredBleHrDevice(it) },
                onDelete = { addr ->
                    sensorViewModel.removeDevice()
                    settingsViewModel.removeRegisteredBleHrDevice(addr)
                }
            )

            // Pebble vs BLE 心拍優先度設定
            Surface(
                tonalElevation = 1.dp,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.settings_label_ble_hr_priority),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = stringResource(Res.string.settings_desc_ble_hr_priority),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = preferBleHr,
                        onCheckedChange = { settingsViewModel.updatePreferBleHeartRate(it) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- 4. 登録済みデバイス一覧 (CSC) ---
        item {
            SensorCategorySection(
                title = "Speed & Cadence Sensors",
                icon = Icons.Default.DirectionsBike,
                iconColor = MaterialTheme.colorScheme.primary,
                registeredEntries = registeredCscDevices,
                preferredAddress = preferredCscAddress,
                isConnected = isCscConnected,
                isDataActive = isCscDataActive,
                activeAddress = settingsViewModel.bleCscDeviceAddress.collectAsState().value,
                valueText = buildString {
                    cadenceRpm?.let { append("$it RPM ") }
                    wheelSpeedKmh?.let { append(String.format("%.1f km/h", it)) }
                }.ifEmpty { null },
                extraSetting = {
                    TextButton(onClick = { showWheelCircDialog = true }) {
                        Icon(Icons.Default.Settings, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Wheel: ${wheelCircumferenceMm}mm", fontSize = 12.sp)
                    }
                },
                onTogglePin = { settingsViewModel.togglePreferredBleCscDevice(it) },
                onDelete = { addr ->
                    sensorViewModel.removeCsc()
                    settingsViewModel.removeRegisteredBleCscDevice(addr)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- 5. 登録済みデバイス一覧 (Power) ---
        item {
            SensorCategorySection(
                title = "Power Meters",
                icon = Icons.Default.Bolt,
                iconColor = Color(0xFFFFB300),
                registeredEntries = registeredPowerDevices,
                preferredAddress = preferredPowerAddress,
                isConnected = isPowerConnected,
                isDataActive = isPowerDataActive,
                activeAddress = settingsViewModel.blePowerDeviceAddress.collectAsState().value,
                valueText = powerWatts?.let { "$it W" },
                onTogglePin = { settingsViewModel.togglePreferredBlePowerDevice(it) },
                onDelete = { addr ->
                    sensorViewModel.removePower()
                    settingsViewModel.removeRegisteredBlePowerDevice(addr)
                }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- 6. スキャン結果一覧 ---
        if (isScanning && foundDevices.isNotEmpty()) {
            item {
                Text("Discovered Sensors", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(8.dp))
            }

            val allRegistered = registeredHrDevices + registeredCscDevices + registeredPowerDevices
            val unregistered = foundDevices.filter { dev -> allRegistered.none { it.startsWith(dev.address) } }

            items(unregistered) { device ->
                val detectedTypeLabel = when {
                    device.isCyclingPower -> "Cycling Power"
                    device.isCsc -> "Speed & Cadence"
                    device.isHeartRate -> "Heart Rate"
                    else -> null
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                        onPairDevice(device)
                    },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    ListItem(
                        leadingContent = {
                            val icon = when {
                                device.isCyclingPower -> Icons.Default.Bolt
                                device.isCsc -> Icons.Default.DirectionsBike
                                device.isHeartRate -> Icons.Default.MonitorHeart
                                else -> Icons.Default.Bluetooth
                            }
                            val tint = when {
                                device.isHeartRate -> Color(IronColors.HEART_RATE_PINK)
                                device.isCyclingPower -> Color(0xFFFFB300)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Icon(icon, null, tint = tint)
                        },
                        headlineContent = { Text(device.name ?: "Unknown Sensor", fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Column {
                                Text(device.address, style = MaterialTheme.typography.bodySmall)
                                if (detectedTypeLabel != null) {
                                    Text(
                                        text = detectedTypeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Button(onClick = { onPairDevice(device) }) {
                                Text("Pair")
                            }
                        }
                    )
                }
            }
        }
    }

    // --- デバイスペアリング種別選択ダイアログ ---
    selectedDeviceForRegistration?.let { device ->
        AlertDialog(
            onDismissRequest = { selectedDeviceForRegistration = null },
            title = { Text("Pair Sensor") },
            text = {
                Column {
                    Text("Select sensor type for ${device.name ?: device.address}:")
                    Spacer(Modifier.height(12.dp))

                    ListItem(
                        leadingContent = { Icon(Icons.Default.MonitorHeart, null, tint = Color(IronColors.HEART_RATE_PINK)) },
                        headlineContent = { Text("Heart Rate Monitor") },
                        modifier = Modifier.clickable {
                            registerDeviceWithType(device, BleSensorType.HEART_RATE)
                        }
                    )
                    ListItem(
                        leadingContent = { Icon(Icons.Default.DirectionsBike, null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("Cycling Speed & Cadence") },
                        modifier = Modifier.clickable {
                            registerDeviceWithType(device, BleSensorType.CSC)
                        }
                    )
                    ListItem(
                        leadingContent = { Icon(Icons.Default.Bolt, null, tint = Color(0xFFFFB300)) },
                        headlineContent = { Text("Cycling Power Meter") },
                        modifier = Modifier.clickable {
                            registerDeviceWithType(device, BleSensorType.CYCLING_POWER)
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedDeviceForRegistration = null }) { Text("Cancel") }
            }
        )
    }

    // --- タイヤ周長設定ダイアログ ---
    if (showWheelCircDialog) {
        var inputMm by remember { mutableStateOf(wheelCircumferenceMm.toString()) }
        AlertDialog(
            onDismissRequest = { showWheelCircDialog = false },
            title = { Text("Wheel Circumference") },
            text = {
                Column {
                    Text("Enter wheel circumference in mm (e.g., 2105 for 700x25C):", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputMm,
                        onValueChange = { inputMm = it.filter { char -> char.isDigit() } },
                        label = { Text("Circumference (mm)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    inputMm.toIntOrNull()?.let {
                        if (it in 500..3000) {
                            settingsViewModel.updateWheelCircumference(it)
                            sensorViewModel.setWheelCircumference(it)
                        }
                    }
                    showWheelCircDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showWheelCircDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SensorCategorySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    registeredEntries: List<String>,
    preferredAddress: String?,
    isConnected: Boolean,
    isDataActive: Boolean,
    activeAddress: String?,
    valueText: String?,
    extraSetting: (@Composable () -> Unit)? = null,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = iconColor)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
            extraSetting?.invoke()
        }
        Spacer(modifier = Modifier.height(6.dp))

        if (registeredEntries.isEmpty()) {
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No sensors registered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            registeredEntries.forEach { entry ->
                val address = entry.substringBefore("|")
                val name = entry.substringAfter("|")
                val isPreferred = preferredAddress == address
                val isActiveDevice = activeAddress == address

                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isPreferred) 4.dp else 1.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isPreferred) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(name, fontWeight = if (isPreferred) FontWeight.Bold else FontWeight.Normal)
                        },
                        supportingContent = { Text(address, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            IconButton(onClick = { onTogglePin(address) }) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pin",
                                    tint = if (isPreferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    modifier = Modifier.graphicsLayer(rotationZ = if (isPreferred) 0f else -45f)
                                )
                            }
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isConnected && isDataActive && isActiveDevice && valueText != null) {
                                    Surface(
                                        color = iconColor.copy(alpha = 0.15f),
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        Text(
                                            text = valueText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = iconColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                IconButton(onClick = { onDelete(address) }) {
                                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
