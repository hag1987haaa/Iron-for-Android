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

class AndroidBleHeartRateManager(private val context: Context) : BleHeartRateManager {
    
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
            override fun onDeviceConnecting(device: BluetoothDevice) {
                Log.i("BleHR", "Connecting to ${device.address}...")
            }
            override fun onDeviceConnected(device: BluetoothDevice) {
                Log.i("BleHR", "Connected to ${device.address}")
            }
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                Log.e("BleHR", "Failed to connect to ${device.address}, reason: $reason")
                _isConnected.value = false
                _isDataActive.value = false
            }
            override fun onDeviceReady(device: BluetoothDevice) {
                Log.i("BleHR", "Device ${device.address} is READY")
                _isConnected.value = true
            }
            override fun onDeviceDisconnecting(device: BluetoothDevice) {}
            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                Log.w("BleHR", "Disconnected from ${device.address}, reason: $reason")
                _isConnected.value = false
                _isDataActive.value = false
                _heartRateBpm.value = 0

                // 意図しない切断の場合、autoConnect(true)で再接続を試み続ける
                if (reason != ConnectionObserver.REASON_SUCCESS) {
                    Log.i("BleHR", "Attempting background auto-reconnect...")
                    connect(device.address, useAutoConnect = true)
                }
            }
        }
    }

    private fun resetDataTimeout() {
        if (!_isDataActive.value) {
            _isDataActive.value = true
        }
        dataTimeoutJob?.cancel()
        dataTimeoutJob = scope.launch {
            delay(12000)
            if (_isDataActive.value) {
                _isDataActive.value = false
                _heartRateBpm.value = 0
                Log.w("BleHR", "Data timeout - No HR data")
            }
        }
    }

    override fun connect(address: String) {
        connect(address, useAutoConnect = false)
    }

    /**
     * 接続のコアロジック。
     * @param useAutoConnect true の場合、Android OS がデバイスを永続的に探し続け、見つかった瞬間に繋ぐ。
     */
    private fun connect(address: String, useAutoConnect: Boolean) {
        if (_isConnected.value && currentDeviceAddress == address && !useAutoConnect) {
            return
        }
        
        Log.i("BleHR", "Initiating connection to $address (AutoConnect=$useAutoConnect)")
        val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) return

        try {
            val device = bluetoothAdapter.getRemoteDevice(address)
            currentDeviceAddress = address
            
            manager.connect(device)
                .retry(10, 500) // 再試行回数を増やして粘り強く
                .useAutoConnect(useAutoConnect)
                .timeout(15000)
                .enqueue()
        } catch (e: Exception) {
            Log.e("BleHR", "Connect error: ${e.message}")
        }
    }

    override fun disconnect() {
        Log.i("BleHR", "Disconnecting")
        // 切断時は明示的にアドレスをクリアしないことで、OSの再接続キューから外れるのを防ぐ
        manager.disconnect().enqueue()
    }

    override fun close() {
        manager.close()
        _isConnected.value = false
        _isDataActive.value = false
        _heartRateBpm.value = 0
        currentDeviceAddress = null
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
                    }
                enableNotifications(hrCharacteristic).enqueue()
            }

            override fun onServicesInvalidated() {
                hrCharacteristic = null
            }
        }

        override fun shouldClearCacheWhenDisconnected(): Boolean = true
    }

    private fun parseHeartRate(data: ByteArray?): Int {
        if (data == null || data.isEmpty()) return 0
        val flag = data[0].toInt()
        val format = flag and 0x01
        return if (format == 0) {
            if (data.size < 2) 0 else data[1].toInt() and 0xFF
        } else {
            if (data.size < 3) 0 else ((data[2].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
        }
    }

    companion object {
        private val HR_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    }
}
