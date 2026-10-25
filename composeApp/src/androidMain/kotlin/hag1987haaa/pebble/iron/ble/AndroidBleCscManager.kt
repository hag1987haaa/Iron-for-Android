package hag1987haaa.pebble.iron.ble

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import hag1987haaa.pebble.iron.domain.ble.BleCscManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.observer.ConnectionObserver
import java.util.UUID

class AndroidBleCscManager(private val context: Context) : BleCscManager {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _cadenceRpm = MutableStateFlow<Int?>(null)
    override val cadenceRpm: StateFlow<Int?> = _cadenceRpm.asStateFlow()

    private val _wheelSpeedKmh = MutableStateFlow<Double?>(null)
    override val wheelSpeedKmh: StateFlow<Double?> = _wheelSpeedKmh.asStateFlow()

    private val _cumulativeDistanceMeters = MutableStateFlow<Double?>(null)
    override val cumulativeDistanceMeters: StateFlow<Double?> = _cumulativeDistanceMeters.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isDataActive = MutableStateFlow(false)
    override val isDataActive: StateFlow<Boolean> = _isDataActive.asStateFlow()

    private var wheelCircumferenceMm: Int = 2105 // デフォルト 700x25C
    private var activeManager: CscBleManager? = null
    private var currentDeviceAddress: String? = null
    private var dataTimeoutJob: Job? = null

    // ケイデンス計算用前状態
    private var lastCrankRevolutions: Int? = null
    private var lastCrankEventTime: Int? = null // 1/1024s
    private var lastCrankUpdateSystemTime: Long = 0L

    // スピード計算用前状態
    private var lastWheelRevolutions: Long? = null
    private var lastWheelEventTime: Int? = null // 1/1024s
    private var initialWheelRevolutions: Long? = null

    override fun setWheelCircumferenceMm(mm: Int) {
        if (mm > 0) {
            this.wheelCircumferenceMm = mm
        }
    }

    private fun createObserver() = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) { Log.i("BleCSC", "Connecting...") }
        override fun onDeviceConnected(device: BluetoothDevice) { Log.i("BleCSC", "Connected") }
        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            _isConnected.value = false
            _isDataActive.value = false
        }
        override fun onDeviceReady(device: BluetoothDevice) {
            Log.i("BleCSC", "Ready")
            _isConnected.value = true
        }
        override fun onDeviceDisconnecting(device: BluetoothDevice) {}
        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            _isConnected.value = false
            _isDataActive.value = false
            resetValues()

            if (reason != ConnectionObserver.REASON_SUCCESS && currentDeviceAddress != null) {
                scope.launch { connect(currentDeviceAddress!!, useAutoConnect = true) }
            }
        }
    }

    private fun resetValues() {
        _cadenceRpm.value = null
        _wheelSpeedKmh.value = null
        lastCrankRevolutions = null
        lastCrankEventTime = null
        lastWheelRevolutions = null
        lastWheelEventTime = null
    }

    private fun handleIncomingData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        _isDataActive.value = true
        resetSilenceTimeout()

        var offset = 0
        val flags = data[offset++].toInt() and 0xFF
        val hasWheel = (flags and 0x01) != 0
        val hasCrank = (flags and 0x02) != 0

        val nowMs = System.currentTimeMillis()

        if (hasWheel && data.size >= offset + 6) {
            val wheelRev = (data[offset].toLong() and 0xFF) or
                    ((data[offset + 1].toLong() and 0xFF) shl 8) or
                    ((data[offset + 2].toLong() and 0xFF) shl 16) or
                    ((data[offset + 3].toLong() and 0xFF) shl 24)
            offset += 4
            val wheelTime = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2

            calculateWheelData(wheelRev, wheelTime)
        }

        if (hasCrank && data.size >= offset + 4) {
            val crankRev = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2
            val crankTime = (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
            offset += 2

            calculateCrankData(crankRev, crankTime, nowMs)
        }
    }

    private fun calculateCrankData(crankRev: Int, crankTime: Int, nowMs: Long) {
        val prevRev = lastCrankRevolutions
        val prevTime = lastCrankEventTime

        if (prevRev != null && prevTime != null) {
            var diffRev = crankRev - prevRev
            if (diffRev < 0) diffRev += 65536

            var diffTime = crankTime - prevTime
            if (diffTime < 0) diffTime += 65536

            if (diffRev > 0 && diffTime > 0) {
                // diffTime は 1/1024 秒単位
                val timeSec = diffTime / 1024.0
                val rpm = ((diffRev / timeSec) * 60.0).toInt().coerceIn(0, 250)
                _cadenceRpm.value = rpm
                lastCrankUpdateSystemTime = nowMs
            } else if (diffRev == 0 && (nowMs - lastCrankUpdateSystemTime > 3000)) {
                // 3秒以上クランクが回っていないならケイデンス 0
                _cadenceRpm.value = 0
            }
        } else {
            lastCrankUpdateSystemTime = nowMs
        }

        lastCrankRevolutions = crankRev
        lastCrankEventTime = crankTime
    }

    private fun calculateWheelData(wheelRev: Long, wheelTime: Int) {
        val prevRev = lastWheelRevolutions
        val prevTime = lastWheelEventTime

        if (initialWheelRevolutions == null) {
            initialWheelRevolutions = wheelRev
        }

        // 走行距離の計算 (m)
        val wheelCircM = wheelCircumferenceMm / 1000.0
        val distM = (wheelRev - (initialWheelRevolutions ?: wheelRev)) * wheelCircM
        if (distM >= 0) {
            _cumulativeDistanceMeters.value = distM
        }

        if (prevRev != null && prevTime != null) {
            var diffRev = (wheelRev - prevRev)
            if (diffRev < 0) diffRev += 0x100000000L

            var diffTime = wheelTime - prevTime
            if (diffTime < 0) diffTime += 65536

            if (diffRev > 0 && diffTime > 0) {
                val timeSec = diffTime / 1024.0
                val speedMps = (diffRev * wheelCircM) / timeSec
                val speedKmh = (speedMps * 3.6).coerceIn(0.0, 150.0)
                _wheelSpeedKmh.value = speedKmh
            } else if (diffRev == 0L) {
                _wheelSpeedKmh.value = 0.0
            }
        }

        lastWheelRevolutions = wheelRev
        lastWheelEventTime = wheelTime
    }

    private fun resetSilenceTimeout() {
        dataTimeoutJob?.cancel()
        dataTimeoutJob = scope.launch {
            delay(4000)
            if (_isDataActive.value) {
                Log.w("BleCSC", "Silence timeout (sensor stopped)")
                _isDataActive.value = false
                _cadenceRpm.value = 0
                _wheelSpeedKmh.value = 0.0
            }
        }
    }

    override fun connect(address: String) {
        if (_isConnected.value && currentDeviceAddress == address) return
        scope.launch { connect(address, useAutoConnect = false) }
    }

    private suspend fun connect(address: String, useAutoConnect: Boolean) {
        activeManager?.let { it.close(); delay(200) }

        val newManager = CscBleManager(context)
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
            Log.e("BleCSC", "Connect fail: ${e.message}")
        }
    }

    override fun disconnect() {
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

    private inner class CscBleManager(context: Context) : BleManager(context) {
        private var cscCharacteristic: BluetoothGattCharacteristic? = null

        override fun getGattCallback(): BleManagerGattCallback = object : BleManagerGattCallback() {
            override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
                val service = gatt.getService(CSC_SERVICE_UUID)
                cscCharacteristic = service?.getCharacteristic(CSC_MEASUREMENT_CHARACTERISTIC_UUID)
                return cscCharacteristic != null
            }

            override fun initialize() {
                setNotificationCallback(cscCharacteristic)
                    .with { _, data ->
                        handleIncomingData(data.value)
                    }
                enableNotifications(cscCharacteristic).enqueue()
            }

            override fun onServicesInvalidated() { cscCharacteristic = null }
        }
        override fun shouldClearCacheWhenDisconnected(): Boolean = true
    }

    companion object {
        val CSC_SERVICE_UUID: UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
        val CSC_MEASUREMENT_CHARACTERISTIC_UUID: UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")
    }
}
