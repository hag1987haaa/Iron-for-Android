package hag1987haaa.pebble.iron.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import hag1987haaa.pebble.iron.domain.ble.BleHeartRateManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

class AndroidBleHeartRateManager(context: Context) : BleHeartRateManager {
    
    private val manager = HeartRateBleManager(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _heartRateBpm = MutableStateFlow(0)
    override val heartRateBpm: StateFlow<Int> = _heartRateBpm.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isDataActive = MutableStateFlow(false)
    override val isDataActive: StateFlow<Boolean> = _isDataActive.asStateFlow()

    private var currentDeviceAddress: String? = null
    private var dataTimeoutJob: Job? = null

    init {
        manager.connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) {}
            override fun onDeviceConnected(device: BluetoothDevice) {}
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                _isConnected.value = false
                _isDataActive.value = false
            }
            override fun onDeviceReady(device: BluetoothDevice) {
                _isConnected.value = true
            }
            override fun onDeviceDisconnecting(device: BluetoothDevice) {}
            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                _isConnected.value = false
                _isDataActive.value = false
                _heartRateBpm.value = 0
            }
        }
    }

    private fun resetDataTimeout() {
        _isDataActive.value = true
        dataTimeoutJob?.cancel()
        dataTimeoutJob = scope.launch {
            delay(10000) // 10秒間データが来なければ非アクティブとする
            _isDataActive.value = false
            _heartRateBpm.value = 0
            Log.w("BleHR", "Data timeout - No HR broadcast received for 10s")
        }
    }

    override fun connect(address: String) {
        // すでに接続中または同じアドレスへの接続試行中なら何もしない
        if (_isConnected.value && currentDeviceAddress == address) return
        
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        val device = bluetoothAdapter.getRemoteDevice(address)
        currentDeviceAddress = address
        
        manager.connect(device)
            .retry(3, 100)
            .useAutoConnect(true)
            .enqueue()
    }

    override fun disconnect() {
        manager.disconnect().enqueue()
    }

    override fun close() {
        manager.close()
        _isConnected.value = false
        _isDataActive.value = false
        _heartRateBpm.value = 0
    }

    private inner class HeartRateBleManager(context: Context) : BleManager(context) {
        
        private var hrCharacteristic: BluetoothGattCharacteristic? = null

        override fun getGattCallback(): BleManagerGattCallback = object : BleManagerGattCallback() {
            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service = gatt.getService(HR_SERVICE_UUID)
                hrCharacteristic = service?.getCharacteristic(HR_MEASUREMENT_CHARACTERISTIC_UUID)
                return hrCharacteristic != null
            }

            override fun initialize() {
                setNotificationCallback(hrCharacteristic)
                    .with { _, data ->
                        val bpm = parseHeartRate(data.value)
                        if (bpm > 0) {
                            _heartRateBpm.value = bpm
                            resetDataTimeout()
                        }
                        Log.d("BleHR", "Received BPM: $bpm")
                    }
                enableNotifications(hrCharacteristic).enqueue()
            }

            override fun onServicesInvalidated() {
                hrCharacteristic = null
            }
        }
    }

    private fun parseHeartRate(data: ByteArray?): Int {
        if (data == null || data.isEmpty()) return 0
        val flag = data[0].toInt()
        val format = flag and 0x01
        return if (format == 0) {
            data[1].toInt() and 0xFF
        } else {
            ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        }
    }

    companion object {
        private val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    }
}
