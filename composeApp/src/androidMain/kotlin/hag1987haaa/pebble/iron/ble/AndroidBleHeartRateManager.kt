package hag1987haaa.pebble.iron.ble

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

    // 不動データ（ホールド）検知用
    private var lastValue: Int = -1
    private var stagnationCount: Int = 0

    private fun createObserver() = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) { Log.i("BleHR", "Connecting...") }
        override fun onDeviceConnected(device: BluetoothDevice) { Log.i("BleHR", "Connected") }
        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            _isConnected.value = false
            _isDataActive.value = false
        }
        override fun onDeviceReady(device: BluetoothDevice) {
            Log.i("BleHR", "Ready")
            _isConnected.value = true
        }
        override fun onDeviceDisconnecting(device: BluetoothDevice) {}
        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            _isConnected.value = false
            _isDataActive.value = false
            _heartRateBpm.value = 0
            stagnationCount = 0

            if (reason != ConnectionObserver.REASON_SUCCESS && currentDeviceAddress != null) {
                scope.launch { connect(currentDeviceAddress!!, useAutoConnect = true) }
            }
        }
    }

    private fun handleIncomingBpm(bpm: Int) {
        // パケットが届いたので、沈黙タイムアウトをリセット
        _isDataActive.value = true
        resetSilenceTimeout()

        // --- 不動（ホールド）検知ロジック ---
        if (bpm == lastValue && bpm > 0) {
            stagnationCount++
        } else {
            stagnationCount = 0
            lastValue = bpm
        }

        // 12秒間（パケット約12回分）1bpmも変化がなければホールドとみなして 0 を送る
        if (stagnationCount >= 12) {
            if (_heartRateBpm.value != 0) {
                Log.w("BleHR", "Stagnation detected ($bpm bpm). Force resetting to 0.")
                _heartRateBpm.value = 0
            }
        } else {
            _heartRateBpm.value = bpm
        }
    }

    private fun resetSilenceTimeout() {
        dataTimeoutJob?.cancel()
        dataTimeoutJob = scope.launch {
            delay(5000) // 5秒間パケットが1つも来なければ沈黙とみなす
            if (_isDataActive.value) {
                Log.w("BleHR", "Silence timeout")
                _isDataActive.value = false
                _heartRateBpm.value = 0
                stagnationCount = 0
            }
        }
    }

    override fun connect(address: String) {
        if (_isConnected.value && currentDeviceAddress == address) return
        scope.launch { connect(address, useAutoConnect = false) }
    }

    private suspend fun connect(address: String, useAutoConnect: Boolean) {
        activeManager?.let { it.close(); delay(200) }
        
        val newManager = HeartRateBleManager(context)
        newManager.connectionObserver = createObserver()
        activeManager = newManager
        currentDeviceAddress = address

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) return

        try {
            newManager.connect(adapter.getRemoteDevice(address))
                .retry(5, 500)
                .useAutoConnect(useAutoConnect)
                .enqueue()
        } catch (e: Exception) {
            Log.e("BleHR", "Connect fail: ${e.message}")
        }
    }

    override fun disconnect() {
        activeManager?.disconnect()?.enqueue()
        activeManager?.close()
        activeManager = null
        _isConnected.value = false
        _isDataActive.value = false
        _heartRateBpm.value = 0
        stagnationCount = 0
    }

    override fun close() {
        disconnect()
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
                        handleIncomingBpm(bpm)
                    }
                enableNotifications(hrCharacteristic).enqueue()
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
