package hag1987haaa.pebble.iron.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
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
            val device = result.device
            val bleDevice = BleDevice(
                name = device.name,
                address = device.address,
                rssi = result.rssi
            )
            
            val currentList = _foundDevices.value
            if (currentList.none { it.address == bleDevice.address }) {
                _foundDevices.value = currentList + bleDevice
                Log.d("AndroidBleScanner", "Found device: ${bleDevice.name} (${bleDevice.address})")
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

    override fun startScan(serviceUuid: String?) {
        // 安全のため、既にスキャン中であれば一度停止してリセットする
        if (_isScanning.value) {
            stopScan()
        }
        
        _foundDevices.value = emptyList()
        
        val settings = ScanSettings.Builder()
            .setLegacy(false)
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .setUseHardwareBatchingIfSupported(true)
            .build()
            
        val filters = mutableListOf<ScanFilter>()
        if (serviceUuid != null) {
            filters.add(ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(serviceUuid)).build())
        }

        try {
            scanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Log.i("AndroidBleScanner", "Scan started with filter: $serviceUuid")
        } catch (e: Exception) {
            Log.e("AndroidBleScanner", "Failed to start scan", e)
        }
    }

    override fun stopScan() {
        if (!_isScanning.value) return
        scanner.stopScan(scanCallback)
        _isScanning.value = false
        Log.i("AndroidBleScanner", "Scan stopped")
    }
}
