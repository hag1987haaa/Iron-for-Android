package hag1987haaa.pebble.iron.domain.ble

import kotlinx.coroutines.flow.StateFlow

interface BlePowerManager {
    val powerWatts: StateFlow<Int?>
    val cadenceRpm: StateFlow<Int?>
    val isConnected: StateFlow<Boolean>
    val isDataActive: StateFlow<Boolean>

    fun connect(address: String)
    fun disconnect()
    fun close()
}
