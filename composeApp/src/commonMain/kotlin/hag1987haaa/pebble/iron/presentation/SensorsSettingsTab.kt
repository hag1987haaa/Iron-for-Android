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
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.theme.IronColors

@Composable
fun SensorsSettingsTab(settingsViewModel: SettingsViewModel) {
    val sensorViewModel: SensorViewModel = viewModel { SensorViewModel() }
    val foundDevices by sensorViewModel.foundDevices.collectAsState()
    val isScanning by sensorViewModel.isScanning.collectAsState()
    val isConnected by sensorViewModel.isConnected.collectAsState()
    val isDataActive by sensorViewModel.isDataActive.collectAsState()
    val heartRateBpm by sensorViewModel.heartRateBpm.collectAsState()
    
    val registeredDevices by settingsViewModel.registeredBleHrDevices.collectAsState()
    val preferredAddress by settingsViewModel.preferredBleHrAddress.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Manage Sensors", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (isScanning) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = { sensorViewModel.startHrScan() }) {
                    Icon(Icons.Default.BluetoothSearching, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Scan")
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))

        // Registered Devices List
        Text("Registered Devices", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        if (registeredDevices.isEmpty()) {
            Surface(tonalElevation = 1.dp, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No sensors registered.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            registeredDevices.forEach { entry ->
                val address = entry.substringBefore("|")
                val name = entry.substringAfter("|")
                val isPreferred = preferredAddress == address
                
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MonitorHeart, null, modifier = Modifier.size(18.dp), tint = if (isPreferred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Text(name, fontWeight = if (isPreferred) FontWeight.Bold else FontWeight.Normal) 
                            }
                        },
                        supportingContent = { Text(address, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            IconButton(onClick = { settingsViewModel.togglePreferredBleHrDevice(address) }) {
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
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                    if (isConnected && isDataActive && KmpDependencies.appSettings.bleHeartRateDeviceAddress == address) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(heartRateBpm.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(IronColors.HEART_RATE_PINK))
                                            Text("BPM", fontSize = 7.sp, color = Color(IronColors.HEART_RATE_PINK), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                IconButton(onClick = { 
                                    sensorViewModel.removeDevice()
                                    settingsViewModel.removeRegisteredBleHrDevice(address) 
                                }) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Available Devices (Scan Results)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(foundDevices) { device ->
                if (registeredDevices.none { it.startsWith(device.address) }) {
                    ListItem(
                        headlineContent = { Text(device.name ?: "Unknown Device") },
                        supportingContent = { Text(device.address) },
                        modifier = Modifier.clickable { 
                            settingsViewModel.setBleHeartRateDevice(device.address, device.name)
                            sensorViewModel.connectDevice(device.address)
                        },
                        trailingContent = { Icon(Icons.Default.Add, null) }
                    )
                }
            }
        }
    }
}
