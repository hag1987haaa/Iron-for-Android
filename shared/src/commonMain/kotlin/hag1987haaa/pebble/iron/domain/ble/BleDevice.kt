package hag1987haaa.pebble.iron.domain.ble

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int = 0,
    val serviceUuids: List<String> = emptyList()
) {
    val isHeartRate: Boolean
        get() = serviceUuids.any { it.contains("180d", ignoreCase = true) }

    val isCsc: Boolean
        get() = serviceUuids.any { it.contains("1816", ignoreCase = true) }

    val isCyclingPower: Boolean
        get() = serviceUuids.any { it.contains("1818", ignoreCase = true) }

    val isRunningSpeedCadence: Boolean
        get() = serviceUuids.any { it.contains("1814", ignoreCase = true) }
}
