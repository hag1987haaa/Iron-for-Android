package hag1987haaa.pebble.iron.domain.ble

import kotlinx.coroutines.flow.StateFlow

interface BleScanner {
    val foundDevices: StateFlow<List<BleDevice>>
    val isScanning: StateFlow<Boolean>
    
    fun startScan(serviceUuid: String? = null)
    fun stopScan()
}
