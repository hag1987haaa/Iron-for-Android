package hag1987haaa.pebble.iron.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.ble.BleDevice
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorSettingsScreen(actions: AppActions, onBack: () -> Unit) {
    val sensorViewModel: SensorViewModel = viewModel { SensorViewModel() }
    val settingsViewModel: SettingsViewModel = viewModel { SettingsViewModel(KmpDependencies.appSettings) }
    
    val foundDevices by sensorViewModel.foundDevices.collectAsState()
    val isScanning by sensorViewModel.isScanning.collectAsState()
    val isConnected by sensorViewModel.isConnected.collectAsState()
    val isDataActive by sensorViewModel.isDataActive.collectAsState()
    val heartRateBpm by sensorViewModel.heartRateBpm.collectAsState()
    
    val bleHrEnabled by settingsViewModel.isBleHeartRateEnabled.collectAsState()
    val bleHrAddress by settingsViewModel.bleHeartRateDeviceAddress.collectAsState()
    val bleHrName by settingsViewModel.bleHeartRateDeviceName.collectAsState()
    val preferBle by settingsViewModel.preferBleHeartRate.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(16.dp))
                    } else {
                        IconButton(onClick = { sensorViewModel.startHrScan() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            item {
                Text(
                    text = "Heart Rate Sensor",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            item {
                Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bluetooth, null, tint = if (bleHrEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Use External BLE Sensor", style = MaterialTheme.typography.bodyLarge)
                                Text("Connect to HR straps or other devices", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                            }
                            Switch(
                                checked = bleHrEnabled, 
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
                        
                        if (bleHrEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Favorite, null, tint = Color(0xFFE91E63))
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Priority: BLE Sensor", style = MaterialTheme.typography.bodyLarge)
                                    Text("Prefer BLE over Pebble internal HR", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                                }
                                Switch(checked = preferBle, onCheckedChange = { settingsViewModel.updatePreferBleHeartRate(it) })
                            }
                        }
                    }
                }
            }

            if (bleHrEnabled) {
                item {
                    Text(
                        text = "Paired Device",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
                    )
                }

                item {
                    if (bleHrAddress != null) {
                        ListItem(
                            headlineContent = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.MonitorHeart, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(bleHrName ?: "Unknown Device") 
                                }
                            },
                            supportingContent = { 
                                Column {
                                    val statusText = if (isConnected) {
                                        if (isDataActive) "Connected & Receiving Data"
                                        else "Connected (Waiting for data...)"
                                    } else "Disconnected"
                                    
                                    Text(
                                        text = statusText,
                                        color = if (isConnected && isDataActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Text(text = bleHrAddress!!, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            leadingContent = { 
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                    if (isConnected && isDataActive) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = heartRateBpm.toString(),
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE91E63)
                                            )
                                            Text("BPM", fontSize = 7.sp, color = Color(0xFFE91E63), fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Icon(
                                            Icons.Default.Bluetooth, 
                                            null, 
                                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Row {
                                    if (!isConnected) {
                                        TextButton(onClick = { sensorViewModel.connectDevice(bleHrAddress!!) }) {
                                            Text("Connect")
                                        }
                                    } else {
                                        TextButton(onClick = { sensorViewModel.disconnectDevice() }) {
                                            Text("Disconnect")
                                        }
                                    }
                                    IconButton(onClick = { 
                                        sensorViewModel.disconnectDevice()
                                        settingsViewModel.setBleHeartRateDevice(null, null) 
                                    }) {
                                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        )
                    } else {
                        ListItem(
                            headlineContent = { Text("No device paired", color = MaterialTheme.colorScheme.outline) },
                            leadingContent = { Icon(Icons.Default.BluetoothSearching, null, tint = MaterialTheme.colorScheme.outline) }
                        )
                    }
                }

                item {
                    Text(
                        text = "Available Devices",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 8.dp)
                    )
                }

                if (foundDevices.isEmpty() && !isScanning) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Button(onClick = { sensorViewModel.startHrScan() }) {
                                Text("Scan for Sensors")
                            }
                        }
                    }
                }

                items(foundDevices) { device ->
                    ListItem(
                        headlineContent = { Text(device.name ?: "Unknown Device") },
                        supportingContent = { Text(device.address) },
                        modifier = Modifier.clickable {
                            settingsViewModel.setBleHeartRateDevice(device.address, device.name)
                            sensorViewModel.connectDevice(device.address)
                            sensorViewModel.stopScan()
                        },
                        trailingContent = {
                            if (device.address == bleHrAddress) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}
