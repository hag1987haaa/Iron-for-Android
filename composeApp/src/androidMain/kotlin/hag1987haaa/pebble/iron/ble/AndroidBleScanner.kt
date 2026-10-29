package hag1987haaa.pebble.iron.ble

import android.annotation.SuppressLint
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import hag1987haaa.pebble.iron.domain.ble.BleDevice
import hag1987haaa.pebble.iron.domain.ble.BleScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings
import java.util.UUID

class AndroidBleScanner(private val context: Context) : BleScanner {
    private val scanner = BluetoothLeScannerCompat.getScanner()
    
    private val _foundDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    override val foundDevices: StateFlow<List<BleDevice>> = _foundDevices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    override val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            try {
                val device = result.device ?: return
                val uuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()
                val devName = try {
                    device.name ?: result.scanRecord?.deviceName
                } catch (_: SecurityException) {
                    result.scanRecord?.deviceName
                }

                val bleDevice = BleDevice(
                    name = devName,
                    address = device.address,
                    rssi = result.rssi,
                    serviceUuids = uuids
                )
                
                val currentList = _foundDevices.value
                val existingIndex = currentList.indexOfFirst { it.address == bleDevice.address }
                if (existingIndex < 0) {
                    _foundDevices.value = currentList + bleDevice
                    Log.d("AndroidBleScanner", "Found device: ${bleDevice.name} (${bleDevice.address}), uuids=$uuids")
                } else {
                    // UUID情報が新たに取得できた場合などは更新
                    val existing = currentList[existingIndex]
                    if (existing.serviceUuids.isEmpty() && uuids.isNotEmpty()) {
                        val updatedList = currentList.toMutableList()
                        updatedList[existingIndex] = bleDevice
                        _foundDevices.value = updatedList
                    }
                }
            } catch (e: Exception) {
                Log.e("AndroidBleScanner", "Error processing scan result", e)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("AndroidBleScanner", "Scan failed with error: $errorCode")
            _isScanning.value = false
        }
    }

    override fun startScan() {
        startScan(DEFAULT_FITNESS_SERVICE_UUIDS)
    }

    override fun startScan(serviceUuid: String) {
        startScan(listOf(serviceUuid))
    }

    override fun startScan(serviceUuids: List<String>) {
        if (_isScanning.value) {
            stopScan()
        }
        
        _foundDevices.value = emptyList()
        
        val settings = ScanSettings.Builder()
            .setLegacy(true)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareBatchingIfSupported(true)
            .build()
            
        val filters = mutableListOf<ScanFilter>()
        val targetUuids = if (serviceUuids.isNotEmpty()) serviceUuids else DEFAULT_FITNESS_SERVICE_UUIDS
        for (uuidStr in targetUuids) {
            try {
                filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuidStr)).build())
            } catch (e: Exception) {
                Log.w("AndroidBleScanner", "Invalid UUID string for scan filter: $uuidStr")
            }
        }

        try {
            scanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Log.i("AndroidBleScanner", "Scan started with ${filters.size} filters")
        } catch (e: Exception) {
            Log.e("AndroidBleScanner", "Failed to start scan", e)
        }
    }

    override fun stopScan() {
        if (!_isScanning.value) return
        try {
            scanner.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e("AndroidBleScanner", "Error stopping scan", e)
        }
        _isScanning.value = false
        Log.i("AndroidBleScanner", "Scan stopped")
    }

    companion object {
        val DEFAULT_FITNESS_SERVICE_UUIDS = listOf(
            "0000180d-0000-1000-8000-00805f9b34fb", // Heart Rate
            "00001816-0000-1000-8000-00805f9b34fb", // Cycling Speed and Cadence (CSC)
            "00001818-0000-1000-8000-00805f9b34fb", // Cycling Power
            "00001814-0000-1000-8000-00805f9b34fb"  // Running Speed and Cadence (RSC)
        )
    }
}
