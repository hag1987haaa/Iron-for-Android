package hag1987haaa.pebble.iron.domain.ble

import kotlinx.coroutines.flow.StateFlow

interface BleCscManager {
    val cadenceRpm: StateFlow<Int?>
    val wheelSpeedKmh: StateFlow<Double?>
    val cumulativeDistanceMeters: StateFlow<Double?>
    val isConnected: StateFlow<Boolean>
    val isDataActive: StateFlow<Boolean>

    fun setWheelCircumferenceMm(mm: Int)
    fun connect(address: String)
    fun disconnect()
    fun close()
}
