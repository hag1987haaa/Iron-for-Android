package hag1987haaa.pebble.iron.domain.ble

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int = 0
)
