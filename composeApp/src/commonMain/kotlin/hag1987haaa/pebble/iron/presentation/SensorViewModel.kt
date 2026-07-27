package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.ble.BleDevice
import hag1987haaa.pebble.iron.domain.ble.BleScanner
import hag1987haaa.pebble.iron.domain.ble.BleHeartRateManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SensorViewModel(
    private val bleScanner: BleScanner = KmpDependencies.bleScanner,
    private val bleHrManager: BleHeartRateManager = KmpDependencies.bleHeartRateManager
) : ViewModel() {

    val foundDevices: StateFlow<List<BleDevice>> = bleScanner.foundDevices
    val isScanning: StateFlow<Boolean> = bleScanner.isScanning
    val isConnected: StateFlow<Boolean> = bleHrManager.isConnected
    val isDataActive: StateFlow<Boolean> = bleHrManager.isDataActive
    val heartRateBpm: StateFlow<Int> = bleHrManager.heartRateBpm

    fun startHrScan() {
        // スキャン開始前に現在の動作（既存のスキャンや未接続の接続試行）を完全にリセット
        stopScan()
        bleHrManager.close()

        // Heart Rate Service UUID: 0x180D
        bleScanner.startScan("0000180d-0000-1000-8000-00805f9b34fb")
    }

    fun stopScan() {
        bleScanner.stopScan()
    }

    fun connectDevice(address: String) {
        bleHrManager.connect(address)
    }

    fun disconnectDevice() {
        bleHrManager.disconnect()
    }

    fun removeDevice() {
        // 削除時は切断だけでなく、リソースを完全に解放してスキャンに備える
        bleHrManager.close()
    }

    override fun onCleared() {
        super.onCleared()
        bleScanner.stopScan()
    }
}
