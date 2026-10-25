package hag1987haaa.pebble.iron.presentation

import androidx.lifecycle.ViewModel
import hag1987haaa.pebble.iron.KmpDependencies
import hag1987haaa.pebble.iron.domain.ble.BleDevice
import hag1987haaa.pebble.iron.domain.ble.BleScanner
import hag1987haaa.pebble.iron.domain.ble.BleHeartRateManager
import hag1987haaa.pebble.iron.domain.ble.BleCscManager
import hag1987haaa.pebble.iron.domain.ble.BlePowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SensorViewModel(
    private val bleScanner: BleScanner = KmpDependencies.bleScanner,
    private val bleHrManager: BleHeartRateManager = KmpDependencies.bleHeartRateManager,
    private val bleCscManager: BleCscManager? = KmpDependencies.bleCscManager,
    private val blePowerManager: BlePowerManager? = KmpDependencies.blePowerManager
) : ViewModel() {

    val foundDevices: StateFlow<List<BleDevice>> = bleScanner.foundDevices
    val isScanning: StateFlow<Boolean> = bleScanner.isScanning

    // Heart Rate
    val isConnected: StateFlow<Boolean> = bleHrManager.isConnected
    val isDataActive: StateFlow<Boolean> = bleHrManager.isDataActive
    val heartRateBpm: StateFlow<Int> = bleHrManager.heartRateBpm

    // CSC (ケイデンス & スピード)
    private val dummyFalse = MutableStateFlow(false).asStateFlow()
    private val dummyNullInt = MutableStateFlow<Int?>(null).asStateFlow()
    private val dummyNullDouble = MutableStateFlow<Double?>(null).asStateFlow()

    val isCscConnected: StateFlow<Boolean> = bleCscManager?.isConnected ?: dummyFalse
    val isCscDataActive: StateFlow<Boolean> = bleCscManager?.isDataActive ?: dummyFalse
    val cadenceRpm: StateFlow<Int?> = bleCscManager?.cadenceRpm ?: dummyNullInt
    val wheelSpeedKmh: StateFlow<Double?> = bleCscManager?.wheelSpeedKmh ?: dummyNullDouble

    // Cycling Power
    val isPowerConnected: StateFlow<Boolean> = blePowerManager?.isConnected ?: dummyFalse
    val isPowerDataActive: StateFlow<Boolean> = blePowerManager?.isDataActive ?: dummyFalse
    val powerWatts: StateFlow<Int?> = blePowerManager?.powerWatts ?: dummyNullInt
    val powerCadenceRpm: StateFlow<Int?> = blePowerManager?.cadenceRpm ?: dummyNullInt

    fun startHrScan() {
        stopScan()
        bleHrManager.close()
        bleScanner.startScan("0000180d-0000-1000-8000-00805f9b34fb")
    }

    fun startFitnessScan() {
        stopScan()
        bleScanner.startScan() // 全スポーツセンサーUUIDでスキャン
    }

    fun stopScan() {
        bleScanner.stopScan()
    }

    // Heart Rate
    fun connectDevice(address: String) {
        bleHrManager.connect(address)
    }

    fun disconnectDevice() {
        bleHrManager.disconnect()
    }

    fun removeDevice() {
        bleHrManager.close()
    }

    // CSC
    fun connectCsc(address: String) {
        bleCscManager?.connect(address)
    }

    fun disconnectCsc() {
        bleCscManager?.disconnect()
    }

    fun removeCsc() {
        bleCscManager?.close()
    }

    fun setWheelCircumference(mm: Int) {
        bleCscManager?.setWheelCircumferenceMm(mm)
    }

    // Cycling Power
    fun connectPower(address: String) {
        blePowerManager?.connect(address)
    }

    fun disconnectPower() {
        blePowerManager?.disconnect()
    }

    fun removePower() {
        blePowerManager?.close()
    }

    override fun onCleared() {
        super.onCleared()
        bleScanner.stopScan()
    }
}
