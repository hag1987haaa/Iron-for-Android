package hag1987haaa.pebble.iron.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import hag1987haaa.pebble.iron.domain.ble.BlePowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

class AndroidBlePowerManager(private val context: Context) : BlePowerManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _powerWatts = MutableStateFlow<Int?>(null)
    override val powerWatts: StateFlow<Int?> = _powerWatts.asStateFlow()

    private val _cadenceRpm = MutableStateFlow<Int?>(null)
    override val cadenceRpm: StateFlow<Int?> = _cadenceRpm.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isDataActive = MutableStateFlow(false)
    override val isDataActive: StateFlow<Boolean> = _isDataActive.asStateFlow()

    private var activeManager: PowerBleManager? = null
    private var currentDeviceAddress: String? = null
    private var dataTimeoutJob: Job? = null
    private var connectJob: Job? = null

    // パワーメーターにケイデンスが含まれる場合の計算用
    private var lastCrankRevolutions: Int? = null
    private var lastCrankEventTime: Int? = null // 1/2048s
    private var lastCrankUpdateSystemTime: Long = 0L

    private fun createObserver() = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) { Log.i("BlePower", "Connecting...") }
        override fun onDeviceConnected(device: BluetoothDevice) { Log.i("BlePower", "Connected") }
        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            _isConnected.value = false
            _isDataActive.value = false
            if (currentDeviceAddress != null) {
                Log.i("BlePower", "Direct connect failed (sensor might be OFF), waiting with autoConnect...")
                connectInternal(currentDeviceAddress!!, useAutoConnect = true)
            }
        }
        override fun onDeviceReady(device: BluetoothDevice) {
            Log.i("BlePower", "Ready")
            _isConnected.value = true
        }
        override fun onDeviceDisconnecting(device: BluetoothDevice) {}
        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            _isConnected.value = false
            _isDataActive.value = false
            resetValues()

            if (reason != ConnectionObserver.REASON_SUCCESS && currentDeviceAddress != null) {
                connectInternal(currentDeviceAddress!!, useAutoConnect = true)
            }
        }
    }

    private fun resetValues() {
        _powerWatts.value = null
        _cadenceRpm.value = null
        lastCrankRevolutions = null
        lastCrankEventTime = null
    }

    private fun handleIncomingData(data: ByteArray?) {
        if (data == null || data.size < 4) return
        _isDataActive.value = true
        resetSilenceTimeout()

        // 1. Flags (16 bit)
        val flags = (data[0].toInt() and 0xFF) or ((data[1].toInt() and 0xFF) shl 8)

        // 2. Instantaneous Power (sint16)
        val rawPower = (data[2].toInt() and 0xFF) or (data[3].toInt() shl 8)
        val watts = rawPower.coerceAtLeast(0)
        _powerWatts.value = watts

        // オプションフィールドのパース
        var offset = 4
        // Bit 0: Pedal Power Balance Present (1 byte)
        if ((flags and 0x0001) != 0) offset += 1
        // Bit 2: Accumulated Torque Present (2 bytes)
        if ((flags and 0x0004) != 0) offset += 2
        // Bit 4: Wheel Revolution Data Present (4 bytes rev + 2 bytes time = 6 bytes)
        if ((flags and 0x0010) != 0) offset += 6
        // Bit 5: Crank Revolution Data Present (2 bytes rev + 2 bytes time = 4 bytes)
        if ((flags and 0x0020) != 0 && data.size >= offset + 4) {
            val crankRev = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            val crankTime = (data[offset + 2].toInt() and 0xFF) or ((data[offset + 3].toInt() and 0xFF) shl 8)
            calculateCrankCadence(crankRev, crankTime)
        }
    }

    private fun calculateCrankCadence(crankRev: Int, crankTime: Int) {
        val nowMs = System.currentTimeMillis()
        val prevRev = lastCrankRevolutions
        val prevTime = lastCrankEventTime

        if (prevRev != null && prevTime != null) {
            var diffRev = crankRev - prevRev
            if (diffRev < 0) diffRev += 65536

            var diffTime = crankTime - prevTime
            if (diffTime < 0) diffTime += 65536

            if (diffRev > 0 && diffTime > 0) {
                // Cycling Power Service の Crank Event Time は 1/2048 秒単位
                val timeSec = diffTime / 2048.0
                val rpm = ((diffRev / timeSec) * 60.0).toInt().coerceIn(0, 250)
                _cadenceRpm.value = rpm
                lastCrankUpdateSystemTime = nowMs
            } else if (diffRev == 0 && (nowMs - lastCrankUpdateSystemTime > 3000)) {
                _cadenceRpm.value = 0
            }
        } else {
            lastCrankUpdateSystemTime = nowMs
        }

        lastCrankRevolutions = crankRev
        lastCrankEventTime = crankTime
    }

    private fun resetSilenceTimeout() {
        dataTimeoutJob?.cancel()
        dataTimeoutJob = scope.launch {
            delay(4000)
            if (_isDataActive.value) {
                Log.w("BlePower", "Silence timeout")
                _isDataActive.value = false
                _powerWatts.value = 0
                _cadenceRpm.value = 0
            }
        }
    }

    override fun connect(address: String) {
        if (_isConnected.value && currentDeviceAddress == address) return
        connectInternal(address, useAutoConnect = false)
    }

    private fun connectInternal(address: String, useAutoConnect: Boolean) {
        connectJob?.cancel()
        connectJob = scope.launch {
            connectTask(address, useAutoConnect)
        }
    }

    private suspend fun connectTask(address: String, useAutoConnect: Boolean) {
        activeManager?.let { it.close(); delay(200) }

        val newManager = PowerBleManager(context)
        newManager.connectionObserver = createObserver()
        activeManager = newManager
        currentDeviceAddress = address

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter
        if (adapter == null || !adapter.isEnabled) return

        try {
            if (android.bluetooth.BluetoothAdapter.checkBluetoothAddress(address)) {
                newManager.connect(adapter.getRemoteDevice(address))
                    .retry(5, 500)
                    .useAutoConnect(useAutoConnect)
                    .enqueue()
            } else {
                Log.w("BlePower", "Invalid Bluetooth address: $address")
            }
        } catch (e: Exception) {
            Log.e("BlePower", "Connect fail: ${e.message}")
        }
    }

    override fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        activeManager?.disconnect()?.enqueue()
        activeManager?.close()
        activeManager = null
        _isConnected.value = false
        _isDataActive.value = false
        resetValues()
    }

    override fun close() {
        disconnect()
        currentDeviceAddress = null
    }

    private inner class PowerBleManager(context: Context) : BleManager(context) {
        private var powerCharacteristic: BluetoothGattCharacteristic? = null

        override fun getGattCallback(): BleManagerGattCallback = object : BleManagerGattCallback() {
            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service = gatt.getService(POWER_SERVICE_UUID)
                powerCharacteristic = service?.getCharacteristic(POWER_MEASUREMENT_CHARACTERISTIC_UUID)
                return powerCharacteristic != null
            }

            override fun initialize() {
                setNotificationCallback(powerCharacteristic)
                    .with { _, data ->
                        handleIncomingData(data.value)
                    }
                enableNotifications(powerCharacteristic).enqueue()
            }

            override fun onServicesInvalidated() { powerCharacteristic = null }
        }
        override fun shouldClearCacheWhenDisconnected(): Boolean = true
    }

    companion object {
        val POWER_SERVICE_UUID: UUID = UUID.fromString("00001818-0000-1000-8000-00805f9b34fb")
        val POWER_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a63-0000-1000-8000-00805f9b34fb")
    }
}
