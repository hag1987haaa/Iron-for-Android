package hag1987haaa.pebble.iron.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
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
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _heartRateBpm = MutableStateFlow(0)
    override val heartRateBpm: StateFlow<Int> = _heartRateBpm.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isDataActive = MutableStateFlow(false)
    override val isDataActive: StateFlow<Boolean> = _isDataActive.asStateFlow()

    private var activeManager: HeartRateBleManager? = null
    private var currentDeviceAddress: String? = null
    private var dataTimeoutJob: Job? = null

    private fun createObserver() = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) { Log.i("BleHR", "Connecting to ${device.address}") }
        override fun onDeviceConnected(device: BluetoothDevice) { Log.i("BleHR", "Connected to ${device.address}") }
        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            Log.e("BleHR", "Failed to connect to ${device.address}, reason: $reason")
            _isConnected.value = false
            _isDataActive.value = false
        }
        override fun onDeviceReady(device: BluetoothDevice) {
            Log.i("BleHR", "Device ${device.address} is READY (Services discovered)")
            _isConnected.value = true
        }
        override fun onDeviceDisconnecting(device: BluetoothDevice) { Log.d("BleHR", "Disconnecting from ${device.address}") }
        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            Log.w("BleHR", "Disconnected from ${device.address}, reason: $reason")
            _isConnected.value = false
            _isDataActive.value = false
            _heartRateBpm.value = 0

            // 切断されたら、現在のマネージャーを安全に閉じる
            activeManager?.close()
            activeManager = null

            if (reason != ConnectionObserver.REASON_SUCCESS && currentDeviceAddress != null) {
                Log.i("BleHR", "Unexpected disconnect. Retrying via AutoConnect...")
                scope.launch { connect(currentDeviceAddress!!, useAutoConnect = true) }
            }
        }
    }

    private fun resetDataTimeout() {
        if (!_isDataActive.value) _isDataActive.value = true
        dataTimeoutJob?.cancel()
        dataTimeoutJob = scope.launch {
            delay(12000)
            if (_isDataActive.value) {
                _isDataActive.value = false
                _heartRateBpm.value = 0
                Log.w("BleHR", "Data stream TIMEOUT")
            }
        }
    }

    override fun connect(address: String) {
        // すでに接続中かつアドレスも同じなら、何もしない（二重接続防止）
        if (_isConnected.value && currentDeviceAddress == address) {
            Log.d("BleHR", "Already connected to $address. Skip.")
            return
        }
        scope.launch { connect(address, useAutoConnect = false) }
    }

    private suspend fun connect(address: String, useAutoConnect: Boolean) {
        Log.i("BleHR", "Initiating connection session: $address (AutoConnect=$useAutoConnect)")
        
        // 1. 以前のマネージャーがいれば破棄
        activeManager?.let { 
            Log.d("BleHR", "Cleaning up previous session...")
            it.close() 
            delay(300) // Bluetoothスタックの安定待ち
        }
        
        // 2. 新品のマネージャーを生成
        val newManager = HeartRateBleManager(context)
        newManager.connectionObserver = createObserver()
        activeManager = newManager
        currentDeviceAddress = address

        // 3. BluetoothAdapterの取得 (SDK 36 推奨方式)
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            Log.e("BleHR", "Bluetooth Adapter not available or disabled")
            return
        }

        try {
            val device = adapter.getRemoteDevice(address)
            newManager.connect(device)
                .retry(10, 500)
                .useAutoConnect(useAutoConnect)
                .timeout(15000)
                .enqueue()
        } catch (e: Exception) {
            Log.e("BleHR", "Connect error: ${e.message}")
        }
    }

    override fun disconnect() {
        Log.i("BleHR", "Manual disconnect requested")
        val manager = activeManager
        if (manager != null && _isConnected.value) {
            // あえて stopNotifications() を呼ばず、直接切断を指示する
            // これにより WHOOP 等の「常時ブロードキャスト」モードの解除を防ぐ
            manager.disconnect().enqueue()
            // 実際の close() は createObserver 内の onDeviceDisconnected で行われる
        } else {
            activeManager?.close()
            activeManager = null
        }
        _isConnected.value = false
        _isDataActive.value = false
        _heartRateBpm.value = 0
    }

    override fun close() {
        disconnect()
        currentDeviceAddress = null
    }

    private inner class HeartRateBleManager(context: Context) : BleManager(context) {
        private var hrCharacteristic: BluetoothGattCharacteristic? = null

        fun stopNotifications() {
            if (hrCharacteristic != null) {
                disableNotifications(hrCharacteristic).enqueue()
            }
        }

        override fun getGattCallback(): BleManagerGattCallback = object : BleManagerGattCallback() {
            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service = gatt.getService(HR_SERVICE_UUID)
                hrCharacteristic = service?.getCharacteristic(HR_MEASUREMENT_CHARACTERISTIC_UUID)
                Log.d("BleHR", "Service 180D support: ${hrCharacteristic != null}")
                return hrCharacteristic != null
            }

            override fun initialize() {
                Log.d("BleHR", "Enabling HR notifications...")
                setNotificationCallback(hrCharacteristic)
                    .with { _, data ->
                        val bpm = parseHeartRate(data.value)
                        if (bpm > 0) {
                            _heartRateBpm.value = bpm
                            resetDataTimeout()
                            Log.v("BleHR", "Raw BPM: $bpm")
                        }
                    }
                enableNotifications(hrCharacteristic)
                    .done { Log.i("BleHR", "Notifications active") }
                    .fail { _, status -> Log.e("BleHR", "Notification failed: $status") }
                    .enqueue()
            }

            override fun onServicesInvalidated() { hrCharacteristic = null }
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
